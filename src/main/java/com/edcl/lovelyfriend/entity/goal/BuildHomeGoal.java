package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Builds a simple 5×5 wooden house (3 walls tall + roof) and sets homePos when done.
 * Layout: outer 5×5, inner 3×3, door gap on south face at x=2 y=1..2.
 * Requires 71 planks (logs counted as 4 each).
 */
public class BuildHomeGoal extends Goal {

    private static final int  PLANKS_NEEDED = 71;
    private static final int  COOLDOWN      = 1200;
    private static final double WORK_DIST_SQ = 196.0; // 14 blocks horiz

    private final FriendEntity entity;
    private BlockPos     buildOrigin;
    private List<BlockPos> plan;
    private int          buildIndex;
    private int          cooldown;
    private boolean      building;

    public BuildHomeGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.HOME_BUILDING) return false;
        if (entity.getTarget() != null) return false;
        if (!hasEnoughMaterials()) return false;
        if (entity.getRandom().nextFloat() > 0.1f) return false;
        buildOrigin = findBuildSite();
        return buildOrigin != null;
    }

    @Override
    public boolean canContinueToUse() {
        return building
                && entity.getGameStage() == GameStage.HOME_BUILDING
                && entity.getTarget() == null;
    }

    @Override
    public void start() {
        convertLogsToPlanks();
        plan = generatePlan(buildOrigin);
        buildIndex = 0;
        building = true;
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        building = false;
        plan = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (plan == null) return;

        // Skip positions already occupied (resumed build or pre-existing blocks)
        while (buildIndex < plan.size()) {
            BlockState state = entity.level().getBlockState(plan.get(buildIndex));
            if (state.isAir() || state.canBeReplaced()) break;
            buildIndex++;
        }

        if (buildIndex >= plan.size()) {
            entity.setHomePos(buildOrigin);
            building = false;
            return;
        }

        // Navigate close to build area (no block-reach limit on direct setBlock)
        double cx = buildOrigin.getX() + 2.5;
        double cz = buildOrigin.getZ() + 2.5;
        double dx = entity.getX() - cx, dz = entity.getZ() - cz;
        if (dx * dx + dz * dz > WORK_DIST_SQ) {
            if (!entity.getNavigation().isInProgress())
                entity.getNavigation().moveTo(cx, buildOrigin.getY(), cz, 1.0);
            return;
        }

        // Place one block per tick
        if (!(entity.level() instanceof ServerLevel sl)) return;
        if (!removePlank()) { building = false; return; }
        sl.setBlock(plan.get(buildIndex), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        buildIndex++;
    }

    // --- Build plan ---

    /**
     * 5×5 house at origin:
     *   Walls y+1..y+3: north (z=0) + south (z=4, door gap at x=2 y=1,2) + west (x=0) + east (x=4)
     *   Roof y+4: full 5×5
     */
    private List<BlockPos> generatePlan(BlockPos o) {
        List<BlockPos> list = new ArrayList<>(PLANKS_NEEDED + 4);
        int ox = o.getX(), oy = o.getY(), oz = o.getZ();

        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx <= 4; dx++) {
                list.add(new BlockPos(ox + dx, oy + dy, oz));          // north
                if (!(dx == 2 && dy <= 2))                             // south — skip door gap
                    list.add(new BlockPos(ox + dx, oy + dy, oz + 4));
            }
            for (int dz = 1; dz <= 3; dz++) {
                list.add(new BlockPos(ox,     oy + dy, oz + dz));      // west
                list.add(new BlockPos(ox + 4, oy + dy, oz + dz));      // east
            }
        }

        for (int dx = 0; dx <= 4; dx++)
            for (int dz = 0; dz <= 4; dz++)
                list.add(new BlockPos(ox + dx, oy + 4, oz + dz));      // roof

        return list;
    }

    // --- Site finder ---

    private BlockPos findBuildSite() {
        Level level = entity.level();
        BlockPos start = entity.blockPosition();

        for (int r = 10; r <= 50; r += 5) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int cx = start.getX() + (int) (Math.cos(angle) * r);
                int cz = start.getZ() + (int) (Math.sin(angle) * r);
                BlockPos ground = findGround(level, cx, start.getY(), cz);
                if (ground != null && isSuitable(level, ground)) return ground;
            }
        }
        return null;
    }

    private BlockPos findGround(Level level, int x, int startY, int z) {
        for (int y = startY + 10; y >= Math.max(1, startY - 10); y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isSolid()
                    && !level.getBlockState(p).liquid()
                    && !level.getBlockState(p.above()).isSolid()) {
                return p.above();
            }
        }
        return null;
    }

    /** 5×5 footprint: ground solid + 5 blocks of non-solid clearance above each cell */
    private boolean isSuitable(Level level, BlockPos base) {
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                BlockPos floor = new BlockPos(base.getX() + dx, base.getY() - 1, base.getZ() + dz);
                BlockState fs = level.getBlockState(floor);
                if (!fs.isSolid() || fs.liquid()) return false;
                for (int dy = 1; dy <= 5; dy++) {
                    if (level.getBlockState(floor.above(dy)).isSolid()) return false;
                }
            }
        }
        return true;
    }

    // --- Material helpers ---

    private boolean hasEnoughMaterials() { return availablePlanks() >= PLANKS_NEEDED; }

    private int availablePlanks() {
        int total = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            BlockState bs = bi.getBlock().defaultBlockState();
            if (bs.is(BlockTags.PLANKS)) total += s.getCount();
            if (bs.is(BlockTags.LOGS))   total += s.getCount() * 4;
        }
        return total;
    }

    private void convertLogsToPlanks() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            if (!bi.getBlock().defaultBlockState().is(BlockTags.LOGS)) continue;
            int count = s.getCount();
            inv.setItem(i, ItemStack.EMPTY);
            give(new ItemStack(Items.OAK_PLANKS, count * 4));
        }
    }

    private boolean removePlank() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            if (!bi.getBlock().defaultBlockState().is(BlockTags.PLANKS)) continue;
            s.shrink(1);
            return true;
        }
        return false;
    }

    private void give(ItemStack stack) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) { inv.setItem(i, stack.copyAndClear()); return; }
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                if (room > 0) { int add = Math.min(room, stack.getCount()); slot.grow(add); stack.shrink(add); }
            }
        }
    }
}
