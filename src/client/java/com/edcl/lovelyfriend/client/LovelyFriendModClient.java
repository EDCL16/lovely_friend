package com.edcl.lovelyfriend.client;

import com.edcl.lovelyfriend.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.PlayerModel;

public class LovelyFriendModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(
                ModEntityModelLayers.FRIEND,
                PlayerModel::createMesh
        );
        EntityRendererRegistry.register(ModEntityTypes.FRIEND, FriendRenderer::new);
    }
}
