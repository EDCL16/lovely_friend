package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class RideFriendGoal extends Goal {

    private static final double SEARCH_RADIUS = 15.0;
    private static final double RIDE_RANGE = 5.0;
    private static final int BOREDOM_THRESHOLD = 100;
    private static final int RIDE_DURATION = Integer.MAX_VALUE;
    private static final int COOLDOWN_AFTER_RIDE = 600;
    private static final int MAX_STACK = 5; // max chain height

    private final FriendEntity entity;
    private FriendEntity targetFriend;
    private int idleTimer;
    private int rideTimer;
    private int cooldown;
    private boolean wasRiding;

    public RideFriendGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.isPassenger()) return false; // already riding
        if (entity.getTarget() != null) return false;

        if (entity.getNavigation().isInProgress()) {
            idleTimer = 0;
            return false;
        }

        if (++idleTimer < BOREDOM_THRESHOLD) return false;

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
        if (entity.getTarget() != null) {
            if (entity.isPassenger()) entity.stopRiding();
            return false;
        }
        if (entity.isPassenger()) {
            return ++rideTimer < RIDE_DURATION;
        }
        return !wasRiding;
    }

    @Override
    public void start() {
        if (targetFriend == null) return;
        rideTimer = 0;
        if (entity.distanceToSqr(targetFriend) < RIDE_RANGE * RIDE_RANGE) {
            wasRiding = entity.startRiding(targetFriend, true, true);
        } else {
            entity.getNavigation().moveTo(targetFriend, 1.0);
            wasRiding = false;
        }
    }

    @Override
    public void stop() {
        if (entity.isPassenger()) entity.stopRiding();
        targetFriend = null;
        entity.getNavigation().stop();
        idleTimer = 0;
        rideTimer = 0;
        wasRiding = false;
        cooldown = COOLDOWN_AFTER_RIDE;
    }

    @Override
    public void tick() {
        if (targetFriend == null || wasRiding) return;

        entity.getLookControl().setLookAt(targetFriend);
        if (entity.distanceToSqr(targetFriend) < RIDE_RANGE * RIDE_RANGE) {
            entity.getNavigation().stop();
            if (targetFriend.isVehicle()) {
                // Target was claimed by someone else while we were walking — abort
                targetFriend = null;
                return;
            }
            boolean mounted = entity.startRiding(targetFriend, true, true);
            if (mounted) {
                wasRiding = true;
                rideTimer = 0;
            }
        } else {
            entity.getNavigation().moveTo(targetFriend, 1.0);
        }
    }

    private FriendEntity findRideableFriend() {
        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<FriendEntity> friends = entity.level().getEntitiesOfClass(FriendEntity.class, searchBox);

        FriendEntity best = null;
        double bestDist = Double.MAX_VALUE;
        int bestHeight = -1;

        for (FriendEntity friend : friends) {
            if (friend == entity) continue;
            if (!friend.isAlive()) continue;
            if (friend.getTarget() != null) continue;
            if (friend.isInWater()) continue;
            if (friend.isVehicle()) continue; // already has a rider — no doubling up
            if (isAbove(entity, friend)) continue; // prevent circular chain
            int h = stackHeight(friend);
            if (h >= MAX_STACK) continue;

            double dist = entity.distanceToSqr(friend);
            // Prefer taller stacks (build upward); break ties by distance
            if (h > bestHeight || (h == bestHeight && dist < bestDist)) {
                bestHeight = h;
                bestDist = dist;
                best = friend;
            }
        }
        return best;
    }

    /** True if candidate is already a (transitive) passenger of anchor. */
    private boolean isAbove(Entity anchor, Entity candidate) {
        Entity v = anchor.getVehicle();
        while (v != null) {
            if (v == candidate) return true;
            v = v.getVehicle();
        }
        return false;
    }

    /** Count how many entities are already stacked below + at the target. */
    private int stackHeight(Entity e) {
        int h = 1;
        Entity v = e.getVehicle();
        while (v != null) { h++; v = v.getVehicle(); }
        return h;
    }
}
