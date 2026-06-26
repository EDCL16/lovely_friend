package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

public class HuntLivestockGoal extends Goal {

    private static final double SEARCH_RADIUS = 20.0;
    private static final int CHECK_INTERVAL = 80;

    private final FriendEntity entity;
    private net.minecraft.world.entity.LivingEntity targetAnimal;
    private int cooldown;

    public HuntLivestockGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        if (!entity.isHungry()) return false;

        cooldown = CHECK_INTERVAL;
        targetAnimal = findLivestock();
        return targetAnimal != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetAnimal == null || !targetAnimal.isAlive()) return false;
        if (entity.getTarget() == targetAnimal && entity.getTarget().isDeadOrDying()) return false;
        return true;
    }

    @Override
    public void start() {
        if (targetAnimal != null) {
            entity.setTarget(targetAnimal);
        }
    }

    @Override
    public void stop() {
        targetAnimal = null;
    }

    private net.minecraft.world.entity.LivingEntity findLivestock() {
        net.minecraft.world.phys.AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);

        List<net.minecraft.world.entity.animal.Animal> animals = entity.level().getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, searchBox,
                animal -> !animal.isDeadOrDying() && isLivestock(animal.getType())
        );

        if (animals.isEmpty()) return null;

        animals.sort(java.util.Comparator.comparingDouble(entity::distanceToSqr));
        return animals.get(0);
    }

    private boolean isLivestock(EntityType<?> type) {
        String id = type.toString();
        return id.contains("cow") ||
               id.contains("pig") ||
               id.contains("sheep") ||
               id.contains("chicken");
    }
}