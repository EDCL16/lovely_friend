package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class FindFoodGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0;
    private static final double EAT_RANGE = 2.0;

    private final FriendEntity entity;
    private double targetX, targetY, targetZ;
    private boolean moving;
    private int cooldown;

    public FindFoodGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        if (!entity.isHungry()) return false;
        if (entity.hasFoodInInventory()) return false;

        cooldown = 40;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.hasFoodInInventory()) return false;
        if (!entity.isHungry()) return false;
        return moving;
    }

    @Override
    public void start() {
        findFoodSource();
    }

    @Override
    public void tick() {
        if (moving) {
            entity.getNavigation().moveTo(targetX, targetY, targetZ, 1.0);
            double dist = entity.distanceToSqr(targetX, targetY, targetZ);
            if (dist < EAT_RANGE * EAT_RANGE) {
                entity.getNavigation().stop();
                consumeFoodBlock();
            }
        }
    }

    @Override
    public void stop() {
        moving = false;
    }

    private void findFoodSource() {
        Level level = entity.level();
        BlockPos origin = entity.blockPosition();

        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                origin.getX() - SEARCH_RADIUS, origin.getY() - 4, origin.getZ() - SEARCH_RADIUS,
                origin.getX() + SEARCH_RADIUS, origin.getY() + 4, origin.getZ() + SEARCH_RADIUS
        );

        // 1. Search for crops
        List<BlockPos> crops = new ArrayList<>();
        for (int x = (int) searchBox.minX; x <= (int) searchBox.maxX; x++) {
            for (int y = (int) searchBox.minY; y <= (int) searchBox.maxY; y++) {
                for (int z = (int) searchBox.minZ; z <= (int) searchBox.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isEdibleCrop(state)) {
                        crops.add(pos);
                    }
                }
            }
        }

        if (!crops.isEmpty()) {
            crops.sort(java.util.Comparator.comparingDouble(origin::distSqr));
            BlockPos best = crops.get(0);
            targetX = best.getX() + 0.5;
            targetY = best.getY();
            targetZ = best.getZ() + 0.5;
            moving = true;
            return;
        }

        // 2. Search for animals
        List<net.minecraft.world.entity.animal.Animal> animals = level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, searchBox,
                animal -> !animal.isDeadOrDying()
        );

        if (!animals.isEmpty()) {
            animals.sort(java.util.Comparator.comparingDouble(entity::distanceToSqr));
            net.minecraft.world.entity.animal.Animal animal = animals.get(0);
            targetX = animal.getX();
            targetY = animal.getY();
            targetZ = animal.getZ();
            moving = true;
        }
    }

    private boolean isEdibleCrop(BlockState state) {
        if (state.is(Blocks.WHEAT)) return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7) == 7;
        if (state.is(Blocks.CARROTS)) return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7) == 7;
        if (state.is(Blocks.POTATOES)) return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7) == 7;
        if (state.is(Blocks.BEETROOTS)) return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3) == 3;
        if (state.is(Blocks.NETHER_WART)) return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3) == 3;
        return false;
    }

    private void consumeFoodBlock() {
        BlockPos pos = entity.blockPosition();
        if (entity.level().isClientSide()) return;

        // Search for edible crop within range
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos check = pos.offset(x, y, z);
                    BlockState state = entity.level().getBlockState(check);
                    if (isEdibleCrop(state)) {
                        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.destroyBlock(check, true, entity);
                        }
                        net.minecraft.world.item.ItemStack food = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD);
                        entity.eatFood(food);
                        moving = false;
                        cooldown = 100;
                        return;
                    }
                }
            }
        }

        // If no block found nearby, hunt animal
        eatAnimal();
    }

    private void eatAnimal() {
        List<net.minecraft.world.entity.animal.Animal> animals = entity.level().getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class,
                entity.getBoundingBox().inflate(4.0)
        );

        for (net.minecraft.world.entity.animal.Animal animal : animals) {
            animal.hurt(entity.damageSources().mobAttack(entity), 3.0f);
            if (animal.isDeadOrDying()) {
                net.minecraft.world.item.ItemStack food = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD);
                entity.eatFood(food);
                moving = false;
                cooldown = 200;
                return;
            }
        }
    }
}