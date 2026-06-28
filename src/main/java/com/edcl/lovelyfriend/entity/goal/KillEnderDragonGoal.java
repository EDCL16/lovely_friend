package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public class KillEnderDragonGoal extends NearestAttackableTargetGoal<EnderDragon> {

    private final FriendEntity friend;

    public KillEnderDragonGoal(FriendEntity entity) {
        super(entity, EnderDragon.class, true);
        this.friend = entity;
    }

    @Override
    public boolean canUse() {
        return friend.getGameStage() == GameStage.END && super.canUse();
    }
}
