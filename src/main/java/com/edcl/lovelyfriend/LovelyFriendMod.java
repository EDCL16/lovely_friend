package com.edcl.lovelyfriend;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LovelyFriendMod implements ModInitializer {
    public static final String MOD_ID = "lovelyfriend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Lovely Friend mod initializing");
    }
}
