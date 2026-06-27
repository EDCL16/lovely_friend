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

    // 計算所需資源
    private static final int PLANKS_PER_PICKAXE = 4; // 3 planks + 2 sticks (=1 plank)
    private static final int PLANKS_PER_AXE     = 4; // 3 planks + 2 sticks
    private static final int PLANKS_PER_SWORD   = 3; // 2 planks + 1 stick (=0.5 plank → 無條件進位到 3)

    public CraftWoodenToolsGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 40; // 每 2 秒檢查一次
        if (entity.level().isClientSide()) return false;
        if (entity.getGameStage() != com.edcl.lovelyfriend.entity.GameStage.WOOD) return false;

        // 不需要任何工具 → 不用合成
        if (!needsAnyTool()) return false;

        // 檢查背包裡的木頭總量是否足夠合成缺少的工具
        int planksFromLogs = count(this::isLog) * 4; // 1 原木 = 4 木材
        int planksTotal    = planksFromLogs + count(this::isPlank);
        int requiredPlanks = calculateRequiredPlanks();

        if (planksTotal >= requiredPlanks) {
            // ✅ 木頭夠了，可以合成
            return true;
        }

        // ❌ 木頭不夠，設短冷卻讓 ChopTreeGoal 有機會執行
        cooldown = 10; // 0.5 秒後再檢查
        return false;
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        // 再次確認木頭夠（可能被其他 Goal 消耗了）
        int planksFromLogs  = count(this::isLog) * 4;
        int planksTotal     = planksFromLogs + count(this::isPlank);
        int requiredPlanks  = calculateRequiredPlanks();

        if (planksTotal < requiredPlanks) {
            // 木頭不夠，不合成，讓 ChopTreeGoal 去砍
            cooldown = 10;
            return;
        }

        // 開始合成流程
        logsToplanks();
        planksToSticks();
        craftTools();

        cooldown = 60; // 合成完休息 3 秒
    }

    // --- 計算需要多少資源 ---

    /**
     * 計算缺少的工具總共需要多少木材
     */
    private int calculateRequiredPlanks() {
        int required = 0;

        if (!hasTool(ItemTags.PICKAXES)) required += PLANKS_PER_PICKAXE;
        if (!hasTool(ItemTags.AXES))     required += PLANKS_PER_AXE;
        if (!hasTool(ItemTags.SWORDS))   required += PLANKS_PER_SWORD;

        return required;
    }

    /**
     * 檢查是否缺少特定類型的工具
     */
    private boolean hasTool(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        if (entity.getMainHandItem().is(tag)) return true;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).is(tag)) return true;
        }
        return false;
    }

    private boolean needsAnyTool() {
        return !hasTool(ItemTags.PICKAXES)
            || !hasTool(ItemTags.AXES)
            || !hasTool(ItemTags.SWORDS);
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
        if (hasTool(haveTag)) return; // already have one
        if (count(this::isPlank) < planksNeeded) return;
        if (count(s -> s.getItem() == Items.STICK) < sticksNeeded) return;

        remove(this::isPlank, planksNeeded);
        remove(s -> s.getItem() == Items.STICK, sticksNeeded);
        give(new ItemStack(result, 1));
    }

    // --- helpers ---

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