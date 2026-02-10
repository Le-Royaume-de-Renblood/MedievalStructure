package fr.renblood.medievalstructures.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketExplorationAction {
    private final BlockPos pos;
    private final int duration;
    private final List<PlayerConfig> playerConfigs;

    public static class PlayerConfig {
        public int chestCount;
        public int animalCount;

        public PlayerConfig(int chestCount, int animalCount) {
            this.chestCount = chestCount;
            this.animalCount = animalCount;
        }
    }

    public PacketExplorationAction(BlockPos pos, int duration, List<PlayerConfig> playerConfigs) {
        this.pos = pos;
        this.duration = duration;
        this.playerConfigs = playerConfigs;
    }

    public static void encode(PacketExplorationAction msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.duration);
        buf.writeInt(msg.playerConfigs.size());
        for (PlayerConfig config : msg.playerConfigs) {
            buf.writeInt(config.chestCount);
            buf.writeInt(config.animalCount);
        }
    }

    public static PacketExplorationAction decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int duration = buf.readInt();
        int size = buf.readInt();
        List<PlayerConfig> configs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            configs.add(new PlayerConfig(buf.readInt(), buf.readInt()));
        }
        return new PacketExplorationAction(pos, duration, configs);
    }

    public static void handle(PacketExplorationAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                BlockPos pos = msg.pos;

                // Simulation : Poser 2 coffres à gauche et à droite
                BlockState chestState = Blocks.CHEST.defaultBlockState();
                level.setBlock(pos.east(), chestState, 3);
                level.setBlock(pos.west(), chestState, 3);
                
                // Récapitulatif dans le chat
                player.sendSystemMessage(Component.literal("--- Exploration Organisée ---"));
                player.sendSystemMessage(Component.literal("Durée : " + msg.duration + " min"));
                player.sendSystemMessage(Component.literal("Nombre de joueurs : " + msg.playerConfigs.size()));
                
                for (int i = 0; i < msg.playerConfigs.size(); i++) {
                    PlayerConfig config = msg.playerConfigs.get(i);
                    player.sendSystemMessage(Component.literal("Joueur " + (i + 1) + " : " + config.chestCount + " coffres, " + config.animalCount + " animaux"));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
