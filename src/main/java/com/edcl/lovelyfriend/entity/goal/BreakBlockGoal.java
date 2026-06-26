package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class BreakBlockGoal extends Goal {

    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_RANGE_Y = 5;
    private static final int BASE_BREAK_TICKS = 40;
    private static final float TOOL_SPEED_MULTIPLIER = 1.5f;

    private final FriendEntity entity;
    private BlockPos target;
    private int breakTick;
    private int requiredBreakTicks;
    private boolean usedPickaxe;
    private int cooldown;

    public BreakBlockGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.isVehicle()) return false;
        if (!entity.hasPickaxe()) return false;
        if (entity.getY() > 60) return false;
        if (entity.getRandom().nextFloat() > 0.03f) return false; // 3% chance
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null) return false;
        if (!entity.hasPickaxe()) return false;
        return true;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe();
        requiredBreakTicks = getRequiredBreakTicks();
        if (target != null) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedPickaxe) {
            entity.restoreWeapon();
        }
        target = null;
        cooldown = 200;
    }

    @Override
    public void tick() {
        if (target == null) return;

        entity.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        double dist = entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (dist > 4.0) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();

        breakTick++;
        if (entity.level() instanceof ServerLevel sl) {
            int progress = Math.min(breakTick * 10 / requiredBreakTicks, 9);
            sl.destroyBlockProgress(entity.getId(), target, progress);
        }

        if (breakTick >= requiredBreakTicks) {
            entity.level().destroyBlock(target, true, entity);
            clearProgress();
            target = null;
        }
    }

    private void clearProgress() {
        if (target != null && entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), target, -1);
        }
    }

    private int getRequiredBreakTicks() {
        if (target == null) return BASE_BREAK_TICKS;

        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness < 0) return BASE_BREAK_TICKS;

        ItemStack tool = entity.getMainHandItem();
        float speed = 1.0f;

        if (!tool.isEmpty()) {
            speed = tool.getDestroySpeed(state);
            if (speed > 1.0f) {
                speed *= TOOL_SPEED_MULTIPLIER;
            }
        }

        boolean isCorrectTool = !tool.isEmpty() && tool.isCorrectToolForDrops(state);
        if (!isCorrectTool) {
            speed = 1.0f;
        }

        float time = hardness * 1.5f / speed;
        if (time < 0.1f) time = 0.1f;

        int ticks = (int) (time * 20);
        return Math.max(2, Math.min(ticks, 200));
    }

    private BlockPos findBestOreTarget() {
        Level level = entity.level();
        BlockPos origin = entity.blockPosition();

        List<BlockPos> valuableBlocks = new ArrayList<>();

        AABB searchBox = new AABB(
                origin.getX() - SEARCH_RADIUS, origin.getY() - SEARCH_RANGE_Y, origin.getZ() - SEARCH_RADIUS,
                origin.getX() + SEARCH_RADIUS, origin.getY() + SEARCH_RANGE_Y, origin.getZ() + SEARCH_RADIUS
        );

        for (int x = (int) searchBox.minX; x <= (int) searchBox.maxX; x++) {
            for (int y = (int) searchBox.minY; y <= (int) searchBox.maxY; y++) {
                for (int z = (int) searchBox.minZ; z <= (int) searchBox.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();

                    if (isValuableOre(block, state)) {
                        valuableBlocks.add(pos);
                    }
                }
            }
        }

        if (valuableBlocks.isEmpty()) {
            return findStoneToDig(level, origin);
        }

        valuableBlocks.sort(Comparator.comparingDouble(pos ->
                origin.distSqr(pos)));

        return valuableBlocks.isEmpty() ? null : valuableBlocks.get(0);
    }

    private boolean isValuableOre(Block block, BlockState state) {
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return true;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return true;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return true;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return true;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return true;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return true;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return true;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return true;
        if (block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE) return true;
        if (block == Blocks.ANCIENT_DEBRIS) return true;
        return false;
    }

    private BlockPos findStoneToDig(Level level, BlockPos origin) {
        for (int y = -SEARCH_RANGE_Y; y <= SEARCH_RANGE_Y; y++) {
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block == Blocks.STONE || block == Blocks.DEEPSLATE ||
                            block == Blocks.GRANITE || block == Blocks.DIORITE ||
                            block == Blocks.ANDESITE || block == Blocks.TUFF ||
                            block == Blocks.DRIPSTONE_BLOCK || block == Blocks.NETHERRACK ||
                            block == Blocks.BLACKSTONE || block == Blocks.BASALT ||
                            block == Blocks.END_STONE || block == Blocks.GRAVEL ||
                            block == Blocks.DIRT || block == Blocks.COBBLESTONE ||
                            block == Blocks.COBBLED_DEEPSLATE) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}