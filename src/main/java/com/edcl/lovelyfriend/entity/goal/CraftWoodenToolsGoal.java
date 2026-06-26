package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.function.Predicate;

public class CraftWoodenToolsGoal extends Goal {

    private final FriendEntity entity;
    private int cooldown;

    public CraftWoodenToolsGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 100;
        if (entity.level().isClientSide()) return false;
        return hasWood() && needsTools();
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        logsToplanks();
        planksToSticks();
        craftTools();
    }

    // --- conversion steps ---

    private void logsToplanks() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (s.isEmpty() || !isLog(s)) continue;
            int count = s.getCount();
            entity.getInventory().setItem(i, ItemStack.EMPTY);
            give(new ItemStack(Items.OAK_PLANKS, count * 4));
        }
    }

    private void planksToSticks() {
        int planks = count(this::isPlank);
        int pairs  = planks / 2;
        if (pairs == 0) return;
        remove(this::isPlank, pairs * 2);
        give(new ItemStack(Items.STICK, pairs * 4));
    }

    private void craftTools() {
        // wooden pickaxe  3 planks + 2 sticks
        make(Items.WOODEN_PICKAXE, ItemTags.PICKAXES, 3, 2);
        // wooden axe      3 planks + 2 sticks
        make(Items.WOODEN_AXE,     ItemTags.AXES,     3, 2);
        // wooden sword    2 planks + 1 stick
        make(Items.WOODEN_SWORD,   ItemTags.SWORDS,   2, 1);
    }

    private void make(net.minecraft.world.item.Item result,
                      net.minecraft.tags.TagKey<net.minecraft.world.item.Item> haveTag,
                      int planksNeeded, int sticksNeeded) {
        if (count(s -> s.is(haveTag)) > 0) return; // already have one
        if (entity.getMainHandItem().is(haveTag)) return;
        if (count(this::isPlank) < planksNeeded) return;
        if (count(s -> s.getItem() == Items.STICK) < sticksNeeded) return;

        remove(this::isPlank, planksNeeded);
        remove(s -> s.getItem() == Items.STICK, sticksNeeded);
        give(new ItemStack(result, 1));
    }

    // --- helpers ---

    private boolean hasWood() {
        return count(this::isLog) > 0 || count(this::isPlank) >= 3;
    }

    private boolean needsTools() {
        boolean pick = count(s -> s.is(ItemTags.PICKAXES)) > 0
                    || entity.getMainHandItem().is(ItemTags.PICKAXES);
        boolean axe  = count(s -> s.is(ItemTags.AXES))     > 0
                    || entity.getMainHandItem().is(ItemTags.AXES);
        boolean sword = count(s -> s.is(ItemTags.SWORDS))  > 0
                    || entity.getMainHandItem().is(ItemTags.SWORDS);
        return !pick || !axe || !sword;
    }

    private boolean isLog(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi
                && bi.getBlock().defaultBlockState().is(BlockTags.LOGS);
    }

    private boolean isPlank(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi
                && bi.getBlock().defaultBlockState().is(BlockTags.PLANKS);
    }

    private int count(Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (p.test(s)) n += s.getCount();
        }
        return n;
    }

    private void remove(Predicate<ItemStack> p, int amount) {
        for (int i = 0; i < entity.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (!p.test(s)) continue;
            int take = Math.min(s.getCount(), amount);
            s.shrink(take);
            amount -= take;
        }
    }

    private void give(ItemStack stack) {
        for (int i = 0; i < entity.getInventory().getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = entity.getInventory().getItem(i);
            if (slot.isEmpty()) {
                entity.getInventory().setItem(i, stack.copyAndClear());
                return;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int room = slot.getMaxStackSize() - slot.getCount();
                if (room > 0) {
                    int add = Math.min(room, stack.getCount());
                    slot.grow(add);
                    stack.shrink(add);
                }
            }
        }
    }
}
