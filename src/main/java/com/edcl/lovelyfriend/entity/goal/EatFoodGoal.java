package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class EatFoodGoal extends Goal {

    private final FriendEntity entity;
    private int cooldown;

    public EatFoodGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!entity.isHungry()) return false;
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        return entity.hasFoodInInventory();
    }

    @Override
    public void start() {
        ItemStack food = entity.findFoodInInventory();
        if (!food.isEmpty()) {
            entity.eatFood(food);
            cooldown = 20;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
