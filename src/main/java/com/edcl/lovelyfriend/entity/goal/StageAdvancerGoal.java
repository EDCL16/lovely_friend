package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class StageAdvancerGoal extends Goal {

    private static final int CHECK_INTERVAL = 1200; // 1 minute

    private final FriendEntity entity;
    private int timer = CHECK_INTERVAL;

    public StageAdvancerGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (--timer > 0) return false;
        timer = CHECK_INTERVAL;
        return shouldAdvance();
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        entity.advanceStage();
    }

    private boolean shouldAdvance() {
        return switch (entity.getGameStage()) {
            case WOOD         -> hasStonePickaxe();
            case STONE        -> hasIronPickaxe();
            case IRON         -> hasFullArmorOf(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case DIAMOND      -> hasFullArmorOf(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            case NETHER       -> countInInventory(Items.BLAZE_ROD) >= 6;
            case STRONGHOLD   -> entity.isEndPortalActivated();
            case END          -> {
                if (entity.level() instanceof ServerLevel sl) {
                    // Check the End dimension directly — entity may be in overworld
                    ServerLevel endLevel = sl.getServer().getLevel(Level.END);
                    if (endLevel == null) yield false;
                    var fight = endLevel.getDragonFight();
                    yield fight != null && fight.hasPreviouslyKilledDragon();
                }
                yield false;
            }
            case HOME_BUILDING -> entity.getHomePos() != null;
            case POST_GAME    -> false;
        };
    }

    private boolean hasStonePickaxe() {
        return hasItemInInventory(Items.STONE_PICKAXE);
    }

    private boolean hasIronPickaxe() {
        return hasItemInInventory(Items.IRON_PICKAXE);
    }

    private boolean hasFullArmorOf(Item helmet, Item chest, Item legs, Item boots) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(helmet)
            && entity.getItemBySlot(EquipmentSlot.CHEST).is(chest)
            && entity.getItemBySlot(EquipmentSlot.LEGS).is(legs)
            && entity.getItemBySlot(EquipmentSlot.FEET).is(boots);
    }

    private boolean hasItemInInventory(Item item) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        // Also check main hand
        return entity.getMainHandItem().is(item);
    }

    private int countInInventory(Item item) {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
