package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class ChopTreeGoal extends Goal {

    private static final int  SEARCH_RADIUS  = 14;

    // 錯誤工具/空手懲罰（同 DiggingEscapeGoal）
    private static final float WRONG_TOOL_PENALTY    = 3.333f;
    private static final float TOOL_REQUIRED_PENALTY = 5.0f;

    private final FriendEntity entity;
    private BlockPos chopTarget;
    private int chopTimer;
    private int chopDuration;   // 改為動態計算
    private int cooldown;
    private boolean usedAxe;

    public ChopTreeGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        // ChopTreeGoal only active during WOOD, STONE, HOME_BUILDING
        com.edcl.lovelyfriend.entity.GameStage stage = entity.getGameStage();
        if (stage != com.edcl.lovelyfriend.entity.GameStage.WOOD
         && stage != com.edcl.lovelyfriend.entity.GameStage.STONE
         && stage != com.edcl.lovelyfriend.entity.GameStage.HOME_BUILDING) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isVehicle()) return false;

        chopTarget = findLog();
        return chopTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getTarget() != null) return false;
        if (entity.isVehicle()) return false;

        // 繼續砍，直到附近真的沒木頭了
        if (chopTarget == null) return false;
        if (!entity.level().getBlockState(chopTarget).is(BlockTags.LOGS)) {
            // 當前方塊沒了 → 找下一根
            BlockPos next = findLog();
            if (next != null) {
                chopTarget = next;
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        chopTimer = 0;
        usedAxe = entity.equipAxe();

        // 使用真實挖掘公式計算所需時間
        chopDuration = calculateChopTicks(chopTarget);
        navigateTo(chopTarget);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedAxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        chopTarget = null;
        cooldown = 30; // 短冷卻，很快就會繼續砍下一根
    }

    @Override
    public void tick() {
        if (chopTarget == null) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;

        // Re-check target each tick
        if (!entity.level().getBlockState(chopTarget).is(BlockTags.LOGS)) {
            clearProgress();
            // Try the log above first (strip the trunk upward)
            BlockPos above = chopTarget.above();
            if (entity.level().getBlockState(above).is(BlockTags.LOGS)) {
                chopTarget = above;
                chopTimer = 0;
                chopDuration = calculateChopTicks(chopTarget);
            } else {
                chopTarget = findLog();
                chopTimer = 0;
                if (chopTarget != null) {
                    chopDuration = calculateChopTicks(chopTarget);
                }
            }
            if (chopTarget != null) navigateTo(chopTarget);
            return;
        }

        entity.getLookControl().setLookAt(
                chopTarget.getX() + 0.5, chopTarget.getY() + 0.5, chopTarget.getZ() + 0.5);

        // Move closer if needed
        if (entity.blockPosition().distSqr(chopTarget) > 9) {
            clearProgress();
            navigateTo(chopTarget);
            return;
        }

        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), chopTarget, Math.min(chopTimer * 10 / chopDuration, 9));
        chopTimer++;

        if (chopTimer >= chopDuration) {
            clearProgress();
            entity.level().destroyBlock(chopTarget, true, entity);
            chopTimer = 0;

            // 砍完一根後立刻找下一根，不要停！
            BlockPos above = chopTarget.above();
            if (entity.level().getBlockState(above).is(BlockTags.LOGS)) {
                chopTarget = above;
                chopDuration = calculateChopTicks(chopTarget);
            } else {
                chopTarget = findLog();
                if (chopTarget != null) {
                    chopDuration = calculateChopTicks(chopTarget);
                }
            }
            if (chopTarget != null) navigateTo(chopTarget);
        }
    }

    private void navigateTo(BlockPos pos) {
        if (pos == null) return;
        entity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    private void clearProgress() {
        if (chopTarget != null && entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), chopTarget, -1);
        }
    }

    /**
     * 使用真實 Minecraft 挖掘公式計算砍樹 ticks
     * 同 DiggingEscapeGoal.calculateDigTicks()
     */
    private int calculateChopTicks(BlockPos pos) {
        if (pos == null) return 40;

        Level level = entity.level();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);

        if (hardness < 0) return 999;
        if (hardness == 0) return 2;

        ItemStack tool = entity.getMainHandItem();
        float speed;

        if (tool.isEmpty()) {
            speed = 1.0f;
        } else {
            float toolSpeed = tool.getDestroySpeed(state);
            if (toolSpeed > 1.0f) {
                // 工具對木頭有效（斧頭），speed 取決於材質：
                //   木斧=2.0, 石斧=4.0, 鐵斧=6.0, 鑽斧=8.0, 獄髓斧=9.0, 金斧=12.0
                speed = toolSpeed;
            } else {
                // 用錯工具（如鎬子砍樹）
                speed = 1.0f;
                if (!tool.isCorrectToolForDrops(state) && state.requiresCorrectToolForDrops()) {
                    speed = 1.0f / TOOL_REQUIRED_PENALTY;
                }
            }
        }

        // 空手砍樹：原版斧頭對原木才有效，空手 5x 懲罰
        if (tool.isEmpty() && state.requiresCorrectToolForDrops()) {
            speed = 1.0f / TOOL_REQUIRED_PENALTY;
        }

        float timeInSeconds = hardness * 1.5f / speed;
        int ticks = Math.max(1, (int) Math.ceil(timeInSeconds * 20.0f));
        return Math.min(ticks, 6000);
    }

    private BlockPos findLog() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 8; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(BlockTags.LOGS)) continue;
                    // Prefer the lowest log of each trunk
                    if (entity.level().getBlockState(pos.below()).is(BlockTags.LOGS)) continue;
                    double d = origin.distSqr(pos);
                    if (d < bestDist) { bestDist = d; best = pos; }
                }
            }
        }
        return best;
    }
}