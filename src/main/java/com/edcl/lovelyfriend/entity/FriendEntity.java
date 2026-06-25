package com.edcl.lovelyfriend.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class FriendEntity extends PathfinderMob {

    public FriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createFriendAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.ARMOR_TOUGHNESS, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1)
                .add(Attributes.ATTACK_SPEED, 2.0);
    }
}
