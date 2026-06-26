package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class EquipBestToolGoal extends Goal {

    private final FriendEntity entity;
    private int cooldown;

    public EquipBestToolGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 100; // Check every 5 seconds

        // Only equip if holding nothing or something worse
        ItemStack hand = entity.getMainHandItem();
        if (hand.isEmpty()) return true;

        // If we have a pickaxe and something else, we might want to switch
        // But let the other goals handle tool switching
        return false;
    }

    @Override
    public void start() {
        equipBestFromInventory();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private void equipBestFromInventory() {
        ItemStack hand = entity.getMainHandItem();

        // If hand is empty, find any weapon/tool
        if (hand.isEmpty()) {
            for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
                ItemStack stack = entity.getInventory().getItem(i);
                if (!stack.isEmpty() && (entity.isWeapon(stack) || entity.isPickaxe(stack))) {
                    entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack.copy());
                    entity.getInventory().setItem(i, ItemStack.EMPTY);
                    return;
                }
            }
        }
    }
}