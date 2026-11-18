package fr.renblood.medievalstructures.manager;
import com.mojang.authlib.GameProfile;
import fr.renblood.medievalstructures.inn.*;
import fr.renblood.medievalstructures.init.ModBlocks;
import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("medieval_structures")
@PrefixGameTestTemplate(false)
public final class RoomCreationGameTests {
    private record Fixture(ServerPlayer player,Inn inn,BlockPos first,BlockPos last) {}
    private static Fixture setup(GameTestHelper h, int height) {
        var level=h.getLevel();var p=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"room-fixture"));
        p.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),p){@Override public void send(Packet<?> packet){}};
        // Separate heights avoid interactions with concurrent templates; only a compact 600-block volume.
        BlockPos first=h.absolutePos(new BlockPos(0,height,0));BlockPos last=first.offset(9,5,9);
        for(int x=0;x<10;x++)for(int y=0;y<6;y++)for(int z=0;z<10;z++)level.setBlock(first.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        BlockPos structure=first.above(6);level.setBlockAndUpdate(structure,ModBlocks.INN_STRUCTURE_BLOCK.get().defaultBlockState());
        var be=(InnStructureBlockEntity)level.getBlockEntity(structure);be.setVolume(first,last.above());
        var inn=new Inn("room-fixture-"+p.getUUID(),p.getUUID(),5,1);inn.setLocation(structure);InnManager.get(level).addInn(inn);
        level.setBlock(first,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD),2);
        var manager=RoomCreationManager.getInstance();manager.startCreation(p,inn.getName(),1,1,1);manager.setPos1(p,first);manager.setPos2(p,last);
        return new Fixture(p,inn,first,last);
    }
    private static void cleanup(GameTestHelper h,Fixture f) {
        RoomCreationManager.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.player()));
        InnManager.get(h.getLevel()).removeInn(f.inn().getName());
    }
    @GameTest(template="exploration_empty")
    public static void roomIsCommittedOnlyAfterIncrementalValidation(GameTestHelper h) {
        var f=setup(h,40);var manager=RoomCreationManager.getInstance();var server=h.getLevel().getServer();
        try {
            manager.validate(f.player());h.assertTrue(f.inn().getRoom(1)==null,"Confirm queues work without immediate whole-volume scan");
            manager.tick(server,p->p==f.player());h.assertTrue(f.inn().getRoom(1)==null,"600-block room still pending after first 512 reads");
            manager.tick(server,p->p==f.player());h.assertTrue(f.inn().getRoom(1)!=null&&!manager.isCreating(f.player()),"Room committed and selection closed at completion");
        }finally{cleanup(h,f);}h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void editingOrLoggingOutCancelsPendingRoom(GameTestHelper h) {
        var f=setup(h,60);var manager=RoomCreationManager.getInstance();var server=h.getLevel().getServer();
        try {
            manager.validate(f.player());manager.setPos2(f.player(),f.first().offset(2,0,0));
            manager.tick(server,p->p==f.player());h.assertTrue(f.inn().getRoom(1)==null,"Changing corner cancels the old job");
            manager.validate(f.player());RoomCreationManager.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.player()));
            manager.tick(server,p->p==f.player());h.assertTrue(f.inn().getRoom(1)==null&&!manager.isCreating(f.player()),"Logout leaves no room or pending selection");
        }finally{cleanup(h,f);}h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void deletedInnAndStaleSessionCannotCommitRoom(GameTestHelper h) {
        var f=setup(h,80);var manager=RoomCreationManager.getInstance();var server=h.getLevel().getServer();
        try {
            manager.validate(f.player());manager.tick(server,p->false);
            h.assertTrue(f.inn().getRoom(1)==null,"Replaced/disconnected session is rejected before scan");
            manager.validate(f.player());InnManager.get(h.getLevel()).removeInn(f.inn().getName());
            manager.tick(server,p->p==f.player());manager.tick(server,p->p==f.player());
            h.assertTrue(f.inn().getRoom(1)==null,"Inn deletion during scan cannot recreate an orphan room");
        }finally{cleanup(h,f);}h.succeed();
    }
}
