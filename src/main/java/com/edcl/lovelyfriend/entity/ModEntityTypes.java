package com.edcl.lovelyfriend.entity;

import com.edcl.lovelyfriend.LovelyFriendMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

public class ModEntityTypes {

    public static final EntityType<FriendEntity> FRIEND = register(
            "friend",
            EntityType.Builder.<FriendEntity>of(FriendEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
    );

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(LovelyFriendMod.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(FRIEND, FriendEntity.createFriendAttributes());

        SpawnPlacements.register(
                FRIEND,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FriendEntity::canSpawn
        );

        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                MobCategory.CREATURE,
                FRIEND,
                200,
                1,
                7
        );

        LovelyFriendMod.LOGGER.info("Registered entity types for " + LovelyFriendMod.MOD_ID);
    }
}
