package fr.renblood.medievalstructures.manager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("medieval_structures")
@PrefixGameTestTemplate(false)
public final class RoomScanGameTests {
    @GameTest(template="exploration_empty")
    public static void largeRoomNeverExceedsItsTickBudget(GameTestHelper h) {
        var reads=new AtomicInteger();var scan=new RoomBedScan(BlockPos.ZERO,new BlockPos(99,9,9),p->true,p->{reads.incrementAndGet();return p.equals(new BlockPos(99,9,9));});
        h.assertTrue(scan.tick(512)==RoomBedScan.State.WORKING&&reads.get()==512,"Only 512 of 10000 blocks read this tick");
        RoomBedScan.State state=RoomBedScan.State.WORKING;
        while(state==RoomBedScan.State.WORKING){int before=reads.get();state=scan.tick(512);h.assertTrue(reads.get()-before<=512,"Bound respected on every continuation");}
        h.assertTrue(reads.get()==10000&&scan.bed().equals(new BlockPos(99,9,9)),"Inclusive volume visited exactly once and last-corner bed found");h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void multipleBedsStopTheScanEarly(GameTestHelper h) {
        var reads=new AtomicInteger();var scan=new RoomBedScan(BlockPos.ZERO,new BlockPos(500,50,50),p->true,p->reads.incrementAndGet()<=2);
        h.assertTrue(scan.tick(512)==RoomBedScan.State.MULTIPLE_BEDS&&reads.get()==2,"Stop immediately at second bed, even in enormous selection");
        scan.tick(512);h.assertTrue(reads.get()==2,"Completed rejection does not restart scanning");h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void unloadedChunksAreNeverReadOrForced(GameTestHelper h) {
        var reads=new AtomicInteger();var scan=new RoomBedScan(BlockPos.ZERO,new BlockPos(20,0,0),p->p.getX()<16,p->{reads.incrementAndGet();return false;});
        h.assertTrue(scan.tick(512)==RoomBedScan.State.UNLOADED&&reads.get()==16,"No block read beyond loaded chunk boundary");h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void reverseCornersAndImmutableBedWorkAcrossTicks(GameTestHelper h) {
        var visits=new HashSet<BlockPos>();BlockPos bed=new BlockPos(-2,70,-2);
        var scan=new RoomBedScan(new BlockPos(0,71,0),bed,p->true,p->{h.assertTrue(visits.add(p.immutable()),"No duplicate block");return p.equals(bed);});
        while(scan.tick(1)==RoomBedScan.State.WORKING){}
        h.assertTrue(visits.size()==18&&scan.bed().equals(bed),"Reversed negative coordinates and immutable result preserved across cursor moves");h.succeed();
    }
    @GameTest(template="exploration_empty")
    public static void emptyRoomCompletesWithoutInventingBed(GameTestHelper h) {
        var scan=new RoomBedScan(BlockPos.ZERO,BlockPos.ZERO,p->true,p->false);
        h.assertTrue(scan.tick(0)==RoomBedScan.State.WORKING,"Zero budget does no work");
        h.assertTrue(scan.tick(1)==RoomBedScan.State.COMPLETE&&scan.bed()==null,"No bed gives explicit empty result");h.succeed();
    }
}
