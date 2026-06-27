package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class MineIronGoal extends Goal {

    private static final int TARGET_Y = 32;
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_Y_RANGE = 20;
    private static final int TARGET_IRON = 8;
    private static final int COOLDOWN = 300;

    private final FriendEntity entity;
    private BlockPos target;
    private int breakTick, breakDuration, cooldown;
    private boolean usedPickaxe;

    public MineIronGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.IRON) return false;
        if (entity.getTarget() != null) return false;
        if (countRawIron() >= TARGET_IRON) return false;
        if (entity.getRandom().nextFloat() > 0.08f) return false;
        target = findIronOre();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && entity.getTarget() == null && countRawIron() < TARGET_IRON;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe();
        breakDuration = calcBreakTicks();
        entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedPickaxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        target = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (target == null || !(entity.level() instanceof ServerLevel sl)) return;
        if (!isIronOre(entity.level().getBlockState(target))) {
            target = findIronOre(); if (target == null) return;
            breakTick = 0; breakDuration = calcBreakTicks();
        }
        entity.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 6.0) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), target, Math.min(breakTick * 10 / breakDuration, 9));
        if (++breakTick >= breakDuration) {
            clearProgress();
            entity.level().destroyBlock(target, true, entity);
            breakTick = 0;
            target = findIronOre();
            if (target != null) { breakDuration = calcBreakTicks(); entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0); }
        }
    }

    private void clearProgress() {
        if (target != null && entity.level() instanceof ServerLevel sl)
            sl.destroyBlockProgress(entity.getId(), target, -1);
    }

    private BlockPos findIronOre() {
        BlockPos origin = entity.blockPosition();
        int searchY = Math.max(TARGET_Y - SEARCH_Y_RANGE, entity.level().getMinY() + 5);
        BlockPos best = null; double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -SEARCH_Y_RANGE; dy <= SEARCH_Y_RANGE; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (pos.getY() < searchY || pos.getY() > TARGET_Y + SEARCH_Y_RANGE) continue;
                    if (isIronOre(entity.level().getBlockState(pos))) {
                        double d = origin.distSqr(pos);
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
        // If no ore found near surface, head to TARGET_Y to dig
        if (best == null && origin.getY() > TARGET_Y + 5) {
            return new BlockPos(origin.getX(), TARGET_Y, origin.getZ());
        }
        return best;
    }

    private boolean isIronOre(BlockState s) {
        return s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE);
    }

    private int calcBreakTicks() {
        if (target == null) return 40;
        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness <= 0) return 4;
        ItemStack tool = entity.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0f : Math.max(1.0f, tool.getDestroySpeed(state));
        return Math.max(1, (int) Math.ceil(hardness * 1.5f / speed * 20));
    }

    private int countRawIron() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i); if (s.is(Items.RAW_IRON)) count += s.getCount();
        }
        return count;
    }
}
