package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {

    private final FriendEntity entity;
    private final double speed;
    private final double maxFollowRange;
    private final double minFollowRange;
    private Player target;

    public FollowPlayerGoal(FriendEntity entity, double speed, double maxRange, double minRange) {
        this.entity = entity;
        this.speed = speed;
        this.maxFollowRange = maxRange;
        this.minFollowRange = minRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.getRandom().nextFloat() > 0.08f) return false;
        target = entity.level().getNearestPlayer(entity, maxFollowRange);
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        double dist = entity.distanceToSqr(target);
        return dist < maxFollowRange * maxFollowRange;
    }

    @Override
    public void tick() {
        if (target == null) return;
        entity.getLookControl().setLookAt(target);
        double dist = entity.distanceToSqr(target);
        if (dist > minFollowRange * minFollowRange) {
            entity.getNavigation().moveTo(target, speed);
        } else {
            entity.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        target = null;
        entity.getNavigation().stop();
    }
}
