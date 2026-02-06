package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.MedievalStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MedievalStructures.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, PacketSyncDefinitionMode.class, PacketSyncDefinitionMode::encode, PacketSyncDefinitionMode::decode, PacketSyncDefinitionMode::handle);
    }
}
