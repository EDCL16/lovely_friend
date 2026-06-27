package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.tags.ItemTags;

import java.util.EnumSet;

public class SmeltOreGoal extends Goal {

    private static final int SEARCH_RADIUS = 24;
    private static final int COOLDOWN = 400;
    private static final int SMELT_WAIT = 200; // ticks to wait for smelting

    private final FriendEntity entity;
    private BlockPos furnacePos;
    private int cooldown;
    private int waitTimer;
    private boolean waiting;

    public SmeltOreGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage().ordinal() < GameStage.IRON.ordinal()) return false;
        if (entity.getTarget() != null) return false;
        if (!hasSmeltableOre()) return false;
        if (entity.getRandom().nextFloat() > 0.05f) return false;
        furnacePos = findFurnace();
        if (furnacePos == null) furnacePos = tryPlaceFurnace();
        return furnacePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (furnacePos == null) return false;
        if (!hasSmeltableOre() && !waiting) return false;
        return entity.getTarget() == null;
    }

    @Override
    public void start() {
        waiting = false;
        waitTimer = 0;
        entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        furnacePos = null;
        waiting = false;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (furnacePos == null) return;
        if (entity.distanceToSqr(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5) > 9.0) {
            entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        if (!waiting) {
            loadFurnace();
            waiting = true;
            waitTimer = 0;
        } else {
            if (++waitTimer >= SMELT_WAIT) {
                collectFurnaceOutput();
                waiting = false;
            }
        }
    }

    private boolean hasSmeltableOre() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER)) return true;
        }
        return false;
    }

    private BlockPos findFurnace() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -4; dy <= 4; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (entity.level().getBlockState(pos).is(Blocks.FURNACE)) return pos;
                }
        return null;
    }

    private BlockPos tryPlaceFurnace() {
        var inv = entity.getInventory();
        int furnaceSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Blocks.FURNACE.asItem())) { furnaceSlot = i; break; }
        }
        if (furnaceSlot < 0) return null;

        BlockPos placePos = entity.blockPosition().relative(entity.getDirection());
        if (!entity.level().getBlockState(placePos).isAir()) return null;
        if (!(entity.level() instanceof ServerLevel sl)) return null;

        sl.setBlock(placePos, Blocks.FURNACE.defaultBlockState(), 3);
        inv.removeItem(furnaceSlot, 1);
        return placePos;
    }

    private void loadFurnace() {
        if (!(entity.level() instanceof ServerLevel)) return;
        BlockEntity be = entity.level().getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        var inv = entity.getInventory();
        // Slot 0 = input ore, slot 1 = fuel, slot 2 = output
        // Load ore into slot 0
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER)) {
                if (furnace.getItem(0).isEmpty()) {
                    furnace.setItem(0, s.split(s.getCount()));
                    inv.setItem(i, ItemStack.EMPTY);
                }
                break;
            }
        }
        // Load fuel into slot 1 (coal preferred, planks as fallback)
        if (furnace.getItem(1).isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.COAL_BLOCK)) {
                    furnace.setItem(1, s.split(Math.min(8, s.getCount())));
                    break;
                }
                if (s.is(ItemTags.PLANKS)) {
                    furnace.setItem(1, s.split(Math.min(8, s.getCount())));
                    break;
                }
            }
        }
    }

    private void collectFurnaceOutput() {
        BlockEntity be = entity.level().getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack output = furnace.getItem(2);
        if (!output.isEmpty()) {
            var inv = entity.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);
                if (slot.isEmpty()) {
                    inv.setItem(i, output.copy());
                    furnace.setItem(2, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }
}
