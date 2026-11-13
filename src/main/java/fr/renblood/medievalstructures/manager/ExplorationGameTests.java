package fr.renblood.medievalstructures.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import fr.renblood.medievalstructures.init.ModDimensions;
import fr.renblood.medievalstructures.util.ExplorationViewport;
import java.util.*;
import java.util.concurrent.atomic.*;

@GameTestHolder("medieval_structures")
@PrefixGameTestTemplate(false)
public final class ExplorationGameTests {
    @GameTest(template="exploration_empty")
    public static void generationNeverWaitsAndTicketsAreReleased(GameTestHelper h) {
        var requested=new HashSet<ChunkPos>();var released=new HashSet<ChunkPos>();var loaded=new HashSet<ChunkPos>();
        var clock=new AtomicLong();var chunks=List.of(new ChunkPos(1,1),new ChunkPos(2,1),new ChunkPos(3,1));
        var job=new ChunkPreparation(chunks,requested::add,loaded::contains,released::add,clock::get,100);
        h.assertTrue(job.tick()==ChunkPreparation.State.WORKING&&requested.size()==1,"At most one ticket request per tick, no wait for a chunk");
        job.tick();job.tick();h.assertTrue(requested.size()==3&&released.isEmpty(),"Keep only the bounded batch alive while generation runs");
        loaded.addAll(chunks);h.assertTrue(job.tick()==ChunkPreparation.State.READY&&job.progress()==100,"Poll readiness without a future join/get");
        job.close();job.close();h.assertTrue(released.equals(requested),"Every owned ticket released, including repeated close");h.succeed();
    }

    @GameTest(template="exploration_empty")
    public static void timedOutOrFailedPreparationDoesNotLeakTickets(GameTestHelper h) {
        var released=new AtomicInteger();var clock=new AtomicLong();
        var job=new ChunkPreparation(List.of(new ChunkPos(1,2)),p->{},p->false,p->released.incrementAndGet(),clock::get,100);
        job.tick();clock.set(101);h.assertTrue(job.tick()==ChunkPreparation.State.FAILED&&released.get()==1,"Timeout is recoverable, not a busy loop");
        var failed=new ChunkPreparation(List.of(new ChunkPos(2,3)),p->{throw new IllegalStateException("fixture");},p->false,p->released.incrementAndGet(),clock::get,100);
        h.assertTrue(failed.tick()==ChunkPreparation.State.FAILED&&released.get()==2,"Partial request failures also release their ticket");h.succeed();
    }

    @GameTest(template="exploration_empty")
    public static void hallFitsAndPointerTransformIsConsistent(GameTestHelper h) {
        for(int[] size:new int[][]{{320,240},{426,240},{640,360},{854,480},{1280,720},{1920,1080}}){
            var v=ExplorationViewport.fit(size[0],size[1]);
            h.assertTrue(560*v.scale()<=size[0]-15&&380*v.scale()<=size[1]-15,"Entire panel stays on screen at every GUI scale");
            h.assertTrue(Math.abs(v.logical(214*v.scale())-214)<.001,"Buttons and drawing use the same coordinates");
        }h.succeed();
    }

    @GameTest(template="exploration_empty")
    public static void unknownLegacyStateAndPaymentArePreserved(GameTestHelper h) {
        var pending=new ExplorationManager.PendingExploration(new BlockPos(0,70,0),20,0,1,0,null,128,Map.of(),null,false,false,UUID.randomUUID());
        pending.paymentKey="fixture-existing-receipt";pending.invited.add(pending.launcherUuid);
        CompoundTag saved=pending.save();saved.putString("State","UNKNOWN_OLD_STATE");
        var restored=new ExplorationManager.PendingExploration(saved);
        h.assertTrue(restored.state==ExplorationManager.ExplorationState.FAILED&&restored.paidAmount==128
                &&restored.paymentKey.equals(pending.paymentKey)&&restored.invited.equals(pending.invited),"Unreadable phase preserves receipt and allows manual cancellation");h.succeed();
    }

    @GameTest(template="exploration_empty",timeoutTicks=1800)
    public static void remoteCampLaunchReturnAndCleanupKeepServerTicking(GameTestHelper h) { remoteCycle(h,16000,false,false); }

    @GameTest(template="exploration_empty",timeoutTicks=1800)
    public static void blockedHallRetainsChestAndItsNbt(GameTestHelper h) { remoteCycle(h,24000,true,false); }

    @GameTest(template="exploration_empty",timeoutTicks=1800)
    public static void cancellingPartialCampRestoresTerrainAndKeepsOriginalChest(GameTestHelper h) { remoteCycle(h,32000,false,true); }

    @GameTest(template="exploration_empty")
    public static void statusPacketRoundTripPreservesTargetAndProgress(GameTestHelper h) {
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet=new fr.renblood.medievalstructures.network.PacketSyncExplorationState(false,false,new BlockPos(-7,72,15),"Construction du camp",0,52,true);
            fr.renblood.medievalstructures.network.PacketSyncExplorationState.encode(packet,buf);
            h.assertTrue(packet.equals(fr.renblood.medievalstructures.network.PacketSyncExplorationState.decode(buf)),"A late status can be tied to its hall, phase and cancellation state");
        } finally {buf.release();}h.succeed();
    }

    private static void remoteCycle(GameTestHelper h,int coordinate,boolean blockHall,boolean cancel) {
        var origin=h.absolutePos(new BlockPos(1,2,1));
        BlockPos chestPos=h.absolutePos(new BlockPos(2,2,2));h.setBlock(new BlockPos(2,2,2),Blocks.CHEST);
        var source=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(chestPos);
        var original=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7);
        original.setHoverName(net.minecraft.network.chat.Component.literal("Recette exploration — NBT conservé"));source.setItem(0,original.copy());
        var pending=new ExplorationManager.PendingExploration(origin,1,coordinate,0,0,null,0,Map.of("Recette",List.of(chestPos)),null,false,false,null);
        CompoundTag row=pending.save();row.putString("State","TELEPORTING");row.putInt("Ticks",0);
        BlockPos camp=new BlockPos(coordinate,220,coordinate);row.putLong("CampPos",camp.asLong());
        ListTag rows=new ListTag();rows.add(row);CompoundTag data=new CompoundTag();data.put("Pending",rows);
        AtomicReference<ExplorationManager> manager=new AtomicReference<>(ExplorationManager.load(data));
        AtomicBoolean started=new AtomicBoolean();AtomicInteger turns=new AtomicInteger();AtomicReference<BlockPos> movedChest=new AtomicReference<>();
        var dimension=h.getLevel().getServer().getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        h.assertTrue(dimension!=null,"Exploration dimension exists in dedicated server");
        h.startSequence().thenWaitUntil(()->{
            turns.incrementAndGet();manager.get().tick(h.getLevel().getServer());
            CompoundTag state=manager.get().save(new CompoundTag());ListTag active=state.getList("Active",Tag.TAG_COMPOUND);
            if(cancel&&!started.get()&&state.getList("Pending",Tag.TAG_COMPOUND).size()==1
                    &&!state.getList("Pending",Tag.TAG_COMPOUND).getCompound(0).getList("CampOriginals",Tag.TAG_COMPOUND).isEmpty()){
                h.assertTrue(active.isEmpty(),"Cancel while camp is being built, before moving a chest");
                started.set(true);manager.get().cancelCountdown(h.getLevel(),origin);
                state=manager.get().save(new CompoundTag());
            }
            if(!cancel&&!started.get()&&!active.isEmpty()){
                started.set(true);var savedActive=active.getCompound(0);
                var mapping=savedActive.getList("ChestMapping",Tag.TAG_COMPOUND);
                h.assertTrue(mapping.size()==1&&source.isEmpty(),"Exactly one physical chest transferred to the camp");
                movedChest.set(BlockPos.of(mapping.getCompound(0).getLong("Dest")));
                if(blockHall)source.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT));
                savedActive.putInt("Ticks",1);manager.set(ExplorationManager.load(state));
            }
            h.assertTrue(started.get()&&state.getList("Active",Tag.TAG_COMPOUND).isEmpty()
                    &&state.getList("Pending",Tag.TAG_COMPOUND).isEmpty()&&state.getLongArray("CleanupBlocks").length==0,"Wait for asynchronous preparation, bounded construction, return and cleanup");
        }).thenExecute(()->{
            h.assertTrue(turns.get()>9,"Remote preparation progressed over multiple server ticks");
            if(blockHall){
                h.assertTrue(source.getItem(0).is(net.minecraft.world.item.Items.DIRT),"Existing hall contents are not overwritten");
                var kept=(net.minecraft.world.level.block.entity.ChestBlockEntity)dimension.getBlockEntity(movedChest.get());
                h.assertTrue(kept!=null&&net.minecraft.world.item.ItemStack.matches(original,kept.getItem(0))&&manager.get().retainedChestPositions().contains(movedChest.get()),"Blocked chest and NBT retained for staff recovery");
            } else h.assertTrue(net.minecraft.world.item.ItemStack.matches(original,source.getItem(0)),"Named items and exact count survive return or cancelled preparation");
            h.assertTrue(dimension.getBlockState(camp.below()).isAir(),"Cleanup restores original terrain instead of leaving a hole or a dirt platform");
        }).thenSucceed();
    }
}
