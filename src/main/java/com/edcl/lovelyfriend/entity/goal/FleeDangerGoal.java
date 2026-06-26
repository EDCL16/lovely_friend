package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class FleeDangerGoal extends Goal {

    private static final double FIRE_CHECK_RADIUS = 4.0;
    private static final double EXPLOSION_CHECK_RADIUS = 10.0;
    private static final int COOLDOWN_TICKS = 100; // 5 seconds
    private static final double FLEE_SPEED = 1.5;

    private final FriendEntity entity;
    private Vec3 fleeTarget;
    private int cooldown;

    public FleeDangerGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getTarget() != null) return false; // In combat

        fleeTarget = findEscapeVector();
        return fleeTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (fleeTarget == null) return false;
        if (entity.getTarget() != null) return false;

        double dist = entity.position().distanceTo(fleeTarget);
        return dist < 2.0; // Close enough to escape point
    }

    @Override
    public void start() {
        if (fleeTarget != null) {
            entity.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
        }
        cooldown = COOLDOWN_TICKS;
    }

    @Override
    public void stop() {
        fleeTarget = null;
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (fleeTarget == null) return;
        entity.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
    }

    private Vec3 findEscapeVector() {
        // Check for nearby fire
        AABB fireBox = entity.getBoundingBox().inflate(FIRE_CHECK_RADIUS);
        List<Entity> nearby = entity.level().getEntitiesOfClass(Entity.class, fireBox);

        for (Entity e : nearby) {
            // Check if it's fire-related block or entity
            if (isFireDanger(e)) {
                return calculateEscapeDirection(e.position());
            }
        }

        // Check for explosions (recently damaged by explosion or near one)
        if (entity.getLastDamageSource() != null &&
                entity.getLastDamageSource().getMsgId().equals("explosion")) {
            // Flee in random direction away from explosion
            return entity.position().add(
                    entity.getRandom().nextDouble() * 10 - 5,
                    0,
                    entity.getRandom().nextDouble() * 10 - 5
            );
        }

        // Check for nearby explosion sources (TNT, creepers about to explode)
        AABB explosionBox = entity.getBoundingBox().inflate(EXPLOSION_CHECK_RADIUS);
        List<Entity> explosiveEntities = entity.level().getEntitiesOfClass(Entity.class, explosionBox);

        for (Entity e : explosiveEntities) {
            if (isExplosiveThreat(e)) {
                return calculateEscapeDirection(e.position());
            }
        }

        return null;
    }

    private boolean isFireDanger(Entity e) {
        // Check if entity is on fire or is fire block
        if (e.isOnFire()) return true;
        // Check if this is a fire-related block (handled by block state check if needed)
        return false;
    }

    private boolean isExplosiveThreat(Entity e) {
        // Primed TNT, charging creeper
        return e instanceof net.minecraft.world.entity.item.PrimedTnt ||
               (e instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.isIgnited());
    }

    private Vec3 calculateEscapeDirection(Vec3 dangerSource) {
        Vec3 entityPos = entity.position();
        Vec3 away = entityPos.subtract(dangerSource).normalize();

        // Add some randomness so they don't all flee in exact same line
        away = away.add(
                entity.getRandom().nextDouble() - 0.5,
                0,
                entity.getRandom().nextDouble() - 0.5
        ).normalize();

        return entityPos.add(away.scale(8.0));
    }
}