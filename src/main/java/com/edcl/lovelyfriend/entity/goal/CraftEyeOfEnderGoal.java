package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

public class CraftEyeOfEnderGoal extends Goal {

    private static final int COOLDOWN = 200;

    private final FriendEntity entity;
    private int cooldown;

    public CraftEyeOfEnderGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = COOLDOWN;
        if (entity.getGameStage() != GameStage.STRONGHOLD) return false;
        return count(Items.BLAZE_ROD) > 0
            || (count(Items.ENDER_PEARL) > 0 && count(Items.BLAZE_POWDER) > 0);
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        // 1 blaze rod → 2 blaze powder
        int rods = count(Items.BLAZE_ROD);
        if (rods > 0) {
            remove(Items.BLAZE_ROD, rods);
            give(new ItemStack(Items.BLAZE_POWDER, rods * 2));
        }

        // 1 ender pearl + 1 blaze powder → 1 eye of ender
        int pairs = Math.min(count(Items.ENDER_PEARL), count(Items.BLAZE_POWDER));
        if (pairs > 0) {
            remove(Items.ENDER_PEARL, pairs);
            remove(Items.BLAZE_POWDER, pairs);
            give(new ItemStack(Items.ENDER_EYE, pairs));
        }
    }

    private int count(Item item) {
        int n = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private void remove(Item item, int amount) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int take = Math.min(s.getCount(), amount);
            s.shrink(take);
            amount -= take;
        }
    }

    private void give(ItemStack stack) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) { inv.setItem(i, stack.copyAndClear()); return; }
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                if (room > 0) { int add = Math.min(room, stack.getCount()); slot.grow(add); stack.shrink(add); }
            }
        }
    }
}
