package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.event.ExplorationEventHandler;
import fr.renblood.medievalstructures.init.ModDimensions;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.*;

public class ExplorationManager extends SavedData {
    private static final String DATA_NAME = "medieval_structures_explorations";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEARCH_DIRECTIONS = 16;
    private static final int FIRST_SEARCH_RADIUS = 96;
    private static final int SEARCH_RADIUS_STEP = 48;
    private static final int SEARCH_RADIUS_LIMIT = 900;
    private static final int SEARCH_CANDIDATES = ((SEARCH_RADIUS_LIMIT - 1 - FIRST_SEARCH_RADIUS)
            / SEARCH_RADIUS_STEP + 1) * SEARCH_DIRECTIONS;
    private static final int CLEANUP_BLOCKS_PER_TICK = 48;
    private final List<PendingExploration> pendingExplorations = new ArrayList<>();
    private final List<ActiveExploration> activeExplorations = new ArrayList<>();
    private final Deque<BlockPos> cleanupBlocks = new ArrayDeque<>();
    private final RetainedChestRegistry retainedChests = new RetainedChestRegistry();
    private int nextExplorationX = 0;
    
    public static boolean DEBUG_MODE = false;

    public static ExplorationManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ExplorationManager::load, ExplorationManager::new, DATA_NAME);
    }
    
    public PendingExploration pendingPayment(BlockPos origin,UUID payer){
        for(PendingExploration row:pendingExplorations)if(row.origin.equals(origin)&&payer.equals(row.launcherUuid))return row;
        return null;
    }
    public void recordPayment(BlockPos origin,UUID payer,String key){
        PendingExploration row=pendingPayment(origin,payer);if(row==null)throw new IllegalStateException("Exploration introuvable");row.paymentKey=key;setDirty();
    }
    public void persist(ServerLevel level){
        try{
            java.nio.file.Path file=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/"+DATA_NAME+".dat");
            setDirty();save(file.toFile());
            if(!net.minecraft.nbt.NbtIo.readCompressed(file.toFile()).getCompound("data").equals(save(new CompoundTag())))throw new java.io.IOException("Exploration save not verified");
        }catch(Exception failure){throw new IllegalStateException("Sauvegarde d’exploration à rapprocher",failure);}
    }
    public RetainedChestRegistry.Page retainedChestPage(int page) {
        return retainedChests.page(page);
    }

    public List<BlockPos> retainedChestPositions() {
        return retainedChests.snapshot().stream().map(row -> BlockPos.of(row.camp())).toList();
    }

    public RetainedChestRegistry.Resolution resolveRetainedChest(MinecraftServer server, BlockPos camp) {
        ServerLevel world = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        var result = retainedChests.resolve(camp.asLong(),
                position -> world != null && world.hasChunkAt(BlockPos.of(position)),
                position -> world.getBlockEntity(BlockPos.of(position)) instanceof net.minecraft.world.Container container
                        && !container.isEmpty());
        if (result == RetainedChestRegistry.Resolution.RESOLVED) setDirty();
        return result;
    }

    private static boolean belongsTo(ServerPlayer player, ActiveExploration active) {
        var data = player.getPersistentData();
        return active.participants.contains(player.getUUID()) && data.hasUUID(ExplorationEventHandler.EXPLORATION_ID)
                && data.getUUID(ExplorationEventHandler.EXPLORATION_ID).equals(active.id);
    }

    private static void showBorder(ServerPlayer player, ActiveExploration active) {
        WorldBorder border = new WorldBorder();
        border.setCenter(active.spawnPos.getX(), active.spawnPos.getZ());
        border.setSize(2.0 * CampLayout.HALF_SIZE);
        player.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    private static void enroll(ServerPlayer player, ActiveExploration active) {
        active.participants.add(player.getUUID());
        player.getPersistentData().putUUID(ExplorationEventHandler.EXPLORATION_ID, active.id);
        player.getPersistentData().putLong(ExplorationEventHandler.EXPLORATION_ORIGIN, active.origin.asLong());
        showBorder(player, active);
    }

    public boolean joinLatest(ServerPlayer player) {
        if (activeExplorations.isEmpty()) return false;
        var active = activeExplorations.get(activeExplorations.size() - 1);
        ServerLevel level = player.getServer().getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        if (level == null) return false;
        player.teleportTo(level, active.spawnPos.getX() + 0.5, active.spawnPos.getY(), active.spawnPos.getZ() + 0.5, 0, 0);
        enroll(player, active);
        setDirty();
        return true;
    }

    public void restorePlayer(ServerPlayer player) {
        if (!player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) return;
        var data = player.getPersistentData();
        if (!data.contains(ExplorationEventHandler.EXPLORATION_ORIGIN)) return;
        List<ActiveExploration> matches = new ArrayList<>();
        for (var active : activeExplorations) {
            if (data.hasUUID(ExplorationEventHandler.EXPLORATION_ID)) {
                if (!active.id.equals(data.getUUID(ExplorationEventHandler.EXPLORATION_ID))) continue;
            } else {
                if (data.getLong(ExplorationEventHandler.EXPLORATION_ORIGIN) != active.origin.asLong()
                        || !CampLayout.contains(player.getX(), player.getZ(), active.spawnPos.getX(), active.spawnPos.getZ())) continue;
            }
            matches.add(active);
        }
        if (matches.size() == 1) {
            enroll(player, matches.get(0));
            setDirty();
            return;
        }
        BlockPos origin = BlockPos.of(data.getLong(ExplorationEventHandler.EXPLORATION_ORIGIN));
        player.teleportTo(player.getServer().overworld(), origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        data.remove(ExplorationEventHandler.EXPLORATION_ID);
        data.remove(ExplorationEventHandler.EXPLORATION_ORIGIN);
        data.remove(ExplorationEventHandler.EXPLORATION_BED_RESPAWN);
        player.sendSystemMessage(Component.literal(matches.size() > 1
                ? "Ancienne exploration ambiguë : retour au hall. Demande au staff de vérifier ton expédition."
                : "Ton exploration est terminée. Tu es de retour au hall."));
    }

    public void tagEntity(Entity entity) {
        if (entity instanceof ServerPlayer || entity.getPersistentData().hasUUID(ExplorationEventHandler.EXPLORATION_ID)) return;
        ActiveExploration owner = null;
        for (var active : activeExplorations) {
            if (CampLayout.contains(entity.getX(), entity.getZ(), active.spawnPos.getX(), active.spawnPos.getZ())) {
                if (owner != null) return; // Preserve ambiguous entities from overlapping legacy camps.
                owner = active;
            }
        }
        if (owner != null) entity.getPersistentData().putUUID(ExplorationEventHandler.EXPLORATION_ID, owner.id);
    }

    public void claimEntity(ServerPlayer player, Entity entity) {
        if (entity instanceof ServerPlayer || entity.getPersistentData().hasUUID(ExplorationEventHandler.EXPLORATION_ID)) return;
        for (var active : activeExplorations) {
            if (belongsTo(player, active) && CampLayout.contains(entity.getX(), entity.getZ(),
                    active.spawnPos.getX(), active.spawnPos.getZ())) {
                entity.getPersistentData().putUUID(ExplorationEventHandler.EXPLORATION_ID, active.id);
                return;
            }
        }
    }

    private static boolean ownsEntity(Entity entity, ActiveExploration active) {
        var tag = entity.getPersistentData();
        return tag.hasUUID(ExplorationEventHandler.EXPLORATION_ID)
                && tag.getUUID(ExplorationEventHandler.EXPLORATION_ID).equals(active.id);
    }

    private void openSharedBorder(ServerLevel level) {
        var border = level.getWorldBorder();
        if (border.getSize() < 59999968.0D || border.getCenterX() != 0 || border.getCenterZ() != 0) {
            border.setCenter(0, 0);
            border.setSize(59999968.0D);
            for (var player : level.players()) {
                for (var active : activeExplorations) {
                    if (belongsTo(player, active)) showBorder(player, active);
                }
            }
        }
    }

    public BlockPos getLastExplorationSpawn() {
        if (!activeExplorations.isEmpty()) {
            return activeExplorations.get(activeExplorations.size() - 1).spawnPos;
        }
        return null;
    }
    
    public int getLastExplorationX() {
        if (!activeExplorations.isEmpty()) {
            return activeExplorations.get(activeExplorations.size() - 1).targetX;
        }
        if (nextExplorationX > 0) {
            return nextExplorationX - 5000;
        }
        return -1;
    }
    
    public boolean isCountdownActive(BlockPos origin) {
        if (activeExplorations.stream().anyMatch(active -> active.origin.equals(origin))) return true;
        for (PendingExploration explo : pendingExplorations) {
            if (explo.origin.equals(origin)) {
                return true;
            }
        }
        return false;
    }

    public int cancelCountdown(ServerLevel level, BlockPos origin) {
        Iterator<PendingExploration> it = pendingExplorations.iterator();
        while (it.hasNext()) {
            PendingExploration explo = it.next();
            if (explo.origin.equals(origin)) {
                removeCountdownDisplay(level, explo.displayUUID);
                it.remove();
                setDirty();
                return explo.paidAmount;
            }
        }
        return -1;
    }
    
    public void startCountdown(ServerLevel level, BlockPos origin, int durationMinutes, int requiredPlayers, int totalAnimals, BlockPos animalReturnPen, int paidAmount, Map<String, List<BlockPos>> playerChests, boolean spawnBeds, boolean spawnLodestone, UUID launcherUuid) {
        int x = nextExplorationX;
        nextExplorationX += 5000;
        
        if (DEBUG_MODE) {
            durationMinutes = 1;
        }
        
        // Créer l'entité TextDisplay
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display != null) {
            display.setPos(origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5);
            
            // Utiliser CustomName comme fallback fiable
            display.setCustomName(Component.literal("Préparation...").withStyle(net.minecraft.ChatFormatting.YELLOW));
            display.setCustomNameVisible(true);
            
            // Tenter d'utiliser le texte interne du Display (plus joli)
            setText(display, Component.literal("Préparation..."));
            setBillboardConstraints(display, Display.BillboardConstraints.CENTER);
            
            level.addFreshEntity(display);
            
            pendingExplorations.add(new PendingExploration(origin, durationMinutes, x, requiredPlayers, totalAnimals, animalReturnPen, paidAmount, playerChests, display.getUUID(), spawnBeds, spawnLodestone, launcherUuid));
        } else {
            pendingExplorations.add(new PendingExploration(origin, durationMinutes, x, requiredPlayers, totalAnimals, animalReturnPen, paidAmount, playerChests, null, spawnBeds, spawnLodestone, launcherUuid));
        }
        setDirty();
    }
    
    private void setText(Display.TextDisplay display, Component text) {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(Display.TextDisplay.class, "m_269008_", Component.class);
            m.invoke(display, text);
        } catch (Exception e) {
            try {
                Method m = ObfuscationReflectionHelper.findMethod(Display.TextDisplay.class, "setText", Component.class);
                m.invoke(display, text);
            } catch (Exception ex) {
                // Fallback déjà géré par setCustomName
            }
        }
    }
    
    private void setBillboardConstraints(Display display, Display.BillboardConstraints constraints) {
        try {
            Method m = ObfuscationReflectionHelper.findMethod(Display.class, "m_269371_", Display.BillboardConstraints.class);
            m.invoke(display, constraints);
        } catch (Exception e) {
            try {
                Method m = ObfuscationReflectionHelper.findMethod(Display.class, "setBillboardConstraints", Display.BillboardConstraints.class);
                m.invoke(display, constraints);
            } catch (Exception ex) {
                // Ignorer
            }
        }
    }

    public void tick(MinecraftServer server) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        if (explorationWorld != null && !activeExplorations.isEmpty()) openSharedBorder(explorationWorld);
        boolean searchedThisTick = false;
        boolean returnDue = activeExplorations.stream().anyMatch(active -> active.ticksRemaining <= 1);
        Iterator<PendingExploration> itPending = pendingExplorations.iterator();
        while (itPending.hasNext()) {
            PendingExploration explo = itPending.next();
            ServerLevel level = server.overworld();
            
            if (explo.state == ExplorationState.COUNTDOWN) {
                AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
                int playerCount = level.getEntitiesOfClass(ServerPlayer.class, zone).size();
                
                if (!DEBUG_MODE && playerCount < explo.requiredPlayers) {
                    if (server.getTickCount() % 40 == 0) {
                        level.getPlayers(p -> p.distanceToSqr(explo.origin.getX(), explo.origin.getY(), explo.origin.getZ()) < 1000).forEach(p -> 
                            p.sendSystemMessage(Component.literal("En attente de joueurs... (" + playerCount + "/" + explo.requiredPlayers + ")").withStyle(net.minecraft.ChatFormatting.YELLOW))
                        );
                        updateCountdownDisplay(level, explo.displayUUID, "En attente de joueurs (" + playerCount + "/" + explo.requiredPlayers + ")");
                    }
                    continue;
                }

                explo.ticksRemaining--;
                if (explo.ticksRemaining % 20 == 0) setDirty();

                // Mise à jour du display
                if (explo.ticksRemaining % 20 == 0) {
                    int seconds = explo.ticksRemaining / 20;
                    updateCountdownDisplay(level, explo.displayUUID, "Départ dans " + seconds + "s");
                }

                if (explo.ticksRemaining % 20 == 0) {
                    int seconds = explo.ticksRemaining / 20;
                    spawnParticles(level, explo.origin, seconds);
                    playCountdownMusic(level, explo.origin, seconds);
                    
                    if (seconds == 30 || seconds <= 5) {
                        level.getPlayers(p -> p.distanceToSqr(explo.origin.getX(), explo.origin.getY(), explo.origin.getZ()) < 1000).forEach(p -> 
                            p.sendSystemMessage(Component.literal("Exploration dans " + seconds + "s !"))
                        );
                    }
                }

                if (explo.ticksRemaining <= 0) {
                    updateCountdownDisplay(level, explo.displayUUID, "Recherche d’un camp…");
                    explo.state = ExplorationState.TELEPORTING;
                    explo.ticksRemaining = 5;
                    setDirty();
                }
            } else if (explo.state == ExplorationState.TELEPORTING) {
                if (explo.ticksRemaining > 0) {
                    explo.ticksRemaining--;
                    continue;
                }
                if (searchedThisTick || returnDue || explorationWorld == null) continue;
                if (!DEBUG_MODE) {
                    AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
                    int players = level.getPlayers(player -> zone.intersects(player.getBoundingBox())).size();
                    if (players < explo.requiredPlayers) {
                        if (server.getTickCount() % 20 == 0) {
                            updateCountdownDisplay(level, explo.displayUUID,
                                    "En attente de joueurs (" + players + "/" + explo.requiredPlayers + ")");
                        }
                        continue;
                    }
                }
                if (server.getTickCount() % 20 == 0) {
                    updateCountdownDisplay(level, explo.displayUUID, "Recherche d’un camp…");
                }
                searchedThisTick = true;
                BlockPos safe = findSafeCandidate(explorationWorld, explo.targetX, explo.searchCandidate++);
                setDirty();
                if (safe == null && explo.searchCandidate < SEARCH_CANDIDATES) continue;
                if (safe == null) {
                    safe = new BlockPos(explo.targetX, 150, 0);
                    if (overlapsActiveCamp(safe.getX() + 2, safe.getZ())) {
                        updateCountdownDisplay(level, explo.displayUUID, "Aucune zone libre. Annule le départ pour être remboursé.");
                        continue;
                    }
                }

                removeCountdownDisplay(level, explo.displayUUID);
                activeExplorations.add(teleportPlayers(server, explorationWorld, explo, safe));
                itPending.remove();
                setDirty();
            }
        }

        boolean returnedThisTick = false;
        Iterator<ActiveExploration> itActive = activeExplorations.iterator();
        while (itActive.hasNext()) {
            ActiveExploration active = itActive.next();
            if (active.ticksRemaining > 0) active.ticksRemaining--;

            if (active.ticksRemaining > 0 && active.ticksRemaining % 20 == 0) {
                setDirty();
                if (explorationWorld != null) {
                    AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
                    List<ServerPlayer> players = explorationWorld.getPlayers(player -> belongsTo(player, active));
                    for (ServerPlayer player : players) {
                        if (!CampLayout.contains(player.getX(), player.getZ(), active.spawnPos.getX(), active.spawnPos.getZ())) {
                            player.teleportTo(explorationWorld, active.spawnPos.getX() + 0.5, active.spawnPos.getY(),
                                    active.spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                            player.sendSystemMessage(Component.literal("Tu as atteint la limite de ton exploration."));
                        }
                    }
                    
                    int secondsLeft = active.ticksRemaining / 20;
                    String timeStr = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);
                    Component msg = Component.literal("Temps restant : " + timeStr).withStyle(net.minecraft.ChatFormatting.YELLOW);
                    
                    for (ServerPlayer player : players) {
                        player.displayClientMessage(msg, true);
                        if (active.ticksRemaining % 100 == 0) {
                            player.getPersistentData().putLong(ExplorationEventHandler.EXPLORATION_ORIGIN, active.origin.asLong());
                        }
                    }
                    
                    if (active.totalAnimals > 0 && active.ticksRemaining % 40 == 0) {
                        spawnAnimalTetherParticles(explorationWorld, active.spawnPos, active.totalAnimals);
                    }

                    if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10) {
                        for (ServerPlayer player : players) {
                            player.sendSystemMessage(Component.literal("Fin de l'exploration dans " + secondsLeft + " secondes !").withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                }
            }
            
            if (active.ticksRemaining <= 0 && explorationWorld != null && !returnedThisTick) {
                endExploration(server, explorationWorld, active);
                returnedThisTick = true;
                itActive.remove();
                setDirty();
            }
        }

        if (explorationWorld != null && !searchedThisTick && !returnedThisTick) {
            cleanCamp(explorationWorld);
        }
    }
    
    private void updateCountdownDisplay(ServerLevel level, UUID uuid, String text) {
        if (uuid == null) return;
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            entity.setCustomName(Component.literal(text).withStyle(net.minecraft.ChatFormatting.YELLOW));
            entity.setCustomNameVisible(true);
            
            if (entity instanceof Display.TextDisplay display) {
                setText(display, Component.literal(text).withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        }
    }
    
    private void removeCountdownDisplay(ServerLevel level, UUID uuid) {
        if (uuid == null || level == null) return;
        Entity entity = level.getEntity(uuid);
        if (entity != null) {
            entity.discard();
        }
    }
    
    private void endExploration(MinecraftServer server, ServerLevel explorationWorld, ActiveExploration active) {
        ServerLevel overworld = server.overworld();
        Set<BlockPos> keptChests = new HashSet<>();

        // Rapatrier les coffres
        for (Map.Entry<BlockPos, BlockPos> entry : active.chestMapping.entrySet()) {
            BlockPos destPos = entry.getKey();
            BlockPos sourcePos = entry.getValue();
            
            BlockEntity destBe = explorationWorld.getBlockEntity(destPos);
            BlockEntity sourceBe = overworld.getBlockEntity(sourcePos);
            
            if (destBe instanceof ChestBlockEntity destChest && sourceBe instanceof ChestBlockEntity sourceChest
                    && sourceChest.isEmpty() && destChest.getContainerSize() <= sourceChest.getContainerSize()) {
                NonNullList<ItemStack> items = NonNullList.withSize(destChest.getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < destChest.getContainerSize(); i++) {
                    items.set(i, destChest.getItem(i).copy());
                }
                
                sourceChest.clearContent();
                for (int i = 0; i < items.size(); i++) {
                    if (i < sourceChest.getContainerSize()) {
                        sourceChest.setItem(i, items.get(i));
                    }
                }
                destChest.clearContent();
                destChest.setChanged();
                sourceChest.setChanged();
                overworld.sendBlockUpdated(sourcePos, overworld.getBlockState(sourcePos), overworld.getBlockState(sourcePos), 3);
            } else if (destBe instanceof ChestBlockEntity) {
                keptChests.add(destPos);
                retainedChests.put(destPos.asLong(), sourcePos.asLong());
                LOGGER.warn("Coffre d’exploration conservé en {} : retour impossible vers {}", destPos, sourcePos);
            }
        }
        
        AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
        List<ServerPlayer> players = explorationWorld.getPlayers(player -> belongsTo(player, active));
        List<Mob> animalsToReturn = collectLeashedAnimals(explorationWorld, zone, active.totalAnimals, active);
        
        BlockPos returnPos = active.origin;
        
        for (ServerPlayer player : players) {
            player.teleportTo(overworld, returnPos.getX() + 0.5, returnPos.getY() + 1, returnPos.getZ() + 0.5, 0, 0);
            player.sendSystemMessage(Component.literal("Exploration terminée !").withStyle(net.minecraft.ChatFormatting.GOLD));
            if (!keptChests.isEmpty()) {
                player.sendSystemMessage(Component.literal(keptChests.size()
                        + " coffre(s) sont restés au camp. Demande à l’équipe de les récupérer.")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            if (!animalsToReturn.isEmpty()) {
                player.sendSystemMessage(Component.literal(animalsToReturn.size() + " animal(aux) rapatrie(s) autour de la table d'exploration.").withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            player.getPersistentData().remove(ExplorationEventHandler.EXPLORATION_ID);
            player.getPersistentData().remove(ExplorationEventHandler.EXPLORATION_ORIGIN);
            player.getPersistentData().remove(ExplorationEventHandler.EXPLORATION_BED_RESPAWN);
        }
        returnLeashedAnimals(overworld, active.animalReturnPen, returnPos, animalsToReturn);
        BlockEntity originBe = overworld.getBlockEntity(active.origin);
        if (originBe instanceof ExplorationHallBlockEntity hall) {
            hall.recordCompletedExploration(active.launcherUuid);
        }
        
        List<Entity> toRemove = new ArrayList<>();
        explorationWorld.getEntities().getAll().forEach(entity -> {
            if (!(entity instanceof ServerPlayer) && ownsEntity(entity, active)) {
                toRemove.add(entity);
            }
        });
        
        for (Entity entity : toRemove) {
            entity.discard();
        }
        
        active.builtBlocks.stream().distinct().filter(pos -> !keptChests.contains(pos))
                .filter(pos -> activeExplorations.stream().noneMatch(other -> other != active && other.builtBlocks.contains(pos)))
                .sorted(Comparator.comparingLong(ExplorationManager::chunkKey))
                .forEach(cleanupBlocks::addLast);
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private void cleanCamp(ServerLevel level) {
        if (cleanupBlocks.isEmpty()) return;
        long chunk = chunkKey(cleanupBlocks.peekFirst());
        int remaining = CLEANUP_BLOCKS_PER_TICK;
        while (remaining-- > 0 && !cleanupBlocks.isEmpty() && chunkKey(cleanupBlocks.peekFirst()) == chunk) {
            level.setBlock(cleanupBlocks.peekFirst(), Blocks.AIR.defaultBlockState(), 2);
            cleanupBlocks.removeFirst();
        }
        setDirty();
    }

    private void spawnParticles(ServerLevel level, BlockPos center, int secondsLeft) {
        int r = secondsLeft <= 5 ? 5 : 4;
        double y = center.getY() + 0.15;
        for (int x = -r; x <= r; x++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() - r + 0.5, 3, 0.12, 0.35, 0.12, 0.02);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() + r + 0.5, 3, 0.12, 0.35, 0.12, 0.02);
            level.sendParticles(ParticleTypes.ENCHANT, center.getX() + x + 0.5, y + 0.8, center.getZ() - r + 0.5, 2, 0.1, 0.5, 0.1, 0.02);
            level.sendParticles(ParticleTypes.ENCHANT, center.getX() + x + 0.5, y + 0.8, center.getZ() + r + 0.5, 2, 0.1, 0.5, 0.1, 0.02);
        }
        for (int z = -r; z <= r; z++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() - r + 0.5, y, center.getZ() + z + 0.5, 3, 0.12, 0.35, 0.12, 0.02);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + r + 0.5, y, center.getZ() + z + 0.5, 3, 0.12, 0.35, 0.12, 0.02);
            level.sendParticles(ParticleTypes.SMOKE, center.getX() - r + 0.5, y + 0.2, center.getZ() + z + 0.5, 2, 0.1, 0.25, 0.1, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, center.getX() + r + 0.5, y + 0.2, center.getZ() + z + 0.5, 2, 0.1, 0.25, 0.1, 0.01);
        }
        level.sendParticles(ParticleTypes.PORTAL, center.getX() + 0.5, center.getY() + 1.1, center.getZ() + 0.5, secondsLeft <= 5 ? 80 : 40, 2.2, 1.0, 2.2, 0.08);
    }

    private void spawnAnimalTetherParticles(ServerLevel level, BlockPos spawnPos, int totalAnimals) {
        int posts = Math.min(totalAnimals, 12);
        int centerX = spawnPos.getX() - 2;
        int tetherZ = spawnPos.getZ() + 6;
        int startX = centerX - Math.max(2, posts / 2);
        for (int i = 0; i < posts; i++) {
            double x = startX + i + 0.5D;
            double y = spawnPos.getY() + 1.15D;
            double z = tetherZ + 0.5D;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 4, 0.22D, 0.35D, 0.22D, 0.02D);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3D, z, 2, 0.08D, 0.18D, 0.08D, 0.01D);
        }
    }

    private void playCountdownMusic(ServerLevel level, BlockPos center, int secondsLeft) {
        float pitch = 0.75F + ((30 - Math.min(secondsLeft, 30)) % 8) * 0.08F;
        level.getPlayers(p -> p.distanceToSqr(center.getX(), center.getY(), center.getZ()) < 1000).forEach(player -> {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.45F, pitch);
            if (secondsLeft <= 5) {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.0F + secondsLeft * 0.05F);
            }
        });
    }

    private ActiveExploration teleportPlayers(MinecraftServer server, ServerLevel explorationWorld,
                                               PendingExploration explo, BlockPos safePos) {
        ServerLevel originLevel = server.overworld();
        AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
        List<ServerPlayer> players = originLevel.getEntitiesOfClass(ServerPlayer.class, zone);
        awardExplorerStartXp(server, originLevel, explo, players.size());
        
        int targetX = safePos.getX();
        int targetZ = safePos.getZ();
        int y = safePos.getY();
        openSharedBorder(explorationWorld);

        // Construction du camp
        List<BlockPos> builtBlocks = new ArrayList<>();
        Map<BlockPos, BlockPos> chestMapping = new HashMap<>();
        
        int totalAnimals = explo.totalAnimals;
        BlockPos firePos = new BlockPos(targetX, y, targetZ);
        
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx*dx + dz*dz <= 36) {
                    mPos.set(targetX + dx, y - 1, targetZ + dz);
                    var ground = explorationWorld.getBlockState(mPos);
                    if (!ground.isSolid() || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.WATER)) {
                        explorationWorld.setBlock(mPos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                        builtBlocks.add(mPos.immutable());
                    }
                    
                    for (int dy = 0; dy <= 5; dy++) {
                        mPos.set(targetX + dx, y + dy, targetZ + dz);
                        var block = explorationWorld.getBlockState(mPos);
                        if (block.isSolid() || block.is(Blocks.WATER)) {
                            explorationWorld.setBlock(mPos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
        explorationWorld.setBlock(firePos, (explo.spawnLodestone ? Blocks.LODESTONE : Blocks.CAMPFIRE).defaultBlockState(), 3);
        builtBlocks.add(firePos);
        buildAnimalTethers(explorationWorld, targetX, y, targetZ, totalAnimals, builtBlocks);
        if (explo.spawnBeds) {
            buildExplorerBeds(explorationWorld, targetX, y, targetZ, players, builtBlocks);
        }
        
        int totalChests = explo.playerChests.values().stream().mapToInt(List::size).sum();
        Iterator<CampLayout.Offset> offsets = CampLayout.chestOffsets().iterator();
        Set<BlockPos> copiedSources = new HashSet<>();
        for (Map.Entry<String, List<BlockPos>> entry : explo.playerChests.entrySet()) {
            for (BlockPos sourcePos : entry.getValue()) {
                if (!copiedSources.add(sourcePos)
                        || activeExplorations.stream().anyMatch(active -> active.chestMapping.containsValue(sourcePos))) continue;
                BlockEntity source = originLevel.getBlockEntity(sourcePos);
                if (!(source instanceof ChestBlockEntity sourceChest)) continue;
                BlockPos destPos = null;
                while (offsets.hasNext()) {
                    var offset = offsets.next();
                    BlockPos candidate = new BlockPos(targetX + offset.x(), y, targetZ + offset.z());
                    if (explorationWorld.isEmptyBlock(candidate) && explorationWorld.isEmptyBlock(candidate.above())) {
                        destPos = candidate;
                        break;
                    }
                }
                if (destPos == null) {
                    for (var player : players) player.sendSystemMessage(Component.literal(
                            "Un coffre reste au hall : aucun emplacement libre au camp."));
                    continue;
                }
                BlockPos ground = destPos.below();
                if (!explorationWorld.getBlockState(ground).isSolid()) {
                    explorationWorld.setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                    builtBlocks.add(ground);
                }
                Direction facing = Direction.getNearest(targetX - destPos.getX(), 0, targetZ - destPos.getZ());
                explorationWorld.setBlock(destPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
                        .setValue(ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE), 3);
                builtBlocks.add(destPos);
                if (!(explorationWorld.getBlockEntity(destPos) instanceof ChestBlockEntity destChest)) continue;
                destChest.setCustomName(sourceChest.hasCustomName() ? sourceChest.getCustomName()
                        : Component.literal("Coffre de " + entry.getKey()));
                for (int slot = 0; slot < sourceChest.getContainerSize(); slot++) {
                    destChest.setItem(slot, sourceChest.getItem(slot).copy());
                }
                sourceChest.clearContent();
                sourceChest.setChanged();
                destChest.setChanged();
                originLevel.sendBlockUpdated(sourcePos, originLevel.getBlockState(sourcePos), originLevel.getBlockState(sourcePos), 3);
                chestMapping.put(destPos, sourcePos);
            }
        }

        if (totalChests < 8) {
            for (int i = 0; i < 4; i++) {
                double angle = (i * Math.PI / 2) + (Math.PI / 4);
                int lx = targetX + (int)(Math.cos(angle) * 2);
                int lz = targetZ + (int)(Math.sin(angle) * 2);
                BlockPos logPos = new BlockPos(lx, y, lz);
                if (explorationWorld.getBlockState(logPos).isAir()) {
                    explorationWorld.setBlock(logPos, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 3);
                    builtBlocks.add(logPos);
                }
            }
        }

        if (chestMapping.size() < totalChests) {
            for (var player : players) player.sendSystemMessage(Component.literal(
                    chestMapping.size() + "/" + totalChests + " coffres déplacés. Les autres restent au hall."));
        }
        BlockPos finalSpawn = new BlockPos(targetX + 2, y, targetZ);

        int durationTicks = explo.durationMinutes * 60 * 20;
        ActiveExploration active = new ActiveExploration(finalSpawn, durationTicks, explo.origin,
                chestMapping, builtBlocks, explo.totalAnimals, explo.animalReturnPen, explo.launcherUuid);
        for (ServerPlayer player : players) {
            player.teleportTo(explorationWorld, finalSpawn.getX() + 0.5, y, finalSpawn.getZ() + 0.5, 0, 0);
            explorationWorld.playSound(null, finalSpawn, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.35F, 1.35F);
            player.sendSystemMessage(Component.literal("Bienvenue au camp d'exploration !"));
            enroll(player, active);
            if (explo.spawnBeds) {
                player.getPersistentData().putBoolean(ExplorationEventHandler.EXPLORATION_BED_RESPAWN, true);
                player.getPersistentData().remove(ExplorationEventHandler.DIED_IN_EXPLORATION);
            } else {
                player.getPersistentData().remove(ExplorationEventHandler.EXPLORATION_BED_RESPAWN);
            }
        }
        
        return active;

    }

    private void awardExplorerStartXp(MinecraftServer server, ServerLevel originLevel, PendingExploration explo, int playerCount) {
        if (explo.launcherUuid == null || playerCount <= 0) return;
        ServerPlayer launcher = server.getPlayerList().getPlayer(explo.launcherUuid);
        if (launcher == null) return;

        int hallLevel = 1;
        BlockEntity originBe = originLevel.getBlockEntity(explo.origin);
        if (originBe instanceof ExplorationHallBlockEntity hall) {
            hallLevel = hall.getHallLevel();
        }

        int durationSlices = Math.max(1, explo.durationMinutes / 20);
        int flatBonus = switch (hallLevel) {
            case 2 -> 8;
            case 3 -> 10;
            default -> 5;
        };
        int gainedXp = playerCount * durationSlices + flatBonus;
        MedievalCoinsIntegration.addExplorerXP(launcher, gainedXp);
    }
    
    private BlockPos findSafeCandidate(ServerLevel level, int startX, int candidate) {
        if (candidate < 0 || candidate >= SEARCH_CANDIDATES) return null;
        int radius = FIRST_SEARCH_RADIUS + candidate / SEARCH_DIRECTIONS * SEARCH_RADIUS_STEP;
        double angle = candidate % SEARCH_DIRECTIONS * (2 * Math.PI / SEARCH_DIRECTIONS);
        int x = startX + (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);
        if (overlapsActiveCamp(x + 2, z)) return null;
        level.getChunk(x >> 4, z >> 4);

        var biome = level.getBiome(new BlockPos(x, 64, z));
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN)
                || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) return null;

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y < 63) return null;
        BlockPos position = new BlockPos(x, y, z);
        return isAreaClear(level, position) ? position : null;
    }

    private boolean overlapsActiveCamp(int x, int z) {
        return activeExplorations.stream().anyMatch(active ->
                CampLayout.overlaps(x, z, active.spawnPos.getX(), active.spawnPos.getZ()))
                || cleanupBlocks.stream().anyMatch(pos -> CampLayout.contains(pos.getX(), pos.getZ(), x, z))
                || retainedChests.anyCamp(packed -> {
                    BlockPos pos = BlockPos.of(packed);
                    return CampLayout.contains(pos.getX(), pos.getZ(), x, z);
                });
    }

    private boolean isAreaClear(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(m).isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void buildExplorerBeds(ServerLevel level, int targetX, int y, int targetZ, List<ServerPlayer> players, List<BlockPos> builtBlocks) {
        if (players.isEmpty()) return;
        net.minecraft.world.level.block.Block[] beds = {
                Blocks.RED_BED, Blocks.BLUE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED,
                Blocks.PURPLE_BED, Blocks.ORANGE_BED, Blocks.CYAN_BED, Blocks.LIME_BED,
                Blocks.PINK_BED, Blocks.LIGHT_BLUE_BED
        };
        Direction[] facings = {
                Direction.SOUTH, Direction.SOUTH, Direction.NORTH, Direction.NORTH,
                Direction.EAST, Direction.EAST, Direction.WEST, Direction.WEST,
                Direction.SOUTH, Direction.NORTH
        };
        int[][] offsets = {
                {-5, -4}, {-2, -4}, {2, 5}, {5, 5},
                {-5, 2}, {-5, 5}, {5, -5}, {5, -2},
                {-1, 5}, {1, -4}
        };

        for (int i = 0; i < players.size(); i++) {
            int slot = i % offsets.length;
            Direction facing = facings[slot];
            BlockPos foot = new BlockPos(targetX + offsets[slot][0], y, targetZ + offsets[slot][1]);
            BlockPos head = foot.relative(facing);
            clearBedSpace(level, foot, head, builtBlocks);
            level.setBlock(foot, beds[i % beds.length].defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT), 3);
            level.setBlock(head, beds[i % beds.length].defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD), 3);
            builtBlocks.add(foot);
            builtBlocks.add(head);
            players.get(i).setRespawnPosition(level.dimension(), foot, 0.0F, true, false);
        }
    }

    private void clearBedSpace(ServerLevel level, BlockPos foot, BlockPos head, List<BlockPos> builtBlocks) {
        for (BlockPos pos : new BlockPos[]{foot, head}) {
            BlockPos ground = pos.below();
            if (!level.getBlockState(ground).isSolid()) {
                level.setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                builtBlocks.add(ground);
            }
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos clear = pos.above(dy);
                if (!level.getBlockState(clear).isAir()) {
                    level.setBlock(clear, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private void buildAnimalTethers(ServerLevel level, int targetX, int y, int targetZ, int totalAnimals, List<BlockPos> builtBlocks) {
        if (totalAnimals <= 0) return;
        int posts = Math.min(totalAnimals, 12);
        int startX = targetX - Math.max(2, posts / 2);
        int tetherZ = targetZ + 6;
        for (int i = 0; i < posts; i++) {
            BlockPos base = new BlockPos(startX + i, y - 1, tetherZ);
            BlockPos fence = new BlockPos(startX + i, y, tetherZ);
            level.setBlock(base, Blocks.COARSE_DIRT.defaultBlockState(), 2);
            level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 3);
            builtBlocks.add(base);
            builtBlocks.add(fence);
        }
    }

    private List<Mob> collectLeashedAnimals(ServerLevel explorationWorld, AABB zone, int maxAnimals, ActiveExploration active) {
        List<Mob> animalsToReturn = new ArrayList<>();
        if (maxAnimals <= 0) return animalsToReturn;

        AABB tetherZone = getAnimalTetherZone(zone.getCenter(), maxAnimals);
        Set<UUID> collected = new HashSet<>();

        explorationWorld.getEntities().getAll().forEach(entity -> {
            if (animalsToReturn.size() >= maxAnimals) return;
            if (!(entity instanceof Mob mob) || collected.contains(mob.getUUID()) || !ownsEntity(mob, active)) return;

            boolean leashedInExploration = zone.contains(mob.position()) && mob.getLeashHolder() != null;
            boolean animalOnTetherPlatform = mob instanceof Animal && tetherZone.contains(mob.position());
            if (leashedInExploration || animalOnTetherPlatform) {
                animalsToReturn.add(mob);
                collected.add(mob.getUUID());
            }
        });
        return animalsToReturn;
    }

    private AABB getAnimalTetherZone(Vec3 zoneCenter, int totalAnimals) {
        int posts = Math.min(totalAnimals, 12);
        double centerX = zoneCenter.x - 2.0D;
        double tetherZ = zoneCenter.z + 6.0D;
        double startX = centerX - Math.max(2, posts / 2);
        return new AABB(startX - 2.0D, zoneCenter.y - 2.0D, tetherZ - 2.0D, startX + posts + 2.0D, zoneCenter.y + 5.0D, tetherZ + 3.0D);
    }

    public static Optional<BlockPos> findAnimalReturnPen(ServerLevel level, BlockPos hallPos) {
        for (int y = hallPos.getY() - 3; y <= hallPos.getY() + 3; y++) {
            for (int x = hallPos.getX() - 20; x <= hallPos.getX() + 20; x++) {
                for (int z = hallPos.getZ() - 20; z <= hallPos.getZ() + 20; z++) {
                    BlockPos penOrigin = new BlockPos(x, y, z);
                    if (isValidAnimalReturnPen(level, penOrigin)) {
                        return Optional.of(penOrigin);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isValidAnimalReturnPen(ServerLevel level, BlockPos penOrigin) {
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                BlockPos pos = penOrigin.offset(dx, 0, dz);
                BlockPos floor = pos.below();
                boolean border = dx == 0 || dx == 3 || dz == 0 || dz == 3;

                if (!level.getBlockState(floor).isSolid()) {
                    return false;
                }
                if (border) {
                    if (!(level.getBlockState(pos).getBlock() instanceof FenceBlock)) {
                        return false;
                    }
                } else if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void returnLeashedAnimals(ServerLevel overworld, BlockPos penOrigin, BlockPos fallbackOrigin, List<Mob> animalsToReturn) {
        List<BlockPos> returnSpots = penOrigin != null && isValidAnimalReturnPen(overworld, penOrigin)
                ? getAnimalReturnPenSpots(penOrigin)
                : getFallbackAnimalReturnSpots(overworld, fallbackOrigin);
        int index = 0;
        for (Mob mob : animalsToReturn) {
            BlockPos target = returnSpots.get(index++ % returnSpots.size());
            mob.dropLeash(true, false);
            CompoundTag entityData = new CompoundTag();
            mob.saveWithoutId(entityData);

            Entity recreated = mob.getType().create(overworld);
            if (recreated != null) {
                recreated.load(entityData);
                recreated.setUUID(UUID.randomUUID());
                recreated.getPersistentData().remove(ExplorationEventHandler.EXPLORATION_ID);
                recreated.moveTo(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, mob.getYRot(), mob.getXRot());
                if (recreated instanceof Mob recreatedMob) {
                    recreatedMob.setPersistenceRequired();
                }
                overworld.addFreshEntity(recreated);
                overworld.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5D, target.getY() + 1.4D, target.getZ() + 0.5D, 8, 0.35D, 0.45D, 0.35D, 0.03D);
            }
            mob.discard();
        }
    }

    private List<BlockPos> getAnimalReturnPenSpots(BlockPos penOrigin) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = 1; dx <= 2; dx++) {
            for (int dz = 1; dz <= 2; dz++) {
                spots.add(penOrigin.offset(dx, -1, dz));
            }
        }
        return spots;
    }

    private List<BlockPos> getFallbackAnimalReturnSpots(ServerLevel level, BlockPos origin) {
        List<BlockPos> spots = new ArrayList<>();
        int[][] offsets = {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };

        for (int[] offset : offsets) {
                BlockPos ground = new BlockPos(origin.getX() + offset[0], origin.getY(), origin.getZ() + offset[1]);
                BlockPos feet = ground.above();
                BlockPos head = feet.above();
                BlockPos top = head.above();

                if (!level.getBlockState(ground).isSolid()) {
                    level.setBlock(ground, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
                if (!level.getBlockState(feet).isAir()) {
                    level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                }
                if (!level.getBlockState(head).isAir()) {
                    level.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
                }
                if (!level.getBlockState(top).isAir()) {
                    level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
                }
                spots.add(ground);
        }
        return spots;
    }

    // --- Sauvegarde ---

    public enum ExplorationState {
        COUNTDOWN,
        TELEPORTING
    }

    public static class PendingExploration {
        public String paymentKey="";
        private int searchCandidate;
        public BlockPos origin;
        public int ticksRemaining;
        public int durationMinutes;
        public ExplorationState state = ExplorationState.COUNTDOWN;
        public int targetX;
        public int requiredPlayers;
        public int totalAnimals;
        public BlockPos animalReturnPen;
        public int paidAmount;
        public Map<String, List<BlockPos>> playerChests;
        public UUID displayUUID;
        public boolean spawnBeds;
        public boolean spawnLodestone;
        public UUID launcherUuid;

        public PendingExploration(BlockPos origin, int durationMinutes, int targetX, int requiredPlayers, int totalAnimals, BlockPos animalReturnPen, int paidAmount, Map<String, List<BlockPos>> playerChests, UUID displayUUID, boolean spawnBeds, boolean spawnLodestone, UUID launcherUuid) {
            this.origin = origin;
            this.ticksRemaining = 30 * 20;
            this.durationMinutes = durationMinutes;
            this.targetX = targetX;
            this.requiredPlayers = requiredPlayers;
            this.totalAnimals = totalAnimals;
            this.animalReturnPen = animalReturnPen;
            this.paidAmount = paidAmount;
            this.playerChests = playerChests;
            this.displayUUID = displayUUID;
            this.spawnBeds = spawnBeds;
            this.spawnLodestone = spawnLodestone;
            this.launcherUuid = launcherUuid;
        }
        
        public PendingExploration(CompoundTag tag) {
            this.origin = BlockPos.of(tag.getLong("Origin"));
            this.ticksRemaining = tag.getInt("Ticks");
            this.durationMinutes = tag.getInt("Duration");
            this.targetX = tag.getInt("TargetX");
            this.requiredPlayers = tag.getInt("RequiredPlayers");
            this.totalAnimals = tag.getInt("TotalAnimals");
            if (tag.contains("AnimalReturnPen")) {
                this.animalReturnPen = BlockPos.of(tag.getLong("AnimalReturnPen"));
            }
            this.paidAmount = tag.getInt("PaidAmount");
            this.paymentKey=tag.getString("PaymentKey");
            this.searchCandidate = Math.max(0, Math.min(SEARCH_CANDIDATES, tag.getInt("SearchCandidate")));
            if (tag.contains("State")) {
                this.state = ExplorationState.valueOf(tag.getString("State"));
            }
            if (tag.hasUUID("DisplayUUID")) {
                this.displayUUID = tag.getUUID("DisplayUUID");
            }
            this.spawnBeds = tag.getBoolean("SpawnBeds");
            this.spawnLodestone = tag.getBoolean("SpawnLodestone");
            if (tag.hasUUID("LauncherUUID")) {
                this.launcherUuid = tag.getUUID("LauncherUUID");
            }
            
            this.playerChests = new HashMap<>();
            if (tag.contains("PlayerChests", Tag.TAG_LIST)) {
                ListTag list = tag.getList("PlayerChests", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    String name = c.getString("Name");
                    List<BlockPos> chests = new ArrayList<>();
                    long[] positions = c.getLongArray("Chests");
                    for (long pos : positions) {
                        chests.add(BlockPos.of(pos));
                    }
                    this.playerChests.put(name, chests);
                }
            }
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Origin", origin.asLong());
            tag.putInt("Ticks", ticksRemaining);
            tag.putInt("Duration", durationMinutes);
            tag.putInt("TargetX", targetX);
            tag.putInt("RequiredPlayers", requiredPlayers);
            tag.putInt("TotalAnimals", totalAnimals);
            if (animalReturnPen != null) tag.putLong("AnimalReturnPen", animalReturnPen.asLong());
            tag.putInt("PaidAmount", paidAmount);
            tag.putString("PaymentKey",paymentKey);
            tag.putInt("SearchCandidate", searchCandidate);
            tag.putString("State", state.name());
            if (displayUUID != null) tag.putUUID("DisplayUUID", displayUUID);
            tag.putBoolean("SpawnBeds", spawnBeds);
            tag.putBoolean("SpawnLodestone", spawnLodestone);
            if (launcherUuid != null) tag.putUUID("LauncherUUID", launcherUuid);
            
            ListTag list = new ListTag();
            for (Map.Entry<String, List<BlockPos>> entry : playerChests.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("Name", entry.getKey());
                List<Long> positions = new ArrayList<>();
                for (BlockPos pos : entry.getValue()) positions.add(pos.asLong());
                c.putLongArray("Chests", positions);
                list.add(c);
            }
            tag.put("PlayerChests", list);
            
            return tag;
        }
    }
    
    public static class ActiveExploration {
        public UUID id = UUID.randomUUID();
        public final Set<UUID> participants = new HashSet<>();
        public BlockPos spawnPos;
        public int ticksRemaining;
        public BlockPos origin;
        public int targetX;
        public Map<BlockPos, BlockPos> chestMapping;
        public List<BlockPos> builtBlocks;
        public int totalAnimals;
        public BlockPos animalReturnPen;
        public UUID launcherUuid;
        
        public ActiveExploration(BlockPos spawnPos, int ticksRemaining, BlockPos origin, Map<BlockPos, BlockPos> chestMapping, List<BlockPos> builtBlocks, int totalAnimals, BlockPos animalReturnPen, UUID launcherUuid) {
            this.spawnPos = spawnPos;
            this.targetX = spawnPos.getX();
            this.ticksRemaining = ticksRemaining;
            this.origin = origin;
            this.chestMapping = chestMapping;
            this.builtBlocks = builtBlocks;
            this.totalAnimals = totalAnimals;
            this.animalReturnPen = animalReturnPen;
            this.launcherUuid = launcherUuid;
        }
        
        public ActiveExploration(CompoundTag tag) {
            if (tag.hasUUID("ExpeditionId")) this.id = tag.getUUID("ExpeditionId");
            else this.id = UUID.nameUUIDFromBytes(("legacy-exploration:" + tag.getLong("Origin") + ":" + tag.getLong("SpawnPos"))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (Tag raw : tag.getList("Participants", Tag.TAG_STRING)) {
                try { participants.add(UUID.fromString(raw.getAsString())); }
                catch (IllegalArgumentException ignored) { }
            }
            this.spawnPos = BlockPos.of(tag.getLong("SpawnPos"));
            this.targetX = tag.getInt("TargetX");
            this.ticksRemaining = tag.getInt("Ticks");
            this.origin = BlockPos.of(tag.getLong("Origin"));
            this.totalAnimals = tag.getInt("TotalAnimals");
            if (tag.contains("AnimalReturnPen")) {
                this.animalReturnPen = BlockPos.of(tag.getLong("AnimalReturnPen"));
            }
            if (tag.hasUUID("LauncherUUID")) {
                this.launcherUuid = tag.getUUID("LauncherUUID");
            }
            
            this.chestMapping = new HashMap<>();
            if (tag.contains("ChestMapping", Tag.TAG_LIST)) {
                ListTag list = tag.getList("ChestMapping", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    chestMapping.put(BlockPos.of(c.getLong("Dest")), BlockPos.of(c.getLong("Source")));
                }
            }
            
            this.builtBlocks = new ArrayList<>();
            if (tag.contains("BuiltBlocks")) {
                long[] positions = tag.getLongArray("BuiltBlocks");
                for (long pos : positions) {
                    builtBlocks.add(BlockPos.of(pos));
                }
            }
        }
        
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("ExpeditionId", id);
            ListTag members = new ListTag();
            for (UUID participant : participants) members.add(net.minecraft.nbt.StringTag.valueOf(participant.toString()));
            tag.put("Participants", members);
            tag.putLong("SpawnPos", spawnPos.asLong());
            tag.putInt("TargetX", targetX);
            tag.putInt("Ticks", ticksRemaining);
            tag.putLong("Origin", origin.asLong());
            tag.putInt("TotalAnimals", totalAnimals);
            if (animalReturnPen != null) tag.putLong("AnimalReturnPen", animalReturnPen.asLong());
            if (launcherUuid != null) tag.putUUID("LauncherUUID", launcherUuid);
            
            ListTag list = new ListTag();
            for (Map.Entry<BlockPos, BlockPos> entry : chestMapping.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putLong("Dest", entry.getKey().asLong());
                c.putLong("Source", entry.getValue().asLong());
                list.add(c);
            }
            tag.put("ChestMapping", list);
            
            List<Long> builtPos = new ArrayList<>();
            for (BlockPos pos : builtBlocks) builtPos.add(pos.asLong());
            tag.putLongArray("BuiltBlocks", builtPos);
            
            return tag;
        }
    }

    public ExplorationManager() {}
    
    public ExplorationManager(int nextX) {
        this.nextExplorationX = nextX;
    }

    public static ExplorationManager load(CompoundTag tag) {
        ExplorationManager manager = new ExplorationManager(tag.getInt("NextX"));
        
        if (tag.contains("Pending", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                manager.pendingExplorations.add(new PendingExploration(list.getCompound(i)));
            }
        }
        
        if (tag.contains("Active", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Active", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                manager.activeExplorations.add(new ActiveExploration(list.getCompound(i)));
            }
        }
        
        for (Tag raw : tag.getList("RetainedChests", Tag.TAG_COMPOUND)) {
            CompoundTag chest = (CompoundTag) raw;
            manager.retainedChests.put(chest.getLong("Camp"), chest.getLong("Source"));
        }
        for (long position : tag.getLongArray("CleanupBlocks")) {
            manager.cleanupBlocks.addLast(BlockPos.of(position));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextX", nextExplorationX);
        
        ListTag pendingList = new ListTag();
        for (PendingExploration explo : pendingExplorations) {
            pendingList.add(explo.save());
        }
        tag.put("Pending", pendingList);
        
        ListTag activeList = new ListTag();
        for (ActiveExploration active : activeExplorations) {
            activeList.add(active.save());
        }
        tag.put("Active", activeList);
        tag.putLongArray("CleanupBlocks", cleanupBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag retained = new ListTag();
        retainedChests.snapshot().forEach(incident -> {
            CompoundTag chest = new CompoundTag();
            chest.putLong("Camp", incident.camp());
            chest.putLong("Source", incident.source());
            retained.add(chest);
        });
        tag.put("RetainedChests", retained);

        return tag;
    }
}
