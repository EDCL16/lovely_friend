package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class CollectItemOnGroundGoal extends Goal {

    private static final double SEARCH_RADIUS = 12.0;
    private static final double PICKUP_RANGE = 1.5;

    private final FriendEntity entity;
    private ItemEntity targetItem;

    public CollectItemOnGroundGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Moderate: pickup when idle and item is valuable
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.getNavigation().isInProgress()) return false;
        if (entity.getRandom().nextFloat() > 0.15f) return false; // 15% chance

        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<ItemEntity> items = entity.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> !item.hasPickUpDelay());
        if (items.isEmpty()) return false;
        
        // Prioritize closer items
        targetItem = items.stream()
                .min(Comparator.comparingDouble(entity::distanceToSqr))
                .orElse(null);
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public void tick() {
        if (targetItem == null) return;
        entity.getNavigation().moveTo(targetItem, 1.0);
        if (entity.distanceToSqr(targetItem) < PICKUP_RANGE * PICKUP_RANGE) {
            entity.collectItem(targetItem);
            targetItem = null;
        }
    }

    @Override
    public void stop() {
        targetItem = null;
    }
}
