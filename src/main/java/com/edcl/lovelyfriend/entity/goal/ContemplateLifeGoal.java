package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 思考人生 Goal
 *
 * 朋友偶爾會停下來「思考人生」，決定一個長期的目標。
 *
 * 核心設計：
 *  - 一旦決定目標（村莊或隨機遠方），targetPos 就會永久保存
 *  - 即使被打斷（戰鬥、吃東西、挖礦），重新 canUse() 時會直接繼續前進
 *  - 只有在真正抵達目標附近時，才會清除 targetPos 並開始下一次思考
 *  - 途中遇到障礙自然由 DiggingEscapeGoal / PlaceBlockToClimbGoal 處理
 *
 * 目標選擇優先序：
 *  1. 如果已經有 targetPos → 繼續執行（被打斷後恢復）
 *  2. 搜尋附近村莊
 *  3. 隨機遠方 100~300 格
 */
public class ContemplateLifeGoal extends Goal {

    // 思考人生冷卻（初次啟動用）
    private static final int COOLDOWN_MIN = 2400;  // 2 min
    private static final int COOLDOWN_MAX = 6000;  // 5 min

    // 抵達目標的判定距離（方塊）
    private static final double ARRIVAL_DIST_SQ = 25.0; // 半徑 5 格內就算到了

    // 村莊搜尋半徑
    private static final int VILLAGE_SEARCH_RADIUS = 64;

    // 卡住檢測（被打斷時持續計時）
    private static final int STUCK_TICKS = 60;
    private static final double STUCK_DIST_SQ = 0.05;

    private final FriendEntity entity;
    private int cooldown;

    /**
     * 持久目標位置。
     * 一旦設定，就會一直保留直到：
     *  - 抵達目標附近（ARRIVAL_DIST_SQ）
     *  - 卡住太久強制放棄
     */
    private BlockPos targetPos;

    // 目標追蹤
    private int stuckTimer;
    private Vec3 lastPos;
    private int totalStuckTime;    // 累計卡住時間，超過就放棄目標
    private static final int MAX_STUCK_TIME = 600; // 卡住 30 秒放棄

    // 計時器：避免每個 tick 都重新導航
    private int retryTimer;

    public ContemplateLifeGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.cooldown = COOLDOWN_MIN + entity.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
    }

    @Override
    public boolean canUse() {
        // 被拴繩、戰鬥中、騎乘中不思考
        if (entity.isLeashed()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isPassenger() || entity.isVehicle()) return false;
        com.edcl.lovelyfriend.entity.GameStage s = entity.getGameStage();
        if (s == com.edcl.lovelyfriend.entity.GameStage.WOOD) return false;
        if (s == com.edcl.lovelyfriend.entity.GameStage.POST_GAME) return false;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return false;

        // === 情況 A：已經有目標且尚未抵達 → 直接繼續 ===
        if (targetPos != null) {
            // 檢查是否到達
            if (hasArrived()) {
                // 到了！清除目標，下次重新思考
                targetPos = null;
                cooldown = COOLDOWN_MIN + entity.getRandom().nextInt(COOLDOWN_MAX - COOLDOWN_MIN);
                return false;
            }

            // 檢查是否卡住太久
            Vec3 currentPos = entity.position();
            if (currentPos.distanceToSqr(lastPos) < STUCK_DIST_SQ) {
                stuckTimer++;
                totalStuckTime++;
                if (totalStuckTime > MAX_STUCK_TIME) {
                    // 卡太久了，放棄這個目標
                    targetPos = null;
                    cooldown = 200;
                    return false;
                }
            } else {
                stuckTimer = 0;
            }
            lastPos = currentPos;

            // 目標還有效，繼續執行
            return true;
        }

        // === 情況 B：沒有目標 → 需要冷卻結束才能思考 ===
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        // === 情況 C：冷卻結束，決定新目標 ===
        if (entity.getNavigation().isInProgress()) {
            // 正在做其他事（挖礦、砍樹等），不中斷
            return false;
        }

        // 決定目標：優先找村莊
        targetPos = findVillage(serverLevel);
        if (targetPos == null) {
            // 找不到村莊，往隨機遠方
            targetPos = findFarAwayTarget(serverLevel);
            if (targetPos == null) {
                cooldown = 200;
                return false;
            }
        }

        // 重置追蹤狀態
        stuckTimer = 0;
        totalStuckTime = 0;
        lastPos = entity.position();
        retryTimer = 0;

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // 目標不存在 → 停止
        if (targetPos == null) return false;

        // 被攻擊就中斷（讓朋友先去戰鬥）
        if (entity.getTarget() != null) {
            return false;
        }

        // 到達目標 → 停止
        if (hasArrived()) {
            targetPos = null;
            return false;
        }

        // 卡住太久 → 停止
        if (totalStuckTime > MAX_STUCK_TIME) {
            targetPos = null;
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            startNavigating();
        }
        stuckTimer = 0;
        lastPos = entity.position();
    }

    @Override
    public void stop() {
        // 注意：不清除 targetPos！
        // 停下來只是因為被更高優先權的 Goal 打斷（戰鬥、吃東西等）
        // targetPos 保留下次 canUse() 時繼續
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        // 更新卡住計時
        Vec3 currentPos = entity.position();
        if (currentPos.distanceToSqr(lastPos) < STUCK_DIST_SQ) {
            totalStuckTime++;
        } else {
            totalStuckTime = Math.max(0, totalStuckTime - 2); // 有移動就減少卡住時間
        }
        lastPos = currentPos;

        // 每 5 ticks 重新導航一次（避免過度計算）
        retryTimer++;
        if (retryTimer >= 5 || !entity.getNavigation().isInProgress()) {
            retryTimer = 0;
            startNavigating();
        }
    }

    private void startNavigating() {
        if (targetPos == null) return;

        double dx = targetPos.getX() + 0.5 - entity.getX();
        double dz = targetPos.getZ() + 0.5 - entity.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        // 遠距走快點，近距走慢點
        double speed = dist > 50 ? 1.0 : 0.8;
        entity.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                speed);
    }

    /**
     * 檢查是否已抵達目標附近
     */
    private boolean hasArrived() {
        if (targetPos == null) return false;
        return entity.blockPosition().distSqr(targetPos) < ARRIVAL_DIST_SQ;
    }

    /**
     * 搜尋附近村莊
     */
    private BlockPos findVillage(ServerLevel level) {
        BlockPos origin = entity.blockPosition();

        TagKey<Structure> villageTag = net.minecraft.tags.StructureTags.VILLAGE;

        BlockPos villagePos = level.findNearestMapStructure(
                villageTag,
                origin, VILLAGE_SEARCH_RADIUS, false);

        if (villagePos != null) {
            int safeY = findSafeY(level, villagePos.getX(), villagePos.getZ());
            if (safeY != Integer.MIN_VALUE) {
                return new BlockPos(villagePos.getX(), safeY, villagePos.getZ());
            }
        }

        return null;
    }

    /**
     * 找不到村莊時往隨機遠方走
     */
    private BlockPos findFarAwayTarget(ServerLevel level) {
        BlockPos origin = entity.blockPosition();

        for (int attempt = 0; attempt < 30; attempt++) {
            int dist = 100 + entity.getRandom().nextInt(200);
            float angle = entity.getRandom().nextFloat() * (float) Math.PI * 2;
            int x = origin.getX() + (int) (Math.cos(angle) * dist);
            int z = origin.getZ() + (int) (Math.sin(angle) * dist);

            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;

            int safeY = findSafeY(level, x, z);
            if (safeY != Integer.MIN_VALUE) {
                if (level.getBlockState(new BlockPos(x, safeY, z)).getFluidState().isEmpty()) {
                    return new BlockPos(x, safeY, z);
                }
            }
        }

        return null;
    }

    /**
     * 找到地面高度
     */
    private int findSafeY(ServerLevel level, int x, int z) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (topY <= level.getMinY()) return Integer.MIN_VALUE;

        BlockPos feetPos = new BlockPos(x, topY, z);
        if (!level.getBlockState(feetPos).isSolid()
                && !level.getBlockState(feetPos.below()).isAir()) {
            return topY;
        }

        for (int y = topY; y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolid()) {
                return y + 1;
            }
        }

        return Integer.MIN_VALUE;
    }
}