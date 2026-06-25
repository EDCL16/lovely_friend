package com.edcl.lovelyfriend;

import com.edcl.lovelyfriend.entity.ModEntityTypes;
import com.edcl.lovelyfriend.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LovelyFriendMod implements ModInitializer {
    public static final String MOD_ID = "lovelyfriend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntityTypes.register();
        ModItems.register();
        LOGGER.info("Lovely Friend mod initialized");
    }
}
