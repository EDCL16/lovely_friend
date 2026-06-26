package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class ShareArmorGoal extends Goal {

    private static final double SEARCH_RADIUS = 10.0;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final FriendEntity entity;
    private Player targetPlayer;
    private EquipmentSlot targetSlot;
    private int cooldown;

    public ShareArmorGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 80;
        if (entity.level().isClientSide()) return false;

        AABB box = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<Player> players = entity.level().getEntitiesOfClass(Player.class, box);

        for (Player player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (!player.getItemBySlot(slot).isEmpty()) continue;
                if (findArmorForSlot(slot) >= 0) {
                    targetPlayer = player;
                    targetSlot = slot;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (targetPlayer == null || targetSlot == null) return;
        entity.swing(InteractionHand.MAIN_HAND);

        int invSlot = findArmorForSlot(targetSlot);
        if (invSlot < 0) return;

        ItemStack armor = entity.getInventory().getItem(invSlot).copy();
        entity.getInventory().setItem(invSlot, ItemStack.EMPTY);
        entity.spawnAtLocation(entity.level() instanceof ServerLevel sl ? sl : null, armor);

        targetPlayer = null;
        targetSlot = null;
        cooldown = 200;
    }

    private int findArmorForSlot(EquipmentSlot slot) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq != null && eq.slot() == slot) return i;
        }
        return -1;
    }
}
