package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DiggingEscapeGoal extends Goal {

    // Stuck detection
    private static final int    STUCK_TICKS            = 50;   // ticks of no movement
    private static final double STUCK_DIST_SQ          = 0.05; // must move < 0.22 blocks/tick

    // Wrong-tool penalty: Minecraft applies 3.33x slower when using wrong tool,
    // and 5x if the block requires a tool at all (drops nothing without correct tool)
    private static final float  WRONG_TOOL_PENALTY      = 3.333f;
    private static final float  TOOL_REQUIRED_PENALTY   = 5.0f;

    private final FriendEntity entity;
    private Vec3    lastPos;
    private int     stuckTicks;
    private BlockPos digTarget;
    private int     digTimer;
    private int     digTicksRequired;
    private int     cooldown;
    private boolean usedTool;

    public DiggingEscapeGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;

        // No tool requirement – bare hands work (just very slow)
        if (entity.getTarget() != null) return false;
        if (entity.isInWater() || entity.isPassenger()) return false;

        Vec3 pos = entity.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) < STUCK_DIST_SQ) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastPos = pos;

        // Require navigation active OR stuckTicks already high enough to try anyway
        boolean navActive = entity.getNavigation().isInProgress();
        if (!navActive && stuckTicks < STUCK_TICKS * 2) {
            return false;
        }
        if (stuckTicks < STUCK_TICKS) return false;

        digTarget = findBlockingBlock();
        return digTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        return digTarget != null;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        digTimer = 0;

        // Try to equip a useful tool for the target block
        if (digTarget != null) {
            usedTool = equipBestToolForBlock(digTarget);
        } else {
            usedTool = entity.equipPickaxe();
            if (!usedTool) usedTool = entity.equipAxe();
        }

        // Calculate required ticks using player mining formula
        digTicksRequired = calculateDigTicks(digTarget);
    }

    @Override
    public void stop() {
        entity.setMidBlockBreak(false);
        clearProgress();
        if (usedTool) entity.restoreWeapon();
        digTarget = null;
        cooldown = 80;
    }

    @Override
    public void tick() {
        if (digTarget == null) return;

        entity.getLookControl().setLookAt(
                digTarget.getX() + 0.5, digTarget.getY() + 0.5, digTarget.getZ() + 0.5);

        digTimer++;
        entity.setMidBlockBreak(true);
        entity.setTarget(null); // suppress combat interruption while mid-block

        // Show block break progress (scaled to required ticks)
        if (entity.level() instanceof ServerLevel sl) {
            int progress = Math.min(digTimer * 10 / digTicksRequired, 9);
            sl.destroyBlockProgress(entity.getId(), digTarget, progress);
        }

        if (digTimer >= digTicksRequired) {
            boolean broken = entity.level().destroyBlock(digTarget, true, entity);

            // If block couldn't be broken (e.g. unbreakable), give up on it
            if (!broken) {
                clearProgress();
                digTarget = null;
                cooldown = 40;
                return;
            }

            clearProgress();
            digTarget = null;
        }
    }

    private void clearProgress() {
        if (digTarget != null && entity.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(entity.getId(), digTarget, -1);
        }
    }

    /**
     * 使用 Minecraft 真實玩家挖掘公式計算所需 ticks
     *
     * 工具速度取決於品質（材質）：
     *   空手     = 1.0
     *   木頭工具  = 2.0  （木鎬/木斧/木鏟/木鋤）
     *   石頭工具  = 4.0
     *   鐵工具    = 6.0
     *   鑽石工具  = 8.0
     *   獄髓工具  = 9.0
     *   金工具    = 12.0 (但耐久極低)
     *
     * 公式（參考 Minecraft 原始碼）：
     *   1. 計算基礎速度:
     *      - getDestroySpeed(state) 根據方塊類型與工具材質回傳速度
     *      - 如果是適合的方塊類型（如鎬對石頭），回傳工具品質速度
     *      - 否則回傳 1.0
     *   2. 若不適用正確工具 && 方塊需要工具才掉落：
     *      - 速度乘以 0.2（即除以 5）
     *   3. 破壞時間 = hardness × 1.5 / speed （秒）
     *   4. ticks = ceil(時間 × 20)
     */
    private int calculateDigTicks(BlockPos pos) {
        if (pos == null) return 999;

        Level level = entity.level();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);

        // 基岩或無法破壞
        if (hardness < 0) return 999;
        if (hardness == 0) return 2; // 瞬間破壞

        ItemStack tool = entity.getMainHandItem();
        float speed;

        if (tool.isEmpty()) {
            // 空手：速度 = 1.0
            speed = 1.0f;
        } else {
            // 取得工具對這個方塊的破壞速度（內含品質/材質影響）
            float toolSpeed = tool.getDestroySpeed(state);

            if (toolSpeed > 1.0f) {
                // 工具對這個方塊類型有效（例如鎬對石頭、斧對木頭）
                // toolSpeed 已經反映了工具品質：
                //   木頭 = 2.0, 石頭 = 4.0, 鐵 = 6.0, 鑽石 = 8.0, 獄髓 = 9.0, 金 = 12.0
                speed = toolSpeed;

                // ⭐ 效率附魔加成（未來可擴充）
                // int efficiency = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.EFFICIENCY, tool);
                // if (efficiency > 0) speed += (efficiency * efficiency + 1) * 0.5f;
            } else {
                // 工具對此方塊無效 → 速度 = 1.0（用錯工具類型）
                speed = 1.0f;

                // 若方塊需要特定工具才掉落，且用錯工具 → Minecraft 懲罰
                if (!tool.isCorrectToolForDrops(state) && state.requiresCorrectToolForDrops()) {
                    speed = 1.0f / TOOL_REQUIRED_PENALTY; // × 0.2（除以 5）
                }
            }
        }

        // 空手且方塊需要工具才掉落 → 3.33x 懲罰
        if (tool.isEmpty() && state.requiresCorrectToolForDrops()) {
            // 注意：實際上 Minecraft 空手挖石頭是 5x 懲罰
            // 這裡用 WRONG_TOOL_PENALTY 代表空手時方塊不掉落但還是會慢慢破壞
            speed = 1.0f / TOOL_REQUIRED_PENALTY;
        }

        // Minecraft 標準公式：時間（秒）= hardness × 1.5 / speed
        float timeInSeconds = hardness * 1.5f / speed;
        int ticks = Math.max(1, (int) Math.ceil(timeInSeconds * 20.0f));

        // 上限 5 分鐘防止卡死
        return Math.min(ticks, 6000);
    }

    /**
     * 裝備最適合破壞目標方塊的工具
     */
    private boolean equipBestToolForBlock(BlockPos pos) {
        Level level = entity.level();
        BlockState state = level.getBlockState(pos);

        // Find the best tool in inventory for this block
        float bestSpeed = 1.0f;
        int bestSlot = -1;

        // Check main hand first
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty()) {
            float speed = mainHand.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
            }
        }

        // Search inventory for a better tool
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot >= 0) {
            // Swap to the better tool
            ItemStack hand = entity.getMainHandItem().copy();
            entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    entity.getInventory().getItem(bestSlot).copy());
            entity.getInventory().setItem(bestSlot, hand);
            return true;
        }

        // If current hand tool is already reasonably effective, keep it
        ItemStack hand = entity.getMainHandItem();
        if (!hand.isEmpty() && hand.getDestroySpeed(state) > 1.0f) {
            return true;
        }

        // Try equip pickaxe or axe as fallback
        if (entity.equipPickaxe()) return true;
        if (entity.equipAxe()) return true;

        return false; // bare hands
    }

    private BlockPos findBlockingBlock() {
        Level level = entity.level();
        BlockPos entityPos = entity.blockPosition();

        // Ceiling check: entity occupies dy=0 (feet) and dy=1 (head, 1.8-block height).
        // Only dy=1 can actually block movement; dy=2 is above head and never obstructs.
        BlockPos ceiling = entityPos.above(1);
        BlockState ceilState = level.getBlockState(ceiling);
        if (ceilState.isSolid() && !ceilState.liquid()
                && ceilState.getDestroySpeed(level, ceiling) >= 0
                && ceilState.getDestroySpeed(level, ceiling) < 50) {
            return ceiling;
        }

        Direction preferred = getPathDirection();

        for (int pass = 0; pass < 2; pass++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (pass == 0 && dir != preferred) continue;
                // Head level (dy=1) FIRST — preserves the foot-level block as a foothold.
                // Digging the foot block first removes the step the entity needs to climb out.
                for (int dy = 1; dy >= 0; dy--) {
                    BlockPos candidate = entityPos.relative(dir).above(dy);
                    BlockState state = level.getBlockState(candidate);
                    if (state.isSolid() && !state.liquid()
                            && state.getDestroySpeed(level, candidate) >= 0
                            && state.getDestroySpeed(level, candidate) < 50) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Direction getPathDirection() {
        Path path = entity.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Node next = path.getNextNode();
            if (next != null) {
                BlockPos pos = entity.blockPosition();
                int dx = next.x - pos.getX();
                int dz = next.z - pos.getZ();
                if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
                return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }
        // Fall back to where the entity is facing
        float yaw = entity.getYRot();
        if (yaw < -135 || yaw >= 135) return Direction.NORTH;
        if (yaw < -45)  return Direction.WEST;
        if (yaw < 45)   return Direction.SOUTH;
        return Direction.EAST;
    }
}