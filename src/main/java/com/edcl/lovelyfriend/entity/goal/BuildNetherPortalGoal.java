package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class BuildNetherPortalGoal extends Goal {

    private static final int COOLDOWN = 600;
    private static final int MIN_OBSIDIAN = 10;

    private final FriendEntity entity;
    private int cooldown;
    private List<BlockPos> framePlan;
    private BlockPos buildOrigin;
    private Direction facing;
    private int buildIndex;
    private boolean building;
    private boolean lighting;

    public BuildNetherPortalGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.NETHER) return false;
        if (entity.getTarget() != null) return false;
        if (entity.getRandom().nextFloat() > 0.03f) return false;
        return countObsidian() >= MIN_OBSIDIAN && hasFlintAndSteel();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getGameStage() == GameStage.NETHER
                && entity.getTarget() == null
                && (building || lighting);
    }

    @Override
    public void start() {
        facing = entity.getDirection();
        buildOrigin = entity.blockPosition().relative(facing, 2);
        framePlan = buildFramePlan(buildOrigin, facing);
        buildIndex = 0;
        building = true;
        lighting = false;
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        building = false;
        lighting = false;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (!(entity.level() instanceof ServerLevel sl)) return;

        if (building) {
            if (buildIndex >= framePlan.size()) {
                building = false;
                lighting = true;
                return;
            }
            BlockPos pos = framePlan.get(buildIndex);
            if (entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9.0) {
                entity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                return;
            }
            entity.getNavigation().stop();
            net.minecraft.world.level.block.state.BlockState existing = entity.level().getBlockState(pos);
            if (existing.isAir() || existing.canBeReplaced()) {
                sl.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                removeObsidianFromInventory();
                buildIndex++;
            } else if (existing.is(Blocks.OBSIDIAN)) {
                // Already obsidian (maybe placed earlier or by player) — count it and move on
                buildIndex++;
            }
            // else: occupied by something else — navigate around it, retry next tick without advancing
        }

        if (lighting) {
            // Place fire 1 block inside the frame (column 1, Y=1)
            BlockPos insidePos = buildOrigin.relative(facing.getClockWise(), 1).above(1);
            if (entity.distanceToSqr(insidePos.getX() + 0.5, insidePos.getY() + 0.5, insidePos.getZ() + 0.5) > 9.0) {
                entity.getNavigation().moveTo(insidePos.getX() + 0.5, insidePos.getY(), insidePos.getZ() + 0.5, 1.0);
                return;
            }
            entity.getNavigation().stop();
            var inv = entity.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(Items.FLINT_AND_STEEL)) {
                    sl.setBlock(insidePos, Blocks.FIRE.defaultBlockState(), 3);
                    entity.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
                    inv.getItem(i).shrink(1);
                    lighting = false;
                    break;
                }
            }
        }
    }

    /**
     * Builds a 4-wide × 6-tall obsidian frame (2×4 inner portal).
     * Bottom row:  Y=0, columns 0-3
     * Left side:   Y=1-4, column 0
     * Right side:  Y=1-4, column 3
     * Top row:     Y=5, columns 0-3
     */
    private List<BlockPos> buildFramePlan(BlockPos origin, Direction dir) {
        Direction right = dir.getClockWise();
        List<BlockPos> plan = new ArrayList<>();
        // Bottom: 4 blocks at Y=0
        for (int i = 0; i < 4; i++) plan.add(origin.relative(right, i));
        // Left side: Y=1..4
        for (int y = 1; y <= 4; y++) plan.add(origin.above(y));
        // Right side: Y=1..4 (column 3)
        for (int y = 1; y <= 4; y++) plan.add(origin.relative(right, 3).above(y));
        // Top: 4 blocks at Y=5
        for (int i = 0; i < 4; i++) plan.add(origin.above(5).relative(right, i));
        return plan;
    }

    private int countObsidian() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(Items.OBSIDIAN)) count += s.getCount();
        }
        return count;
    }

    private boolean hasFlintAndSteel() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.FLINT_AND_STEEL)) return true;
        }
        return false;
    }

    private void removeObsidianFromInventory() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(Items.OBSIDIAN)) {
                s.shrink(1);
                return;
            }
        }
    }
}
