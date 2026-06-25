package com.edcl.lovelyfriend.client;

import com.edcl.lovelyfriend.LovelyFriendMod;
import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.resources.Identifier;

public class FriendRenderer extends MobRenderer<FriendEntity, FriendEntityRenderState, FriendModel> {

    public FriendRenderer(EntityRendererProvider.Context context) {
        super(context, new FriendModel(context.bakeLayer(ModEntityModelLayers.FRIEND)), 0.5f);
        ArmorModelSet<HumanoidModel<FriendEntityRenderState>> armorModels = ArmorModelSet.bake(
                ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new
        );
        this.addLayer(new HumanoidArmorLayer<>(this, armorModels, context.getEquipmentRenderer()));
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public FriendEntityRenderState createRenderState() {
        return new FriendEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(FriendEntityRenderState state) {
        return Identifier.fromNamespaceAndPath(
                LovelyFriendMod.MOD_ID,
                "textures/entity/friend/" + state.selectedTexture + ".png"
        );
    }

    @Override
    public void extractRenderState(FriendEntity entity, FriendEntityRenderState state, float tickProgress) {
        super.extractRenderState(entity, state, tickProgress);
        state.selectedTexture = entity.getSelectedTexture();
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, tickProgress);
    }
}
