package fr.renblood.medievalstructures.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncInnData {
    private final CompoundTag innData;

    // Constructeur utilisé par le serveur (prend un Inn)
    // On ne peut pas importer Inn ici si Inn utilise des classes client, mais Inn est safe.
    // Cependant, pour être sûr, on va utiliser un constructeur qui prend le NBT directement ou on garde l'import de Inn.
    // Inn est dans le package commun, donc c'est bon.
    public PacketSyncInnData(fr.renblood.medievalstructures.inn.Inn inn) {
        this.innData = inn.save();
    }

    public PacketSyncInnData(CompoundTag innData) {
        this.innData = innData;
    }

    public static void encode(PacketSyncInnData msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.innData);
    }

    public static PacketSyncInnData decode(FriendlyByteBuf buf) {
        return new PacketSyncInnData(buf.readNbt());
    }

    public static void handle(PacketSyncInnData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                fr.renblood.medievalstructures.client.ClientPacketHandler.handleSyncInnData(msg.innData)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
