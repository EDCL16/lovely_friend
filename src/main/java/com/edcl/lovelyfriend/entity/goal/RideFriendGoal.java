package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class RideFriendGoal extends Goal {

    private static final double SEARCH_RADIUS = 4.0;
    private static final double RIDE_RANGE = 2.5;
    private static final int BOREDOM_THRESHOLD = 600; // 30 seconds idle
    private static final int RIDE_DURATION = 200; // Ride for ~10 seconds
    private static final int COOLDOWN_AFTER_RIDE = 4800; // 240 second (4 minute) cooldown

    private final FriendEntity entity;
    private FriendEntity targetFriend;
    private int idleTimer;
    private int rideTimer;
    private int cooldown;
    private boolean wasRiding;

    public RideFriendGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.isVehicle()) return false;
        if (entity.isPassenger()) return false;
        if (entity.getTarget() != null) return false;

        if (entity.getNavigation().isInProgress()) {
            idleTimer = 0;
            return false;
        }

        idleTimer++;
        if (idleTimer < BOREDOM_THRESHOLD) return false;

        targetFriend = findRideableFriend();
        if (targetFriend == null) {
            idleTimer = 0;
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetFriend == null || !targetFriend.isAlive()) return false;
        if (!entity.isPassenger()) return false;
        if (entity.getTarget() != null) {
            entity.stopRiding();
            return false;
        }

        rideTimer++;
        return rideTimer < RIDE_DURATION;
    }

    @Override
    public void start() {
        if (targetFriend == null) return;

        double dist = entity.distanceToSqr(targetFriend);
        if (dist < RIDE_RANGE * RIDE_RANGE) {
            entity.startRiding(targetFriend, true, true);
            wasRiding = true;
            rideTimer = 0;
        } else {
            entity.getNavigation().moveTo(targetFriend, 1.0);
            wasRiding = false;
        }
    }

    @Override
    public void stop() {
        if (entity.isPassenger()) {
            entity.stopRiding();
        }

        targetFriend = null;
        entity.getNavigation().stop();
        idleTimer = 0;
        rideTimer = 0;
        wasRiding = false;
        cooldown = COOLDOWN_AFTER_RIDE;
    }

    @Override
    public void tick() {
        if (targetFriend == null) return;

        if (!wasRiding) {
            double dist = entity.distanceToSqr(targetFriend);
            entity.getLookControl().setLookAt(targetFriend);

            if (dist < RIDE_RANGE * RIDE_RANGE) {
                entity.getNavigation().stop();
                entity.startRiding(targetFriend, true, true);
                wasRiding = true;
                rideTimer = 0;
            } else {
                entity.getNavigation().moveTo(targetFriend, 1.0);
            }
        }
    }

    private FriendEntity findRideableFriend() {
        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<FriendEntity> friends = entity.level().getEntitiesOfClass(FriendEntity.class, searchBox);

        FriendEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (FriendEntity friend : friends) {
            if (friend == entity) continue;
            
            if (friend.isAlive() && !friend.isPassenger() && !friend.isVehicle()) {
                if (friend.getTarget() != null) continue;
                if (friend.getRandom().nextFloat() < 0.7f) continue; // 70% skip rate

                double dist = entity.distanceToSqr(friend);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = friend;
                }
            }
        }

        return closest;
    }
}