package com.edcl.lovelyfriend.client;

import com.edcl.lovelyfriend.LovelyFriendMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

public class ModEntityModelLayers {
    public static final ModelLayerLocation FRIEND = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(LovelyFriendMod.MOD_ID, "friend"),
            "main"
    );
}
