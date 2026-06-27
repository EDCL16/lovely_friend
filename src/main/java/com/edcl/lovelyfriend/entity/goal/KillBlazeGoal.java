package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Blaze;

public class KillBlazeGoal extends NearestAttackableTargetGoal<Blaze> {

    private final FriendEntity friend;

    public KillBlazeGoal(FriendEntity entity) {
        super(entity, Blaze.class, true);
        this.friend = entity;
    }

    @Override
    public boolean canUse() {
        return friend.getGameStage() == GameStage.NETHER && super.canUse();
    }
}
