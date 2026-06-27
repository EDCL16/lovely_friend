package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FindNetherFortressGoal extends Goal {

    private static final int COOLDOWN_MIN = 3600;
    private static final double ARRIVAL_DIST_SQ = 100.0;
    private static final int MAX_STUCK_TIME = 800;

    private static final TagKey<Structure> NETHER_FORTRESS =
            TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("lovelyfriend", "nether_fortress"));

    private final FriendEntity entity;
    private BlockPos targetPos;
    private int cooldown;
    private int stuckTimer;
    private Vec3 lastPos;

    public FindNetherFortressGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
        cooldown = COOLDOWN_MIN;
    }

    @Override
    public boolean canUse() {
        if (entity.getGameStage() != GameStage.NETHER) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isLeashed()) return false;
        if (!(entity.level() instanceof ServerLevel sl)) return false;
        if (targetPos != null) {
            if (entity.blockPosition().distSqr(targetPos) < ARRIVAL_DIST_SQ) {
                targetPos = null;
                cooldown = COOLDOWN_MIN;
                return false;
            }
            return true;
        }
        if (cooldown-- > 0) return false;
        targetPos = sl.findNearestMapStructure(NETHER_FORTRESS, entity.blockPosition(), 64, false);
        if (targetPos == null) {
            cooldown = 200;
            return false;
        }
        stuckTimer = 0;
        lastPos = entity.position();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null
                && entity.getGameStage() == GameStage.NETHER
                && entity.getTarget() == null
                && !entity.isLeashed();
    }

    @Override
    public void start() {
        if (targetPos != null) navigate();
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;
        Vec3 cur = entity.position();
        if (cur.distanceToSqr(lastPos) < 0.05) {
            if (++stuckTimer > MAX_STUCK_TIME) {
                targetPos = null;
                cooldown = COOLDOWN_MIN;
                return;
            }
        } else {
            stuckTimer = 0;
        }
        lastPos = cur;
        if (!entity.getNavigation().isInProgress()) navigate();
    }

    private void navigate() {
        entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
    }
}
