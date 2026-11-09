package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.InnStructureBlock;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import fr.renblood.medievalstructures.manager.InnCustomerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketInnAction {
    private final String innName;
    private final int action; // 0: toggle active, 1: rent room, 2: collect money, 3: add employee, 4: remove employee, 5: evict, 6: pay rent
    private final int roomNumber;
    private final String extraData; // Pour le nom/UUID de l'employé

    public PacketInnAction(String innName, int action, int roomNumber) {
        this(innName, action, roomNumber, "");
    }

    public PacketInnAction(String innName, int action, int roomNumber, String extraData) {
        this.innName = innName;
        this.action = action;
        this.roomNumber = roomNumber;
        this.extraData = extraData;
    }

    public static void encode(PacketInnAction msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.innName);
        buf.writeInt(msg.action);
        buf.writeInt(msg.roomNumber);
        buf.writeUtf(msg.extraData);
    }

    public static PacketInnAction decode(FriendlyByteBuf buf) {
        return new PacketInnAction(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readUtf());
    }

    public static void handle(PacketInnAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                InnManager manager = InnManager.get(level);
                Inn inn = manager.getInn(msg.innName);

                if (inn != null) {
                    boolean updated = false;
                    
                    if (msg.action == 0) { // Toggle Active
                        if (inn.getOwnerUUID().equals(player.getUUID())) {
                            inn.setActive(!inn.isActive());
                            if (!inn.isActive()) {
                                NpcShopkeeperIntegration.clearInnCustomers(level, inn.getName());
                                InnCustomerManager.clearSchedule(inn.getName());
                            }
                            manager.setDirty();
                            updated = true;
                            
                            // Mettre à jour le bloc
                            if (inn.getLocation() != null) {
                                BlockPos pos = inn.getLocation();
                                BlockState state = level.getBlockState(pos);
                                if (state.getBlock() instanceof InnStructureBlock) {
                                    level.setBlock(pos, state.setValue(InnStructureBlock.ACTIVE, inn.isActive()), 3);
                                }
                            }
                        }
                    } else if (msg.action == 1) { // Rent Room (Customer)
                        fr.renblood.medievalstructures.integration.InnCommerceService.rent(player,inn,manager,msg.roomNumber);
                    } else if (msg.action == 2) {
                        fr.renblood.medievalstructures.integration.InnCommerceService.claim(player,inn);
                    } else if (msg.action == 3) { // Add Employee
                        if (inn.getOwnerUUID().equals(player.getUUID())) {
                            ServerPlayer employee = level.getServer().getPlayerList().getPlayerByName(msg.extraData);
                            if (employee != null) {
                                inn.addEmployee(employee.getUUID(), employee.getName().getString());
                                manager.setDirty();
                                updated = true;
                                player.sendSystemMessage(Component.literal("Employé ajouté : " + msg.extraData));
                            } else {
                                player.sendSystemMessage(Component.literal("Joueur introuvable."));
                            }
                        }
                    } else if (msg.action == 4) { // Remove Employee
                        if (inn.getOwnerUUID().equals(player.getUUID())) {
                            try {
                                UUID uuid = UUID.fromString(msg.extraData);
                                inn.removeEmployee(uuid);
                                manager.setDirty();
                                updated = true;
                                player.sendSystemMessage(Component.literal("Employé retiré."));
                            } catch (IllegalArgumentException e) {
                                // UUID invalide
                            }
                        }
                    } else if (msg.action == 5) { // Evict (Exclure)
                        if (inn.getOwnerUUID().equals(player.getUUID()) || inn.isEmployee(player.getUUID())) {
                            InnRoom room = inn.getRoom(msg.roomNumber);
                            if (room != null && room.isBusy()) {
                                String occupant = room.getOccupantName();
                                room.setBusy(false);
                                room.setOccupantName("");
                                room.setOccupantUUID(null);
                                manager.setDirty();
                                updated = true;
                                player.sendSystemMessage(Component.literal("Occupant " + occupant + " exclu de la chambre " + msg.roomNumber + "."));
                            }
                        }
                    } else if (msg.action == 6) { // Pay Rent (Payer Loyer)
                        fr.renblood.medievalstructures.integration.InnCommerceService.pay(player,inn,manager,msg.roomNumber,msg.extraData);
                    }
                    
                    // Si une modification a eu lieu, on renvoie les données mises à jour au joueur
                    if (updated) {
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncInnData(inn));
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
