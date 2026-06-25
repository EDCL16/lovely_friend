package com.edcl.lovelyfriend.item;

import com.edcl.lovelyfriend.LovelyFriendMod;
import com.edcl.lovelyfriend.entity.ModEntityTypes;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.function.Function;

public class ModItems {

    public static final Item FRIENDSHIP_STONE = register(
            "friendship_stone",
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntityTypes.FRIEND)
    );

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(LovelyFriendMod.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(creativeTab -> creativeTab.accept(FRIENDSHIP_STONE));
        LovelyFriendMod.LOGGER.info("Registered items for " + LovelyFriendMod.MOD_ID);
    }
}
