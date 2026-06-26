package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class ExtinguishFireGoal extends Goal {

    private static final int SEARCH_RADIUS = 16;

    private final FriendEntity entity;
    private BlockPos waterPos;

    public ExtinguishFireGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!entity.isOnFire()) return false;
        if (entity.isInWater()) return false;
        waterPos = findNearestWater();
        return waterPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.isOnFire() && !entity.isInWater() && waterPos != null;
    }

    @Override
    public void start() {
        entity.getNavigation().moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5, 1.4);
    }

    @Override
    public void stop() {
        waterPos = null;
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (waterPos == null) return;

        // Re-path if we've stalled
        if (!entity.getNavigation().isInProgress()) {
            entity.getNavigation().moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5, 1.4);
        }
    }

    private BlockPos findNearestWater() {
        Level level = entity.level();
        BlockPos origin = entity.blockPosition();

        // Search ring by ring so the first hit is always closest
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < r && Math.abs(dz) < r) continue; // perimeter only
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (level.getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }
}
