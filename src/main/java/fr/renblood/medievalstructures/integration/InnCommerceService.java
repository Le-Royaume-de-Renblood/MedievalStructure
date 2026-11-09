package fr.renblood.medievalstructures.integration;

import fr.renblood.medievalstructures.inn.*;
import fr.renblood.medievalstructures.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

public final class InnCommerceService {
    private InnCommerceService() {}
    public static String business(Inn inn){return "inn:"+inn.getEconomyId();}
    private static void sync(ServerPlayer player,Inn inn){PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new PacketSyncInnData(inn));}
    public static void rent(ServerPlayer player,Inn inn,InnManager manager,int number){
        InnRoom room=inn.getRoom(number);
        if(room==null||room.isBusy()||room.isDirty()||!inn.isActive())return;
        long price;try{price=CommerceAdapter.amount(room.getPrice());}catch(IllegalArgumentException error){player.sendSystemMessage(Component.literal(error.getMessage()));return;}
        String key=UUID.randomUUID().toString();
        CommerceAdapter.settle(player,key,business(inn),inn.getOwnerUUID().toString(),inn.getName(),"INN","room:"+number,price,1,false,()->{
            if(manager.getInn(inn.getName())!=inn||!inn.isActive()||room.isBusy()||room.isDirty())return false;
            room.setBusy(true);room.setOccupantUUID(player.getUUID());room.setOccupantName(player.getName().getString());room.setBalance(0);
            manager.setDirty();return true;
        },()->manager.persist(player.serverLevel())).whenComplete((receipt,failure)->player.getServer().execute(()->{
            if(failure!=null)player.sendSystemMessage(Component.literal("Location en attente : aucun débit supposé. Vérifiez vos finances."));
            sync(player,inn);
        }));
    }
    public static void pay(ServerPlayer player,Inn inn,InnManager manager,int number,String input){
        InnRoom room=inn.getRoom(number);if(room==null||!player.getUUID().equals(room.getOccupantUUID()))return;
        long amount;try{if(!input.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException();amount=Long.parseLong(input);if(amount>9007199254740991L)throw new IllegalArgumentException();}
        catch(IllegalArgumentException failure){player.sendSystemMessage(Component.literal("Indiquez un montant entier positif en fer."));return;}
        String key=UUID.randomUUID().toString();
        CommerceAdapter.settle(player,key,business(inn),inn.getOwnerUUID().toString(),inn.getName(),"INN","advance:"+key,amount,1,false,()->{
            if(manager.getInn(inn.getName())!=inn||!player.getUUID().equals(room.getOccupantUUID()))return false;
            double next=room.getBalance()+amount;if(!Double.isFinite(next)||Math.abs(next)>9007199254740991D)return false;
            room.addToBalance(amount);manager.setDirty();return true;
        },()->manager.persist(player.serverLevel())).whenComplete((receipt,failure)->player.getServer().execute(()->sync(player,inn)));
    }
    public static void claim(ServerPlayer player,Inn inn){
        if(!inn.getOwnerUUID().equals(player.getUUID()))return;
        CommerceAdapter.claim(player,UUID.randomUUID().toString(),business(inn)).whenComplete((receipt,failure)->player.getServer().execute(()->{
            if(failure!=null)player.sendSystemMessage(Component.literal("Seules les recettes confirmées dans la caisse peuvent être transférées en banque."));
            sync(player,inn);
        }));
    }
}
