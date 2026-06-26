package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.biome.Biome;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ExploreGoal extends Goal {

    private static final int SEARCH_RADIUS = 32;
    private static final int VISITED_MEMORY_SIZE = 50;
    private static final int COOLDOWN_BETWEEN_EXPLORATIONS = 400;

    private final FriendEntity entity;
    private BlockPos targetPos;
    private int cooldown;
    private int stuckTimer;
    private BlockPos lastPosition;

    private final Set<Long> visitedChunks = new HashSet<>();

    public ExploreGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return false;
        if (entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.getNavigation().isInProgress()) return false;
        if (entity.getRandom().nextFloat() > 0.01f) return false; // 1% chance

        targetPos = findNewBiome(serverLevel);
        if (targetPos != null) {
            cooldown = 60;
            return true;
        }

        cooldown = 400; // 20 seconds
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;
        if (!entity.getNavigation().isInProgress()) return false;

        if (entity.blockPosition().equals(lastPosition)) {
            stuckTimer++;
            if (stuckTimer > 100) {
                return false;
            }
        } else {
            stuckTimer = 0;
            lastPosition = entity.blockPosition();
        }

        return true;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            entity.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    0.8);
            lastPosition = entity.blockPosition();
            stuckTimer = 0;
        }
    }

    @Override
    public void stop() {
        if (targetPos != null) {
            markVisited(targetPos);
        }

        targetPos = null;
        entity.getNavigation().stop();
        cooldown = COOLDOWN_BETWEEN_EXPLORATIONS;
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        entity.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                0.8);
    }

    private void markVisited(BlockPos pos) {
        visitedChunks.add(chunkKey(pos));

        if (visitedChunks.size() > VISITED_MEMORY_SIZE) {
            Iterator<Long> it = visitedChunks.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    private BlockPos findNewBiome(ServerLevel serverLevel) {
        BlockPos origin = entity.blockPosition();

        for (int attempt = 0; attempt < 20; attempt++) {
            int x = origin.getX() + entity.getRandom().nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;
            int z = origin.getZ() + entity.getRandom().nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS;
            BlockPos probePos = new BlockPos(x, origin.getY(), z);

            long chunkKey = chunkKey(probePos);
            if (visitedChunks.contains(chunkKey)) continue;

            Holder<Biome> currentBiome = serverLevel.getBiome(origin);
            Holder<Biome> targetBiome = serverLevel.getBiome(probePos);

            if (currentBiome != targetBiome) {
                int safeY = findSafeY(serverLevel, x, z);
                if (safeY != Integer.MIN_VALUE) {
                    visitedChunks.add(chunkKey);
                    return new BlockPos(x, safeY, z);
                }
            }
        }

        return null;
    }

    private int findSafeY(ServerLevel level, int x, int z) {
        int topY = level.getHeight();

        for (int y = topY; y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolid()) {
                return y + 1;
            }
        }

        return Integer.MIN_VALUE;
    }

    private long chunkKey(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}