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

    // 錯誤工具/空手懲罰（同 DiggingEscapeGoal）
    private static final float WRONG_TOOL_PENALTY    = 3.333f;
    private static final float TOOL_REQUIRED_PENALTY = 5.0f;

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
        if (entity.getY() > 60) return false;
        if (entity.getRandom().nextFloat() > 0.03f) return false; // 3% chance

        // ✅ 不再要求一定要有鎬子！
        // 沒鎬子 → 徒手挖（超慢）→ 過程中自然會去砍木頭做工具

        target = findBestOreTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null) return false;
        // 即使鎬子用壞了也可以繼續（用其他工具或空手）
        return true;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe(); // 有鎬就裝，沒有就空手
        requiredBreakTicks = getRequiredBreakTicks();
        if (target != null) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        entity.setMidBlockBreak(false);
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
        entity.setMidBlockBreak(true);
        entity.setTarget(null); // suppress combat interruption while mid-block

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

    /**
     * 使用真實 Minecraft 挖掘公式計算所需 ticks
     * 同 DiggingEscapeGoal.calculateDigTicks()
     */
    private int getRequiredBreakTicks() {
        if (target == null) return 40;

        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);

        if (hardness < 0) return 999;
        if (hardness == 0) return 2;

        ItemStack tool = entity.getMainHandItem();
        float speed;

        if (tool.isEmpty()) {
            speed = 1.0f;
        } else {
            float toolSpeed = tool.getDestroySpeed(state);
            if (toolSpeed > 1.0f) {
                speed = toolSpeed;
            } else {
                speed = 1.0f;
                if (!tool.isCorrectToolForDrops(state) && state.requiresCorrectToolForDrops()) {
                    speed = 1.0f / TOOL_REQUIRED_PENALTY;
                }
            }
        }

        if (tool.isEmpty() && state.requiresCorrectToolForDrops()) {
            speed = 1.0f / TOOL_REQUIRED_PENALTY;
        }

        float timeInSeconds = hardness * 1.5f / speed;
        int ticks = Math.max(1, (int) Math.ceil(timeInSeconds * 20.0f));
        return Math.min(ticks, 6000);
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