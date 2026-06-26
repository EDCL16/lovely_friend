package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DiggingEscapeGoal extends Goal {

    private static final int    STUCK_TICKS   = 30;   // ticks of no movement
    private static final double STUCK_DIST_SQ = 0.05; // must move < 0.22 blocks/tick
    private static final int    DIG_TICKS     = 30;

    private final FriendEntity entity;
    private Vec3    lastPos;
    private int     stuckTicks;
    private BlockPos digTarget;
    private int     digTimer;
    private int     cooldown;
    private boolean usedTool;

    public DiggingEscapeGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (!entity.hasPickaxe() && !entity.hasAxe()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isInWater() || entity.isPassenger()) return false;

        Vec3 pos = entity.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) < STUCK_DIST_SQ) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = pos;

        // Require navigation active OR stuckTicks already high enough to try anyway
        boolean navActive = entity.getNavigation().isInProgress();
        if (!navActive && stuckTicks < STUCK_TICKS * 2) {
            // If nav stopped on its own, give it more time before assuming stuck
            return false;
        }
        if (stuckTicks < STUCK_TICKS) return false;

        digTarget = findBlockingBlock();
        return digTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        return digTarget != null && (entity.hasPickaxe() || entity.hasAxe());
    }

    @Override
    public void start() {
        stuckTicks = 0;
        digTimer = 0;
        usedTool = entity.equipPickaxe();
        if (!usedTool) usedTool = entity.equipAxe();
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedTool) entity.restoreWeapon();
        digTarget = null;
        cooldown = 80;
    }

    @Override
    public void tick() {
        if (digTarget == null) return;

        entity.getLookControl().setLookAt(
                digTarget.getX() + 0.5, digTarget.getY() + 0.5, digTarget.getZ() + 0.5);

        digTimer++;
        if (entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), digTarget, Math.min(digTimer * 10 / DIG_TICKS, 9));
        }

        if (digTimer >= DIG_TICKS) {
            entity.level().destroyBlock(digTarget, true, entity);
            clearProgress();
            digTarget = null;
        }
    }

    private void clearProgress() {
        if (digTarget != null && entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), digTarget, -1);
        }
    }

    private BlockPos findBlockingBlock() {
        Level level = entity.level();
        BlockPos entityPos = entity.blockPosition();
        Direction preferred = getPathDirection();

        for (int pass = 0; pass < 2; pass++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (pass == 0 && dir != preferred) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos candidate = entityPos.relative(dir).above(dy);
                    BlockState state = level.getBlockState(candidate);
                    if (state.isSolid() && !state.liquid()
                            && state.getDestroySpeed(level, candidate) >= 0
                            && state.getDestroySpeed(level, candidate) < 50) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Direction getPathDirection() {
        Path path = entity.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Node next = path.getNextNode();
            if (next != null) {
                BlockPos pos = entity.blockPosition();
                int dx = next.x - pos.getX();
                int dz = next.z - pos.getZ();
                if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
                return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }
        // Fall back to where the entity is facing
        float yaw = entity.getYRot();
        if (yaw < -135 || yaw >= 135) return Direction.NORTH;
        if (yaw < -45)  return Direction.WEST;
        if (yaw < 45)   return Direction.SOUTH;
        return Direction.EAST;
    }
}
