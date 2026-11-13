package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import fr.renblood.medievalstructures.util.ExplorationCostCalculator;
import fr.renblood.medievalstructures.util.ExplorationProgression;
import fr.renblood.medievalstructures.util.ExplorationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class PacketLaunchExploration {
    private final BlockPos pos;

    public PacketLaunchExploration(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketLaunchExploration msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketLaunchExploration decode(FriendlyByteBuf buf) {
        return new PacketLaunchExploration(buf.readBlockPos());
    }

    public static void handle(PacketLaunchExploration msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall && hall.hasExplorationReady()) {
                    if(serverLevel!=player.server.overworld()){player.sendSystemMessage(Component.literal("Le hall de départ doit être dans le monde principal."));return;}
                    if(hall.getSavedConfigs().size()>10||hall.getSavedConfigs().stream().anyMatch(c->c.playerUUID==null||c.status!=PacketExplorationAction.InvitationStatus.ACCEPTED)){
                        player.sendSystemMessage(Component.literal("Tous les invités doivent accepter avant le départ."));return;
                    }
                    if(hall.getSavedConfigs().stream().map(c->c.playerUUID).distinct().count()!=hall.getSavedConfigs().size()){
                        player.sendSystemMessage(Component.literal("Un joueur ne peut figurer deux fois dans l’expédition."));return;
                    }
                    if (!player.blockPosition().closerThan(msg.pos, 8.0D)) {
                        player.sendSystemMessage(Component.literal("Vous êtes trop loin du hall d'exploration.").withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    
                    // Vérifier si une exploration est déjà en cours pour ce bloc
                    if (ExplorationManager.get(serverLevel).isCountdownActive(msg.pos)) {
                        player.sendSystemMessage(Component.literal("Une exploration est déjà en cours de lancement !").withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    
                    // 1. Vérifier les coffres disponibles
                    List<BlockPos> availableChests = ExplorationUtils.findChests(serverLevel, msg.pos);
                    int totalChestsNeeded = 0;
                    for (PacketExplorationAction.PlayerConfig config : hall.getSavedConfigs()) {
                        totalChestsNeeded += config.chestCount;
                    }
                    
                    if (availableChests.size() < totalChestsNeeded) {
                        player.sendSystemMessage(Component.literal("Pas assez de coffres ! Requis : " + totalChestsNeeded + ", Trouvés : " + availableChests.size()).withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    
                    // 2. Calculer le coût
                    int cost = ExplorationCostCalculator.calculateTotalCost(hall.getSavedDuration(), hall.getSavedConfigs());
                    int totalAnimals = hall.getSavedConfigs().stream().mapToInt(config -> config.animalCount).sum();

                    if (cost < 0 || hall.getSavedDuration() <= 0 || hall.getSavedConfigs().isEmpty()) {
                        player.sendSystemMessage(Component.literal("Configuration d'exploration invalide.").withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    if (!ExplorationProgression.isDurationAllowed(hall.getSavedDuration(), hall.getHallLevel(), player.getUUID())) {
                        player.sendSystemMessage(Component.literal("Cette duree n'est plus disponible pour ce hall ou vos talents.").withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    for (PacketExplorationAction.PlayerConfig config : hall.getSavedConfigs()) {
                        int maxChests = ExplorationProgression.getMaxChests(hall.getHallLevel(), config.playerUUID);
                        if (config.chestCount < 1
                                || config.chestCount > maxChests
                                || config.animalCount < 0
                                || config.animalCount > ExplorationProgression.MAX_ANIMALS_PER_PLAYER) {
                            player.sendSystemMessage(Component.literal("Configuration invalide pour " + config.playerName + ".").withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                    }

                    Optional<BlockPos> animalReturnPen = Optional.empty();
                    if (totalAnimals > 0) {
                        animalReturnPen = ExplorationManager.findAnimalReturnPen(serverLevel, msg.pos);
                        if (animalReturnPen.isEmpty()) {
                            player.sendSystemMessage(Component.literal("Impossible de lancer : construisez un enclos animaux 4x4 a moins de 20 blocs du hall.").withStyle(net.minecraft.ChatFormatting.RED));
                            player.sendSystemMessage(Component.literal("Format attendu : 12 barrieres en contour, sol complet juste dessous, interieur 2x2 vide.").withStyle(net.minecraft.ChatFormatting.YELLOW));
                            return;
                        }
                    }
                    

                    final BlockPos returnPen=animalReturnPen.orElse(null);
                    final int duration=hall.getSavedDuration();
                    final String configSnapshot=hall.getSavedConfigs().toString();
                    final String paymentKey=java.util.UUID.randomUUID().toString();
                    if(cost==0){
                        ExplorationManager manager=ExplorationManager.get(serverLevel);
                        Map<String,List<BlockPos>> chests=ExplorationUtils.assignAndRenameChests(serverLevel,msg.pos,hall.getSavedConfigs());
                        manager.startCountdown(serverLevel,msg.pos,duration,hall.getSavedConfigs().size(),totalAnimals,returnPen,0,chests,
                            ExplorationProgression.shouldSpawnBeds(player),ExplorationProgression.shouldSpawnLodestone(player),player.getUUID());
                        manager.setInvited(msg.pos,player.getUUID(),hall.getSavedConfigs().stream().map(c->c.playerUUID).toList());
                        manager.recordPayment(msg.pos,player.getUUID(),"free:"+paymentKey);hall.clearExploration();manager.persist(serverLevel);
                        player.sendSystemMessage(Component.literal("Départ préparé : cette formule est gratuite.").withStyle(net.minecraft.ChatFormatting.GREEN));return;
                    }
                    fr.renblood.medievalstructures.integration.CommerceAdapter.settle(player,paymentKey,"explorations",null,"Expéditions","EXPLORATION",
                        "launch:"+cost,cost,1,false,()->{
                            ExplorationManager manager=ExplorationManager.get(serverLevel);
                            if(serverLevel.getBlockEntity(msg.pos)!=hall||!hall.hasExplorationReady()||manager.isCountdownActive(msg.pos)
                                ||hall.getSavedDuration()!=duration||!hall.getSavedConfigs().toString().equals(configSnapshot))return false;
                            Map<String,List<BlockPos>> chests=ExplorationUtils.assignAndRenameChests(serverLevel,msg.pos,hall.getSavedConfigs());
                            manager.startCountdown(serverLevel,msg.pos,duration,hall.getSavedConfigs().size(),totalAnimals,returnPen,cost,chests,
                                ExplorationProgression.shouldSpawnBeds(player),ExplorationProgression.shouldSpawnLodestone(player),player.getUUID());
                            manager.setInvited(msg.pos,player.getUUID(),hall.getSavedConfigs().stream().map(c->c.playerUUID).toList());
                            manager.recordPayment(msg.pos,player.getUUID(),paymentKey);hall.clearExploration();return true;
                        },()->ExplorationManager.get(serverLevel).persist(serverLevel)).whenComplete((receipt,failure)->player.getServer().execute(()->{
                            player.sendSystemMessage(Component.literal(failure==null?"Exploration préparée. Paiement bancaire enregistré.":"Exploration en attente : vérifiez le reçu avant de recommencer."));
                        }));
                    
                } else {
                    player.sendSystemMessage(Component.literal("Aucune exploration prête à être lancée."));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
