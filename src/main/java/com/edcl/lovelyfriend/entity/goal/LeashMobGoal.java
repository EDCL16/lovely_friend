package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class LeashMobGoal extends Goal {

    private static final double SEARCH_RADIUS = 12.0;
    private static final double USE_RANGE = 3.0;

    private final FriendEntity entity;
    private Mob targetMob;
    private int cooldown;

    public LeashMobGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 80; // Check every 4 seconds

        if (!hasLead()) return false;

        targetMob = findLeashableMob();
        return targetMob != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetMob == null) return false;
        if (!targetMob.isAlive()) return false;
        if (targetMob.isLeashed()) return false;

        double dist = entity.distanceToSqr(targetMob);
        if (dist > SEARCH_RADIUS * SEARCH_RADIUS) return false;

        return true;
    }

    @Override
    public void start() {
        if (targetMob != null) {
            entity.getNavigation().moveTo(targetMob, 1.0);
        }
    }

    @Override
    public void stop() {
        targetMob = null;
        entity.getNavigation().stop();
        cooldown = 200;
    }

    @Override
    public void tick() {
        if (targetMob == null) return;

        double dist = entity.distanceToSqr(targetMob);
        entity.getLookControl().setLookAt(targetMob);

        if (dist > USE_RANGE * USE_RANGE) {
            entity.getNavigation().moveTo(targetMob, 1.0);
        } else {
            entity.getNavigation().stop();

            // Apply leash
            if (!targetMob.isLeashed()) {
                // Leash the mob to this entity
                targetMob.setLeashedTo(entity, true);
            }

            targetMob = null;
            cooldown = 300;
        }
    }

    private boolean hasLead() {
        if (entity.getMainHandItem().is(Items.LEAD)) return true;
        if (entity.getOffhandItem().is(Items.LEAD)) return true;

        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.is(Items.LEAD)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Mob findLeashableMob() {
        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<Mob> mobs = entity.level().getEntitiesOfClass(Mob.class, searchBox);

        Mob closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Mob mob : mobs) {
            if (mob == entity) continue;
            if (mob.isAlive() && !mob.isLeashed()) {
                // Only leash passive/neutral mobs (not monsters)
                if (mob instanceof Animal || isNeutralMob(mob)) {
                    double dist = entity.distanceToSqr(mob);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = mob;
                    }
                }
            }
        }

        return closest;
    }

    private boolean isNeutralMob(Mob mob) {
        // Leash neutral mobs too (like llamas, polar bears, etc.)
        return !(mob instanceof net.minecraft.world.entity.monster.Enemy);
    }
}