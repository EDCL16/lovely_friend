package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class EatFoodGoal extends Goal {

    private static final int EAT_DURATION = 32; // ticks to eat (same as player)

    private final FriendEntity entity;
    private int eatTimer;
    private int cooldown;
    private ItemStack savedHand = ItemStack.EMPTY;

    public EatFoodGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (!entity.isHungry()) return false;
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        return entity.hasFoodInInventory();
    }

    @Override
    public boolean canContinueToUse() {
        return eatTimer < EAT_DURATION;
    }

    @Override
    public void start() {
        eatTimer = 0;
        ItemStack food = entity.findFoodInInventory();
        if (food.isEmpty()) return;

        // Hold food visually in main hand while eating
        savedHand = entity.getMainHandItem().copy();
        entity.setItemSlot(EquipmentSlot.MAINHAND, food.copyWithCount(1));
        entity.startUsingItem(InteractionHand.MAIN_HAND);
    }

    @Override
    public void stop() {
        entity.stopUsingItem();

        if (eatTimer >= EAT_DURATION) {
            // Eat completed: consume from inventory and apply nutrition
            ItemStack food = entity.findFoodInInventory();
            if (!food.isEmpty()) entity.eatFood(food);
        }

        // Restore previous hand item
        entity.setItemSlot(EquipmentSlot.MAINHAND, savedHand);
        savedHand = ItemStack.EMPTY;
        cooldown = 30;
    }

    @Override
    public void tick() {
        eatTimer++;
    }
}
