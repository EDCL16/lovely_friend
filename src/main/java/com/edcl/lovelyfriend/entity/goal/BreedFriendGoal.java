package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class BreedFriendGoal extends Goal {

    private static final int COOLDOWN = 6000;   // 5 min per entity
    private static final int BREED_TICKS = 60;  // 3 sec of hearts before spawn
    private static final double SEARCH_RADIUS = 8.0;
    private static final double BREED_RANGE = 2.5;

    private final FriendEntity entity;
    private FriendEntity partner;
    private int breedTimer;
    private int cooldown;

    public BreedFriendGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != com.edcl.lovelyfriend.entity.GameStage.POST_GAME) return false;
        if (entity.isPassenger() || entity.isVehicle()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isBreedingOnCooldown()) return false;
        if (entity.getRandom().nextInt(400) != 0) return false;

        partner = findPartner();
        return partner != null;
    }

    @Override
    public boolean canContinueToUse() {
        return partner != null && partner.isAlive() && breedTimer < BREED_TICKS;
    }

    @Override
    public void start() {
        breedTimer = 0;
        entity.getNavigation().moveTo(partner, 1.0);
    }

    @Override
    public void stop() {
        partner = null;
        cooldown = COOLDOWN;
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (partner == null) return;

        entity.getLookControl().setLookAt(partner);
        double dist = entity.distanceToSqr(partner);

        if (dist <= BREED_RANGE * BREED_RANGE) {
            entity.getNavigation().stop();
            breedTimer++;

            if (entity.level() instanceof ServerLevel serverLevel) {
                double px = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth();
                double py = entity.getY() + entity.getBbHeight();
                double pz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth();
                serverLevel.sendParticles(ParticleTypes.HEART, px, py, pz, 1, 0.0, 0.1, 0.0, 0.0);
            }

            if (breedTimer >= BREED_TICKS) {
                spawnOffspring();
            }
        } else {
            entity.getNavigation().moveTo(partner, 1.0);
        }
    }

    private void spawnOffspring() {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        FriendEntity baby = new FriendEntity(ModEntityTypes.FRIEND, serverLevel);
        double mx = (entity.getX() + partner.getX()) / 2;
        double mz = (entity.getZ() + partner.getZ()) / 2;
        baby.snapTo(mx, entity.getY(), mz, entity.getYRot(), 0.0f);
        baby.finalizeSpawn(serverLevel,
                serverLevel.getCurrentDifficultyAt(baby.blockPosition()),
                EntitySpawnReason.BREEDING, null);
        serverLevel.addFreshEntity(baby);

        entity.setBreedCooldown(COOLDOWN);
        partner.setBreedCooldown(COOLDOWN);
    }

    private FriendEntity findPartner() {
        AABB box = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<FriendEntity> nearby = entity.level().getEntitiesOfClass(FriendEntity.class, box);

        for (FriendEntity friend : nearby) {
            if (friend == entity) continue;
            if (!friend.isAlive()) continue;
            if (friend.isPassenger() || friend.isVehicle()) continue;
            if (friend.getTarget() != null) continue;
            if (friend.isBreedingOnCooldown()) continue;
            return friend;
        }
        return null;
    }
}
