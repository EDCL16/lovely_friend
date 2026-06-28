package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class ActivateEndPortalGoal extends Goal {

    private static final int SEARCH_RADIUS = 48;
    private static final int COOLDOWN = 600;
    private static final double REACH_DIST_SQ = 9.0;

    private final FriendEntity entity;
    private BlockPos targetFrame;
    private int cooldown;

    public ActivateEndPortalGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.STRONGHOLD) return false;
        if (entity.getTarget() != null) return false;
        if (!hasEye()) return false;
        targetFrame = findEmptyFrame();
        return targetFrame != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetFrame != null
                && entity.getGameStage() == GameStage.STRONGHOLD
                && entity.getTarget() == null
                && hasEye();
    }

    @Override
    public void start() {
        entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        targetFrame = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (targetFrame == null) return;

        BlockState state = entity.level().getBlockState(targetFrame);
        if (!state.is(Blocks.END_PORTAL_FRAME) || state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            // Frame gone or already filled — find next
            targetFrame = findEmptyFrame();
            if (targetFrame == null) { entity.setEndPortalActivated(true); return; }
            entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 1.0);
            return;
        }

        double distSq = entity.distanceToSqr(targetFrame.getX() + 0.5, targetFrame.getY() + 0.5, targetFrame.getZ() + 0.5);
        if (distSq > REACH_DIST_SQ) {
            if (!entity.getNavigation().isInProgress()) {
                entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 1.0);
            }
            return;
        }

        if (!(entity.level() instanceof ServerLevel sl)) return;
        if (!removeOneEye()) return;

        sl.setBlock(targetFrame, state.setValue(EndPortalFrameBlock.HAS_EYE, true), 3);

        targetFrame = findEmptyFrame();
        if (targetFrame == null) {
            entity.setEndPortalActivated(true);
        } else {
            entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 1.0);
        }
    }

    private BlockPos findEmptyFrame() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(pos);
                    if (state.is(Blocks.END_PORTAL_FRAME) && !state.getValue(EndPortalFrameBlock.HAS_EYE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasEye() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.ENDER_EYE)) return true;
        }
        return false;
    }

    private boolean removeOneEye() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ENDER_EYE)) { s.shrink(1); return true; }
        }
        return false;
    }
}
