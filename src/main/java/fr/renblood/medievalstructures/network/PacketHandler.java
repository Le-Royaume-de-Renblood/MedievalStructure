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
        INSTANCE.registerMessage(id++, PacketSyncInnData.class, PacketSyncInnData::encode, PacketSyncInnData::decode, PacketSyncInnData::handle);
        INSTANCE.registerMessage(id++, PacketInnAction.class, PacketInnAction::encode, PacketInnAction::decode, PacketInnAction::handle);
        INSTANCE.registerMessage(id++, PacketHighlightRoom.class, PacketHighlightRoom::encode, PacketHighlightRoom::decode, PacketHighlightRoom::handle);
        INSTANCE.registerMessage(id++, PacketSyncRoomSelection.class, PacketSyncRoomSelection::encode, PacketSyncRoomSelection::decode, PacketSyncRoomSelection::handle);
        INSTANCE.registerMessage(id++, PacketVisualizeRoom.class, PacketVisualizeRoom::encode, PacketVisualizeRoom::decode, PacketVisualizeRoom::handle);
        INSTANCE.registerMessage(id++, PacketRequestDoorInfo.class, PacketRequestDoorInfo::encode, PacketRequestDoorInfo::decode, PacketRequestDoorInfo::handle);
        INSTANCE.registerMessage(id++, PacketExplorationAction.class, PacketExplorationAction::encode, PacketExplorationAction::decode, PacketExplorationAction::handle);
        INSTANCE.registerMessage(id++, PacketSaveExploration.class, PacketSaveExploration::encode, PacketSaveExploration::decode, PacketSaveExploration::handle);
        INSTANCE.registerMessage(id++, PacketLaunchExploration.class, PacketLaunchExploration::encode, PacketLaunchExploration::decode, PacketLaunchExploration::handle);
    }
}
