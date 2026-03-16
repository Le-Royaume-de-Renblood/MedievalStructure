package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.init.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.*;

public class ExplorationManager extends SavedData {
    private static final String DATA_NAME = "medieval_structures_explorations";
    private final List<PendingExploration> pendingExplorations = new ArrayList<>();
    private final List<ActiveExploration> activeExplorations = new ArrayList<>();
    private int nextExplorationX = 0;
    
    public static boolean DEBUG_MODE = false;

    public static ExplorationManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ExplorationManager::load, ExplorationManager::new, DATA_NAME);
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
        for (PendingExploration explo : pendingExplorations) {
            if (explo.origin.equals(origin) && explo.state == ExplorationState.COUNTDOWN) {
                return true;
            }
        }
        return false;
    }

    public int cancelCountdown(BlockPos origin) {
        Iterator<PendingExploration> it = pendingExplorations.iterator();
        while (it.hasNext()) {
            PendingExploration explo = it.next();
            if (explo.origin.equals(origin) && explo.state == ExplorationState.COUNTDOWN) {
                removeCountdownDisplay(serverLevel(origin), explo.displayUUID);
                it.remove();
                setDirty();
                return explo.paidAmount;
            }
        }
        return -1;
    }
    
    // Helper pour récupérer le niveau (supposé Overworld pour l'instant)
    private ServerLevel serverLevel(BlockPos pos) {
        // TODO: Passer le niveau en paramètre ou stocker la dimension
        return null; // A corriger dans l'appelant
    }

    public void startCountdown(ServerLevel level, BlockPos origin, int durationMinutes, int requiredPlayers, int paidAmount, Map<String, List<BlockPos>> playerChests) {
        int x = nextExplorationX;
        nextExplorationX += 5000;
        
        if (DEBUG_MODE) {
            durationMinutes = 0;
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
            
            pendingExplorations.add(new PendingExploration(origin, durationMinutes, x, requiredPlayers, paidAmount, playerChests, display.getUUID()));
        } else {
            pendingExplorations.add(new PendingExploration(origin, durationMinutes, x, requiredPlayers, paidAmount, playerChests, null));
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
                
                // Mise à jour du display
                if (explo.ticksRemaining % 20 == 0) {
                    int seconds = explo.ticksRemaining / 20;
                    updateCountdownDisplay(level, explo.displayUUID, "Départ dans " + seconds + "s");
                }

                if (explo.ticksRemaining % 20 == 0) {
                    spawnParticles(level, explo.origin);
                    
                    int seconds = explo.ticksRemaining / 20;
                    if (seconds == 30 || seconds <= 5) {
                        level.getPlayers(p -> p.distanceToSqr(explo.origin.getX(), explo.origin.getY(), explo.origin.getZ()) < 1000).forEach(p -> 
                            p.sendSystemMessage(Component.literal("Exploration dans " + seconds + "s !"))
                        );
                    }
                }

                if (explo.ticksRemaining <= 0) {
                    removeCountdownDisplay(level, explo.displayUUID);
                    explo.state = ExplorationState.TELEPORTING;
                    explo.ticksRemaining = 5;
                    setDirty();
                }
            } else if (explo.state == ExplorationState.TELEPORTING) {
                if (explo.ticksRemaining > 0) {
                    explo.ticksRemaining--;
                    
                    if (explo.ticksRemaining == 2) {
                        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
                        if (explorationWorld != null) {
                            explorationWorld.getChunk(explo.targetX >> 4, 0);
                        }
                    }
                } else {
                    ActiveExploration active = teleportPlayers(server, explo);
                    
                    if (active != null) {
                        activeExplorations.add(active);
                    }
                    
                    itPending.remove();
                    setDirty();
                }
            }
        }
        
        Iterator<ActiveExploration> itActive = activeExplorations.iterator();
        while (itActive.hasNext()) {
            ActiveExploration active = itActive.next();
            active.ticksRemaining--;
            
            if (active.ticksRemaining % 20 == 0) {
                ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
                if (explorationWorld != null) {
                    AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
                    List<ServerPlayer> players = explorationWorld.getEntitiesOfClass(ServerPlayer.class, zone);
                    
                    int secondsLeft = active.ticksRemaining / 20;
                    String timeStr = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);
                    Component msg = Component.literal("Temps restant : " + timeStr).withStyle(net.minecraft.ChatFormatting.YELLOW);
                    
                    for (ServerPlayer player : players) {
                        player.displayClientMessage(msg, true);
                        if (active.ticksRemaining % 100 == 0) {
                            player.getPersistentData().putLong("ExplorationOrigin", active.origin.asLong());
                        }
                    }
                    
                    if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10) {
                        for (ServerPlayer player : players) {
                            player.sendSystemMessage(Component.literal("Fin de l'exploration dans " + secondsLeft + " secondes !").withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                }
            }
            
            if (active.ticksRemaining <= 0) {
                endExploration(server, active);
                itActive.remove();
                setDirty();
            }
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
    
    private void endExploration(MinecraftServer server, ActiveExploration active) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        ServerLevel overworld = server.overworld();
        
        if (explorationWorld != null) {
            // Rapatrier les coffres
            for (Map.Entry<BlockPos, BlockPos> entry : active.chestMapping.entrySet()) {
                BlockPos destPos = entry.getKey();
                BlockPos sourcePos = entry.getValue();
                
                BlockEntity destBe = explorationWorld.getBlockEntity(destPos);
                BlockEntity sourceBe = overworld.getBlockEntity(sourcePos);
                
                if (destBe instanceof ChestBlockEntity destChest && sourceBe instanceof ChestBlockEntity sourceChest) {
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
                    sourceChest.setChanged();
                    overworld.sendBlockUpdated(sourcePos, overworld.getBlockState(sourcePos), overworld.getBlockState(sourcePos), 3);
                }
            }
            
            AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
            List<ServerPlayer> players = explorationWorld.getEntitiesOfClass(ServerPlayer.class, zone);
            
            BlockPos returnPos = active.origin;
            
            for (ServerPlayer player : players) {
                player.teleportTo(overworld, returnPos.getX() + 0.5, returnPos.getY() + 1, returnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("Exploration terminée !").withStyle(net.minecraft.ChatFormatting.GOLD));
                player.getPersistentData().remove("ExplorationOrigin");
            }
            
            List<Entity> toRemove = new ArrayList<>();
            explorationWorld.getEntities().getAll().forEach(entity -> {
                if (entity != null && !(entity instanceof ServerPlayer) && zone.contains(entity.position())) {
                    toRemove.add(entity);
                }
            });
            
            for (Entity entity : toRemove) {
                entity.discard();
            }
            
            // Nettoyer le camp
            for (BlockPos pos : active.builtBlocks) {
                explorationWorld.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private void spawnParticles(ServerLevel level, BlockPos center) {
        int r = 3;
        double y = center.getY() + 0.1;
        for (int x = -r; x <= r; x++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() - r + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() + r + 0.5, 1, 0, 0, 0, 0);
        }
        for (int z = -r; z <= r; z++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() - r + 0.5, y, center.getZ() + z + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + r + 0.5, y, center.getZ() + z + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private ActiveExploration teleportPlayers(MinecraftServer server, PendingExploration explo) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        
        if (explorationWorld != null) {
            ServerLevel originLevel = server.overworld();
            AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
            List<ServerPlayer> players = originLevel.getEntitiesOfClass(ServerPlayer.class, zone);
            
            int targetX = explo.targetX;
            int targetZ = 0;
            
            explorationWorld.getChunk(targetX >> 4, targetZ >> 4);
            
            BlockPos safePos = findSafeSpot(explorationWorld, targetX, targetZ);
            targetX = safePos.getX();
            targetZ = safePos.getZ();
            int y = safePos.getY();

            // Construction du camp
            List<BlockPos> builtBlocks = new ArrayList<>();
            Map<BlockPos, BlockPos> chestMapping = new HashMap<>();
            
            BlockPos firePos = new BlockPos(targetX, y, targetZ);
            explorationWorld.setBlock(firePos, Blocks.CAMPFIRE.defaultBlockState(), 3);
            builtBlocks.add(firePos);
            
            BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx*dx + dz*dz <= 16) {
                        mPos.set(targetX + dx, y - 1, targetZ + dz);
                        if (!explorationWorld.getBlockState(mPos).isSolid() || explorationWorld.getBlockState(mPos).is(Blocks.GRASS_BLOCK) || explorationWorld.getBlockState(mPos).is(Blocks.WATER)) {
                            explorationWorld.setBlock(mPos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                            builtBlocks.add(mPos.immutable());
                        }
                        
                        for (int dy = 0; dy <= 3; dy++) {
                            mPos.set(targetX + dx, y + dy, targetZ + dz);
                            if (explorationWorld.getBlockState(mPos).isSolid() || explorationWorld.getBlockState(mPos).is(Blocks.WATER)) {
                                explorationWorld.setBlock(mPos, Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    }
                }
            }
            
            List<BlockPos> chestPositions = new ArrayList<>();
            int chestRadius = 3;
            int totalChests = 0;
            for (List<BlockPos> l : explo.playerChests.values()) totalChests += l.size();
            
            if (totalChests > 0) {
                double angleStep = (2 * Math.PI) / totalChests;
                int currentChest = 0;
                
                for (Map.Entry<String, List<BlockPos>> entry : explo.playerChests.entrySet()) {
                    String playerName = entry.getKey();
                    for (BlockPos sourcePos : entry.getValue()) {
                        double angle = currentChest * angleStep;
                        int cx = targetX + (int)(Math.cos(angle) * chestRadius);
                        int cz = targetZ + (int)(Math.sin(angle) * chestRadius);
                        BlockPos destPos = new BlockPos(cx, y, cz);
                        
                        Direction facing = Direction.getNearest(targetX - cx, 0, targetZ - cz);
                        
                        explorationWorld.setBlock(destPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
                        builtBlocks.add(destPos);
                        
                        BlockEntity destBe = explorationWorld.getBlockEntity(destPos);
                        BlockEntity sourceBe = originLevel.getBlockEntity(sourcePos);
                        
                        if (destBe instanceof ChestBlockEntity destChest && sourceBe instanceof ChestBlockEntity sourceChest) {
                            if (sourceChest.hasCustomName()) destChest.setCustomName(sourceChest.getCustomName());
                            else destChest.setCustomName(Component.literal("Coffre de " + playerName));
                            
                            NonNullList<ItemStack> items = NonNullList.withSize(sourceChest.getContainerSize(), ItemStack.EMPTY);
                            for (int i = 0; i < sourceChest.getContainerSize(); i++) items.set(i, sourceChest.getItem(i).copy());
                            for (int i = 0; i < items.size(); i++) destChest.setItem(i, items.get(i));
                            
                            sourceChest.clearContent();
                            sourceChest.setChanged();
                            originLevel.sendBlockUpdated(sourcePos, originLevel.getBlockState(sourcePos), originLevel.getBlockState(sourcePos), 3);
                            
                            chestMapping.put(destPos, sourcePos);
                        }
                        currentChest++;
                    }
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

            BlockPos finalSpawn = new BlockPos(targetX, y, targetZ);

            for (ServerPlayer player : players) {
                player.teleportTo(explorationWorld, targetX + 0.5, y, targetZ + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("Bienvenue au camp d'exploration !"));
                player.getPersistentData().putLong("ExplorationOrigin", explo.origin.asLong());
            }
            
            int durationTicks = explo.durationMinutes > 0 ? explo.durationMinutes * 60 * 20 : 30 * 20;
            return new ActiveExploration(finalSpawn, durationTicks, explo.origin, chestMapping, builtBlocks);
            
        } else {
            System.err.println("Erreur: Le monde d'exploration n'est pas chargé !");
            return null;
        }
    }
    
    private BlockPos findSafeSpot(ServerLevel level, int startX, int startZ) {
        for (int r = 0; r < 500; r += 32) {
            for (int i = 0; i < 16; i++) {
                double angle = i * (Math.PI / 8);
                int x = startX + (int)(Math.cos(angle) * r);
                int z = startZ + (int)(Math.sin(angle) * r);
                
                level.getChunk(x >> 4, z >> 4);
                
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(new BlockPos(x, 64, z));
                if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) {
                    continue;
                }
                
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < 63) continue;
                
                BlockPos pos = new BlockPos(x, y, z);
                
                if (isAreaClear(level, pos)) {
                    return pos;
                }
            }
        }
        return new BlockPos(startX, 150, startZ);
    }
    
    private boolean isAreaClear(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(m).isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // --- Sauvegarde ---

    public enum ExplorationState {
        COUNTDOWN,
        TELEPORTING
    }

    public static class PendingExploration {
        public BlockPos origin;
        public int ticksRemaining;
        public int durationMinutes;
        public ExplorationState state = ExplorationState.COUNTDOWN;
        public int targetX;
        public int requiredPlayers;
        public int paidAmount;
        public Map<String, List<BlockPos>> playerChests;
        public UUID displayUUID;

        public PendingExploration(BlockPos origin, int durationMinutes, int targetX, int requiredPlayers, int paidAmount, Map<String, List<BlockPos>> playerChests, UUID displayUUID) {
            this.origin = origin;
            this.ticksRemaining = 30 * 20;
            this.durationMinutes = durationMinutes;
            this.targetX = targetX;
            this.requiredPlayers = requiredPlayers;
            this.paidAmount = paidAmount;
            this.playerChests = playerChests;
            this.displayUUID = displayUUID;
        }
        
        public PendingExploration(CompoundTag tag) {
            this.origin = BlockPos.of(tag.getLong("Origin"));
            this.ticksRemaining = tag.getInt("Ticks");
            this.durationMinutes = tag.getInt("Duration");
            this.targetX = tag.getInt("TargetX");
            this.requiredPlayers = tag.getInt("RequiredPlayers");
            this.paidAmount = tag.getInt("PaidAmount");
            if (tag.contains("State")) {
                this.state = ExplorationState.valueOf(tag.getString("State"));
            }
            if (tag.hasUUID("DisplayUUID")) {
                this.displayUUID = tag.getUUID("DisplayUUID");
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
            tag.putInt("PaidAmount", paidAmount);
            tag.putString("State", state.name());
            if (displayUUID != null) tag.putUUID("DisplayUUID", displayUUID);
            
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
        public BlockPos spawnPos;
        public int ticksRemaining;
        public BlockPos origin;
        public int targetX;
        public Map<BlockPos, BlockPos> chestMapping;
        public List<BlockPos> builtBlocks;
        
        public ActiveExploration(BlockPos spawnPos, int ticksRemaining, BlockPos origin, Map<BlockPos, BlockPos> chestMapping, List<BlockPos> builtBlocks) {
            this.spawnPos = spawnPos;
            this.targetX = spawnPos.getX();
            this.ticksRemaining = ticksRemaining;
            this.origin = origin;
            this.chestMapping = chestMapping;
            this.builtBlocks = builtBlocks;
        }
        
        public ActiveExploration(CompoundTag tag) {
            this.spawnPos = BlockPos.of(tag.getLong("SpawnPos"));
            this.targetX = tag.getInt("TargetX");
            this.ticksRemaining = tag.getInt("Ticks");
            this.origin = BlockPos.of(tag.getLong("Origin"));
            
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
            tag.putLong("SpawnPos", spawnPos.asLong());
            tag.putInt("TargetX", targetX);
            tag.putInt("Ticks", ticksRemaining);
            tag.putLong("Origin", origin.asLong());
            
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

        return tag;
    }
}
