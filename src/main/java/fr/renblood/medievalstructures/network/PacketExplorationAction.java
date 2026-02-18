package fr.renblood.medievalstructures.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketExplorationAction {
    private final BlockPos pos;
    private final int duration;
    private final List<PlayerConfig> playerConfigs;

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    public static class PlayerConfig {
        public int chestCount;
        public int animalCount;
        public String playerName;
        public UUID playerUUID;
        public InvitationStatus status;

        public PlayerConfig(int chestCount, int animalCount) {
            this(chestCount, animalCount, "", null, InvitationStatus.PENDING);
        }

        public PlayerConfig(int chestCount, int animalCount, String playerName) {
            this(chestCount, animalCount, playerName, null, InvitationStatus.PENDING);
        }
        
        public PlayerConfig(int chestCount, int animalCount, String playerName, UUID uuid, InvitationStatus status) {
            this.chestCount = chestCount;
            this.animalCount = animalCount;
            this.playerName = playerName;
            this.playerUUID = uuid;
            this.status = status;
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
            buf.writeUtf(config.playerName != null ? config.playerName : "");
            buf.writeBoolean(config.playerUUID != null);
            if (config.playerUUID != null) {
                buf.writeUUID(config.playerUUID);
            }
            buf.writeEnum(config.status);
        }
    }

    public static PacketExplorationAction decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int duration = buf.readInt();
        int size = buf.readInt();
        List<PlayerConfig> configs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int chests = buf.readInt();
            int animals = buf.readInt();
            String name = buf.readUtf();
            UUID uuid = null;
            if (buf.readBoolean()) {
                uuid = buf.readUUID();
            }
            InvitationStatus status = buf.readEnum(InvitationStatus.class);
            configs.add(new PlayerConfig(chests, animals, name, uuid, status));
        }
        return new PacketExplorationAction(pos, duration, configs);
    }

    public static void handle(PacketExplorationAction msg, Supplier<NetworkEvent.Context> ctx) {
        // Ce paquet n'est plus utilisé directement pour l'action, mais la classe interne PlayerConfig est partagée.
        // La logique est dans PacketSaveExploration.
        ctx.get().setPacketHandled(true);
    }
}
