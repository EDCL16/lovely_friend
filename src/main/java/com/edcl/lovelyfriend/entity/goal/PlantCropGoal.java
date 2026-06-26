package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

public class PlantCropGoal extends Goal {

    private static final double SEARCH_RADIUS = 8.0;
    private static final int CHECK_INTERVAL = 100;

    private final FriendEntity entity;
    private BlockPos targetPos;
    private int cooldown;
    private int searchCooldown;

    public PlantCropGoal(FriendEntity entity) {
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
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        plantCrop();
    }

    private void plantCrop() {
        Level level = entity.level();
        if (level.isClientSide()) return;

        // Find tilled soil nearby
        List<BlockPos> tilledSoil = findTilledSoil(level, entity.blockPosition());
        if (tilledSoil.isEmpty()) return;

        // Get seed from inventory
        net.minecraft.world.item.ItemStack seed = findSeedInInventory();
        if (seed.isEmpty()) return;

        // Plant at the first available tilled soil
        BlockPos pos = tilledSoil.get(0);
        BlockState currentState = level.getBlockState(pos);

        if (currentState.is(Blocks.FARMLAND)) {
            // Plant the crop based on seed type
            BlockState cropState = getCropStateFromSeed(seed);
            if (cropState != null) {
                level.setBlockAndUpdate(pos, cropState);
                seed.shrink(1);
            }
        }
    }

    private List<BlockPos> findTilledSoil(Level level, BlockPos origin) {
        List<BlockPos> soil = new java.util.ArrayList<>();
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                origin.getX() - SEARCH_RADIUS, origin.getY() - 2, origin.getZ() - SEARCH_RADIUS,
                origin.getX() + SEARCH_RADIUS, origin.getY() + 2, origin.getZ() + SEARCH_RADIUS
        );

        for (int x = (int) searchBox.minX; x <= (int) searchBox.maxX; x++) {
            for (int y = (int) searchBox.minY; y <= (int) searchBox.maxY; y++) {
                for (int z = (int) searchBox.minZ; z <= (int) searchBox.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.FARMLAND)) {
                        // Check if there's air above it
                        BlockPos above = pos.above();
                        if (level.isEmptyBlock(above)) {
                            soil.add(above);
                        }
                    }
                }
            }
        }

        soil.sort(java.util.Comparator.comparingDouble(origin::distSqr));
        return soil;
    }

    private net.minecraft.world.item.ItemStack findSeedInInventory() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = entity.getInventory().getItem(i);
            if (isSeed(stack)) {
                return stack;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    private boolean isSeed(net.minecraft.world.item.ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.WHEAT_SEEDS) ||
               stack.is(net.minecraft.world.item.Items.CARROT) ||
               stack.is(net.minecraft.world.item.Items.POTATO) ||
               stack.is(net.minecraft.world.item.Items.BEETROOT_SEEDS) ||
               stack.is(net.minecraft.world.item.Items.NETHER_WART);
    }

    private BlockState getCropStateFromSeed(net.minecraft.world.item.ItemStack seed) {
        if (seed.is(net.minecraft.world.item.Items.WHEAT_SEEDS)) {
            return Blocks.WHEAT.defaultBlockState();
        }
        if (seed.is(net.minecraft.world.item.Items.CARROT)) {
            return Blocks.CARROTS.defaultBlockState();
        }
        if (seed.is(net.minecraft.world.item.Items.POTATO)) {
            return Blocks.POTATOES.defaultBlockState();
        }
        if (seed.is(net.minecraft.world.item.Items.BEETROOT_SEEDS)) {
            return Blocks.BEETROOTS.defaultBlockState();
        }
        if (seed.is(net.minecraft.world.item.Items.NETHER_WART)) {
            return Blocks.NETHER_WART.defaultBlockState();
        }
        return null;
    }
}