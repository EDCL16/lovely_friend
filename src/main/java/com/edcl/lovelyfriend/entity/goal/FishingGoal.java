package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class FishingGoal extends Goal {

    private static final int MIN_CAST = 80;
    private static final int MAX_CAST = 160;
    private static final int SEARCH_RADIUS = 12;
    private static final int COOLDOWN = 600;
    private static final double FISH_RANGE = 2.5;

    private final FriendEntity entity;
    private BlockPos waterPos;
    private BlockPos standPos;
    private int castTimer;
    private int cooldown;
    private boolean atWater;

    public FishingGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage().ordinal() < com.edcl.lovelyfriend.entity.GameStage.IRON.ordinal()) return false;
        if (!entity.hasFishingRod()) return false;
        if (entity.isPassenger()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isLeashed() && entity.getRandom().nextInt(5) != 0) return false;
        if (entity.getRandom().nextInt(80) != 0) return false;

        return findWaterSpot();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.hasFishingRod() && waterPos != null && entity.getTarget() == null;
    }

    @Override
    public void start() {
        entity.equipFishingRod();
        atWater = false;
        castTimer = 0;
        if (standPos != null) {
            entity.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        entity.restoreWeapon();
        entity.getNavigation().stop();
        cooldown = COOLDOWN;
        atWater = false;
        waterPos = null;
        standPos = null;
    }

    @Override
    public void tick() {
        if (waterPos == null) return;

        double dx = entity.getX() - (waterPos.getX() + 0.5);
        double dz = entity.getZ() - (waterPos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (!atWater) {
            if (dist <= FISH_RANGE) {
                entity.getNavigation().stop();
                atWater = true;
                castTimer = MIN_CAST + entity.getRandom().nextInt(MAX_CAST - MIN_CAST);
                entity.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.5f, 1.0f + entity.getRandom().nextFloat() * 0.2f);
            }
        } else {
            entity.getLookControl().setLookAt(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
            castTimer--;
            if (castTimer <= 0) {
                catchFish();
                entity.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.5f, 1.0f);
                castTimer = MIN_CAST + entity.getRandom().nextInt(MAX_CAST - MIN_CAST);
                entity.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.5f, 1.0f);
            }
        }
    }

    private void catchFish() {
        int roll = entity.getRandom().nextInt(10);
        ItemStack fish;
        if (roll < 6)      fish = new ItemStack(Items.COD);
        else if (roll < 9) fish = new ItemStack(Items.SALMON);
        else               fish = new ItemStack(Items.TROPICAL_FISH);

        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, fish);
                return;
            }
        }
    }

    private boolean findWaterSpot() {
        Level level = entity.level();
        BlockPos origin = entity.blockPosition();

        for (int attempt = 0; attempt < 30; attempt++) {
            int ox = entity.getRandom().nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;
            int oz = entity.getRandom().nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;

            for (int oy = 2; oy >= -2; oy--) {
                BlockPos pos = origin.offset(ox, oy, oz);
                if (!level.getBlockState(pos).getFluidState().is(FluidTags.WATER)) continue;

                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos land = pos.relative(dir);
                    if (isValidStand(level, land)) {
                        waterPos = pos;
                        standPos = land;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isValidStand(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getBlockState(pos.below()).isSolid()
                && !state.isSolid()
                && state.getFluidState().isEmpty();
    }
}
