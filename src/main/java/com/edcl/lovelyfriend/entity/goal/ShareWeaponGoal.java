package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class ShareWeaponGoal extends Goal {

    private static final double SEARCH_RADIUS = 10.0;

    private final FriendEntity entity;
    private Player targetPlayer;
    private int cooldown;

    public ShareWeaponGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        cooldown = 60; // Check every 3 seconds

        if (!entity.level().isClientSide()) {
            targetPlayer = findPlayerWithoutWeapon();
        }
        return targetPlayer != null;
    }

    @Override
    public void start() {
        if (targetPlayer == null) return;

        // Find a spare weapon to give away
        int weaponSlot = findSpareWeaponSlot();
        if (weaponSlot >= 0) {
            ItemStack weaponToGive = entity.getInventory().getItem(weaponSlot).copy();

            // Drop it for the player
            entity.spawnAtLocation(
                    entity.level() instanceof ServerLevel sl ? sl : null,
                    weaponToGive
            );

            // Clear the slot
            entity.getInventory().setItem(weaponSlot, ItemStack.EMPTY);

            targetPlayer = null;
            cooldown = 200; // Longer cooldown after giving a weapon
            return;
        }

        // If no spare in inventory, check if we can swap our main hand weapon
        if (hasWeaponInInventoryElsewhere()) {
            ItemStack handWeapon = entity.getMainHandItem().copy();
            // Move inventory weapon to hand
            for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
                ItemStack stack = entity.getInventory().getItem(i);
                if (!stack.isEmpty() && entity.isWeapon(stack)) {
                    entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack.copy());
                    entity.getInventory().setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
            // Drop the old hand weapon
            entity.spawnAtLocation(
                    entity.level() instanceof ServerLevel sl ? sl : null,
                    handWeapon
            );
        }

        targetPlayer = null;
        cooldown = 200;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private Player findPlayerWithoutWeapon() {
        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<Player> players = entity.level().getEntitiesOfClass(Player.class, searchBox);

        for (Player player : players) {
            if (player.isAlive() && !player.isSpectator()) {
                ItemStack hand = player.getMainHandItem();
                if (hand.isEmpty() || !entity.isWeapon(hand)) {
                    if (canGiveWeapon()) {
                        return player;
                    }
                }
            }
        }
        return null;
    }

    private boolean canGiveWeapon() {
        return findSpareWeaponSlot() >= 0 || hasWeaponInInventoryElsewhere();
    }

    private boolean hasWeaponInInventoryElsewhere() {
        // Check if there's any weapon in inventory besides what's in the main hand
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && entity.isWeapon(stack)) {
                return true;
            }
        }
        return false;
    }

    private int findSpareWeaponSlot() {
        int weaponCount = 0;
        if (entity.isWeapon(entity.getMainHandItem())) {
            weaponCount++;
        }
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && entity.isWeapon(stack)) {
                weaponCount++;
            }
        }

        // Need more than 1 weapon total to give one away
        if (weaponCount <= 1) return -1;

        // Find the weakest weapon in inventory to give away
        int weakestSlot = -1;
        int weakestScore = Integer.MAX_VALUE;

        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && entity.isWeapon(stack)) {
                // Don't give away our pickaxe if it's the only one
                if (entity.isPickaxe(stack)) {
                    int pickaxeCount = 0;
                    if (entity.isPickaxe(entity.getMainHandItem())) pickaxeCount++;
                    for (int j = 0; j < entity.getInventory().getContainerSize(); j++) {
                        ItemStack s = entity.getInventory().getItem(j);
                        if (!s.isEmpty() && entity.isPickaxe(s)) pickaxeCount++;
                    }
                    if (pickaxeCount <= 1) continue;
                }

                int score = entity.getWeaponScore(stack);
                if (score < weakestScore) {
                    weakestScore = score;
                    weakestSlot = i;
                }
            }
        }

        return weakestSlot;
    }
}