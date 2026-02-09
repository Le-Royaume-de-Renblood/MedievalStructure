package fr.renblood.medievalstructures.item;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnProp;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.manager.RoomCreationManager;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketHighlightRoom;
import fr.renblood.medievalstructures.network.PacketVisualizeRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class InnkeeperWandItem extends Item {
    
    public InnkeeperWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            ServerLevel level = (ServerLevel) context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockState state = level.getBlockState(pos);
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            
            if (player == null) return InteractionResult.PASS;

            // Si on est en mode création, le clic droit est géré par l'event handler pour éviter les conflits
            if (RoomCreationManager.getInstance().isCreating(player)) {
                return InteractionResult.SUCCESS;
            }

            InnManager manager = InnManager.get(level);

            // Gestion des props (Shift + Clic Droit)
            // On vérifie si le bloc cliqué est un prop
            if (player.isCrouching()) {
                for (Inn inn : manager.getInns()) {
                    for (InnProp prop : inn.getProps()) {
                        if (prop.getPos().equals(pos)) {
                            player.sendSystemMessage(Component.literal("Prop ID: " + prop.getId() + " (Type: " + prop.getType() + ")"));
                            return InteractionResult.SUCCESS;
                        }
                    }
                }
            }

            if (state.getBlock() instanceof BedBlock) {
                if (player.isCrouching()) {
                    // Shift + Clic Droit sur un lit : Visualiser la chambre
                    for (Inn inn : manager.getInns()) {
                        for (InnRoom room : inn.getRooms()) {
                            if (isSameBedLocation(pos, room.getHeadPos()) || (room.getFootPos() != null && isSameBedLocation(pos, room.getFootPos()))) {
                                if (room.getP1() != null && room.getP2() != null) {
                                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketVisualizeRoom(room.getP1(), room.getP2(), 100)); // 5 secondes
                                    player.sendSystemMessage(Component.literal("Visualisation de la chambre " + room.getNumber() + " activée."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Cette chambre n'a pas de zone définie."));
                                }
                                return InteractionResult.SUCCESS;
                            }
                        }
                    }
                } else {
                    // Clic Droit normal sur un lit : Infos + Highlight props
                    for (Inn inn : manager.getInns()) {
                        for (InnRoom room : inn.getRooms()) {
                            if (isSameBedLocation(pos, room.getHeadPos()) || (room.getFootPos() != null && isSameBedLocation(pos, room.getFootPos()))) {
                                player.sendSystemMessage(Component.translatable("message.medieval_structures.wand.room_info", room.getNumber()));
                                player.sendSystemMessage(Component.translatable("message.medieval_structures.wand.price", room.getPrice()));
                                player.sendSystemMessage(Component.translatable("message.medieval_structures.wand.size", room.getSize()));
                                
                                String occupant = room.isBusy() ? room.getOccupantName() : "Aucun";
                                player.sendSystemMessage(Component.translatable("message.medieval_structures.wand.occupant", occupant));
                                
                                List<BlockPos> propsToHighlight = new ArrayList<>();
                                for (InnProp prop : inn.getProps()) {
                                    if (prop.getPos().distSqr(room.getHeadPos()) < 100) { 
                                        propsToHighlight.add(prop.getPos());
                                    }
                                }
                                
                                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketHighlightRoom(propsToHighlight));
                                return InteractionResult.SUCCESS;
                            }
                        }
                    }
                    player.sendSystemMessage(Component.translatable("message.medieval_structures.wand.no_room"));
                }
            }
        }
        return InteractionResult.PASS;
    }
    
    private boolean isSameBedLocation(BlockPos pos1, BlockPos pos2) {
        return pos1.equals(pos2);
    }
}
