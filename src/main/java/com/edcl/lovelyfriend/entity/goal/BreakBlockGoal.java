package com.edcl.lovelyfriend.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class BreakBlockGoal extends Goal {

    private final PathfinderMob mob;
    private BlockPos target;
    private int breakTick;
    private static final int BREAK_TICKS = 40;

    public BreakBlockGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!mob.getNavigation().isInProgress()) return false;
        target = findBlockAhead();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        BlockState s = mob.level().getBlockState(target);
        float speed = s.getDestroySpeed(mob.level(), target);
        return !s.isAir() && speed >= 0;
    }

    @Override
    public void start() {
        breakTick = 0;
    }

    @Override
    public void stop() {
        clearProgress();
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        breakTick++;
        if (mob.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(mob.getId(), target, breakTick * 10 / BREAK_TICKS);
        }
        if (breakTick >= BREAK_TICKS) {
            mob.level().destroyBlock(target, true, mob);
            clearProgress();
            target = null;
        }
    }

    private void clearProgress() {
        if (target != null && mob.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(mob.getId(), target, -1);
        }
    }

    private BlockPos findBlockAhead() {
        double yaw = Math.toRadians(mob.getYRot());
        int bx = (int) Math.floor(mob.getX() - Math.sin(yaw) * 0.8);
        int by = mob.blockPosition().getY();
        int bz = (int) Math.floor(mob.getZ() + Math.cos(yaw) * 0.8);
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos pos = new BlockPos(bx, by + dy, bz);
            BlockState s = mob.level().getBlockState(pos);
            float speed = s.getDestroySpeed(mob.level(), pos);
            // ponytail: skip indestructible (speed < 0) and very hard blocks (obsidian etc.)
            if (!s.isAir() && speed >= 0 && speed <= 10) return pos;
        }
        return null;
    }
}
