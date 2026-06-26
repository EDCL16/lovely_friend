package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class PlaceBlockToClimbGoal extends Goal {

    private static final int STUCK_TICKS = 40;
    private static final double STUCK_DIST_SQ = 0.1;
    private static final int MAX_PLACEMENTS = 6; // max stair steps per activation

    private final FriendEntity entity;
    private Vec3 lastPos;
    private int stuckTicks;
    private int placedCount;
    private int cooldown;

    public PlaceBlockToClimbGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getTarget() != null) return false;

        if (!entity.getNavigation().isInProgress()) {
            stuckTicks = 0;
            lastPos = null;
            return false;
        }

        Vec3 pos = entity.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) < STUCK_DIST_SQ) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = pos;

        if (stuckTicks < STUCK_TICKS) return false;

        // Only climb if destination is above current position
        if (!destinationIsHigher()) return false;

        return !findPlaceableBlock().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return placedCount < MAX_PLACEMENTS
                && !findPlaceableBlock().isEmpty()
                && entity.getNavigation().isInProgress();
    }

    @Override
    public void start() {
        stuckTicks = 0;
        placedCount = 0;
    }

    @Override
    public void stop() {
        cooldown = 80;
    }

    @Override
    public void tick() {
        if (!(entity.level() instanceof ServerLevel sl)) return;

        Direction forward = getForwardDirection();
        if (forward == null) { return; }

        BlockPos entityPos = entity.blockPosition();

        // Place a step block one forward at entity feet level
        BlockPos placePos = entityPos.relative(forward);
        Level level = entity.level();
        BlockState existing = level.getBlockState(placePos);

        if (existing.isAir() || existing.canBeReplaced()) {
            // Need solid ground below to place against
            boolean solidBelow = level.getBlockState(placePos.below()).isSolid();
            if (!solidBelow) {
                // Try one lower (to fill a gap)
                placePos = entityPos.relative(forward).below();
                existing = level.getBlockState(placePos);
                if (!existing.isAir() && !existing.canBeReplaced()) return;
            }

            ItemStack blockStack = findPlaceableBlock();
            if (blockStack.isEmpty()) return;

            Block toPlace = ((BlockItem) blockStack.getItem()).getBlock();
            sl.setBlock(placePos, toPlace.defaultBlockState(), 3);
            blockStack.shrink(1);
            placedCount++;
        }

        // Navigate toward the step
        entity.getNavigation().moveTo(
                placePos.getX() + 0.5, placePos.getY() + 1.0, placePos.getZ() + 0.5, 1.0);
    }

    private boolean destinationIsHigher() {
        Path path = entity.getNavigation().getPath();
        if (path == null || path.getNodeCount() == 0) return false;
        Node end = path.getEndNode();
        if (end == null) return false;
        return end.y > entity.blockPosition().getY() + 1;
    }

    private Direction getForwardDirection() {
        Path path = entity.getNavigation().getPath();
        if (path == null || path.isDone()) return null;
        Node next = path.getNextNode();
        if (next == null) return null;
        BlockPos pos = entity.blockPosition();
        int dx = next.x - pos.getX();
        int dz = next.z - pos.getZ();
        if (dx == 0 && dz == 0) {
            // Use end node if next is same pos
            Node end = path.getEndNode();
            if (end == null) return null;
            dx = end.x - pos.getX();
            dz = end.z - pos.getZ();
        }
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private ItemStack findPlaceableBlock() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            if (isSafePlaceableBlock(block)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private boolean isSafePlaceableBlock(Block block) {
        // Only use common expendable solid blocks; avoid anything precious
        return block == Blocks.DIRT || block == Blocks.GRAVEL || block == Blocks.SAND
                || block == Blocks.COBBLESTONE || block == Blocks.STONE
                || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.ANDESITE
                || block == Blocks.DIORITE || block == Blocks.GRANITE
                || block == Blocks.NETHERRACK || block == Blocks.BLACKSTONE
                || block == Blocks.BASALT || block == Blocks.TUFF
                || block == Blocks.OAK_PLANKS || block == Blocks.SPRUCE_PLANKS
                || block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS
                || block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS
                || block == Blocks.MANGROVE_PLANKS || block == Blocks.CHERRY_PLANKS;
    }
}
