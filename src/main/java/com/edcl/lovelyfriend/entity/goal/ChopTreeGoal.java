package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class ChopTreeGoal extends Goal {

    private static final int  SEARCH_RADIUS  = 14;
    private static final int  CHOP_WITH_AXE  = 25;
    private static final int  CHOP_BY_HAND   = 80;
    private static final int  MAX_LOGS       = 20;

    private final FriendEntity entity;
    private BlockPos chopTarget;
    private int chopTimer;
    private int chopDuration;
    private int cooldown;
    private boolean usedAxe;

    public ChopTreeGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getTarget() != null) return false;
        if (countLogs() >= MAX_LOGS) return false;
        chopTarget = findLog();
        return chopTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getTarget() != null) return false;
        if (countLogs() >= MAX_LOGS) return false;
        return chopTarget != null && entity.level().getBlockState(chopTarget).is(BlockTags.LOGS);
    }

    @Override
    public void start() {
        chopTimer = 0;
        usedAxe = entity.equipAxe();
        chopDuration = usedAxe ? CHOP_WITH_AXE : CHOP_BY_HAND;
        navigateTo(chopTarget);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedAxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        chopTarget = null;
        cooldown = 80;
    }

    @Override
    public void tick() {
        if (chopTarget == null) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;

        // Re-check target each tick
        if (!entity.level().getBlockState(chopTarget).is(BlockTags.LOGS)) {
            clearProgress();
            // Try the log above first (strip the trunk upward)
            BlockPos above = chopTarget.above();
            if (entity.level().getBlockState(above).is(BlockTags.LOGS)) {
                chopTarget = above;
                chopTimer = 0;
            } else {
                chopTarget = findLog();
                chopTimer = 0;
            }
            if (chopTarget != null) navigateTo(chopTarget);
            return;
        }

        entity.getLookControl().setLookAt(
                chopTarget.getX() + 0.5, chopTarget.getY() + 0.5, chopTarget.getZ() + 0.5);

        // Move closer if needed
        if (entity.blockPosition().distSqr(chopTarget) > 9) {
            clearProgress();
            navigateTo(chopTarget);
            return;
        }

        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), chopTarget, Math.min(chopTimer * 10 / chopDuration, 9));
        chopTimer++;

        if (chopTimer >= chopDuration) {
            clearProgress();
            entity.level().destroyBlock(chopTarget, true, entity);
            chopTimer = 0;
        }
    }

    private void navigateTo(BlockPos pos) {
        if (pos == null) return;
        entity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    private void clearProgress() {
        if (chopTarget != null && entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), chopTarget, -1);
        }
    }

    private BlockPos findLog() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 8; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(BlockTags.LOGS)) continue;
                    // Prefer the lowest log of each trunk
                    if (entity.level().getBlockState(pos.below()).is(BlockTags.LOGS)) continue;
                    double d = origin.distSqr(pos);
                    if (d < bestDist) { bestDist = d; best = pos; }
                }
            }
        }
        return best;
    }

    private int countLogs() {
        int n = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            var s = entity.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem bi
                    && bi.getBlock().defaultBlockState().is(BlockTags.LOGS)) {
                n += s.getCount();
            }
        }
        return n;
    }
}
