package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

public class ShareFoodGoal extends Goal {

    private static final double SEARCH_RADIUS = 12.0;
    private static final int CHECK_INTERVAL = 60;

    private final FriendEntity entity;
    private Player targetPlayer;
    private int cooldown;

    public ShareFoodGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;

        cooldown = CHECK_INTERVAL;
        targetPlayer = findHungryPlayer();
        return targetPlayer != null && hasFoodToShare();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (targetPlayer == null) return;
        entity.swing(InteractionHand.MAIN_HAND);

        // Find best food to share
        ItemStack foodToGive = findBestFoodToShare();
        if (!foodToGive.isEmpty()) {
            // Drop the food for the player
            entity.spawnAtLocation(
                    entity.level() instanceof ServerLevel sl ? sl : null,
                    foodToGive.copy()
            );
            foodToGive.shrink(1);
        }
    }

    private Player findHungryPlayer() {
        if (entity.level().isClientSide()) return null;

        net.minecraft.world.phys.AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<Player> players = entity.level().getEntitiesOfClass(Player.class, searchBox);

        for (Player player : players) {
            if (player.isAlive() && !player.isSpectator() && isHungry(player)) {
                return player;
            }
        }
        return null;
    }

    private boolean isHungry(Player player) {
        return player.getFoodData().getFoodLevel() < 18;
    }

    private boolean hasFoodToShare() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (isGoodFood(stack) && stack.getCount() > 1) {
                return true;
            }
        }
        return false;
    }

    private ItemStack findBestFoodToShare() {
        ItemStack bestFood = ItemStack.EMPTY;
        int bestNutrition = -1;

        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (isGoodFood(stack) && stack.getCount() > 1) {
                FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
                if (food != null) {
                    int nutrition = food.nutrition();
                    if (nutrition > bestNutrition || bestNutrition == -1) {
                        bestNutrition = nutrition;
                        bestFood = stack;
                    }
                }
            }
        }

        return bestFood;
    }

    private boolean isGoodFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food != null && food.nutrition() > 0;
    }
}