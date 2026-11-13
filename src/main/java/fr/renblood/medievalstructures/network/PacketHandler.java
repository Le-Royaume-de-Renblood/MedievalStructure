package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.MedievalStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "3";
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
        INSTANCE.registerMessage(id++, PacketSyncExplorationState.class, PacketSyncExplorationState::encode, PacketSyncExplorationState::decode, PacketSyncExplorationState::handle);
        INSTANCE.registerMessage(id++, PacketCancelExploration.class, PacketCancelExploration::encode, PacketCancelExploration::decode, PacketCancelExploration::handle);
        INSTANCE.registerMessage(id++, PacketResendInvitation.class, PacketResendInvitation::encode, PacketResendInvitation::decode, PacketResendInvitation::handle);
        INSTANCE.registerMessage(id++, PacketRemovePlayer.class, PacketRemovePlayer::encode, PacketRemovePlayer::decode, PacketRemovePlayer::handle);
        INSTANCE.registerMessage(id++, PacketHighlightChests.class, PacketHighlightChests::encode, PacketHighlightChests::decode, PacketHighlightChests::handle);
        INSTANCE.registerMessage(id++, PacketRequestChestHighlight.class, PacketRequestChestHighlight::encode, PacketRequestChestHighlight::decode, PacketRequestChestHighlight::handle);
        INSTANCE.registerMessage(id++, PacketRequestExplorationState.class, PacketRequestExplorationState::encode, PacketRequestExplorationState::decode, PacketRequestExplorationState::handle);
        INSTANCE.registerMessage(id++, PacketInnMenuAction.class, PacketInnMenuAction::encode, PacketInnMenuAction::decode, PacketInnMenuAction::handle);
        INSTANCE.registerMessage(id++, PacketRequestInnReferences.class, PacketRequestInnReferences::encode, PacketRequestInnReferences::decode, PacketRequestInnReferences::handle);
        INSTANCE.registerMessage(id++, PacketSyncInnReferences.class, PacketSyncInnReferences::encode, PacketSyncInnReferences::decode, PacketSyncInnReferences::handle);
        INSTANCE.registerMessage(id++, PacketOpenInnTradeCatalog.class, PacketOpenInnTradeCatalog::encode, PacketOpenInnTradeCatalog::decode, PacketOpenInnTradeCatalog::handle);
        INSTANCE.registerMessage(id++, PacketInnReferenceAdminAction.class, PacketInnReferenceAdminAction::encode, PacketInnReferenceAdminAction::decode, PacketInnReferenceAdminAction::handle);
        INSTANCE.registerMessage(id++, PacketSyncInnReferenceSuggestion.class, PacketSyncInnReferenceSuggestion::encode, PacketSyncInnReferenceSuggestion::decode, PacketSyncInnReferenceSuggestion::handle);
        INSTANCE.registerMessage(id++, PacketRequestInnRoutes.class, PacketRequestInnRoutes::encode, PacketRequestInnRoutes::decode, PacketRequestInnRoutes::handle);
        INSTANCE.registerMessage(id++, PacketSyncInnRoutes.class, PacketSyncInnRoutes::encode, PacketSyncInnRoutes::decode, PacketSyncInnRoutes::handle);
        INSTANCE.registerMessage(id++, PacketInnRouteAction.class, PacketInnRouteAction::encode, PacketInnRouteAction::decode, PacketInnRouteAction::handle);
    }
}
