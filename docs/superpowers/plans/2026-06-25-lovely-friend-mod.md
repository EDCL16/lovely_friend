# Lovely Friend Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric mod for Minecraft 26.2 that adds a humanoid NPC companion ("FriendEntity") with AI, inventory, equipment, hunger, and a spawn egg item.

**Architecture:** Single-entity mod. FriendEntity extends PathfinderMob with vanilla Goal-based AI, a SimpleContainer inventory, and auto-equipment logic. Client rendering uses PlayerModel with ArmorFeatureRenderer and HeldItemFeatureRenderer. Split source sets: `src/main/` for server/common code, `src/client/` for rendering.

**Tech Stack:** Minecraft 26.2, Fabric Loader 0.19.3, Fabric Loom 1.17-SNAPSHOT, Fabric API 0.153.0+26.2, Java 25, Gradle 9.5.1

## Global Constraints

- Java 25 source/target compatibility
- Mojang official class names (no Yarn — MC 26.1+ is unobfuscated)
- `implementation` instead of `modImplementation` in build.gradle
- No mappings line in dependencies
- Fabric Loom plugin ID: `net.fabricmc.fabric-loom`
- Split source sets: `src/main/java/` (common) + `src/client/java/` (client)
- Mod ID: `lovelyfriend`
- Package: `com.edcl.lovelyfriend`
- No external dependencies beyond Fabric API

## Testing Note

Minecraft mods cannot be unit-tested with standard frameworks. Each task's verification is: run `./gradlew runClient`, launch a creative world, and verify behavior in-game. Steps that say "Verify" mean in-game verification.

---

### Task 1: Project Scaffold & Build Configuration

**Files:**
- Create: `gradle.properties`
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/com/edcl/lovelyfriend/LovelyFriendMod.java`
- Create: `src/client/java/com/edcl/lovelyfriend/client/LovelyFriendModClient.java`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: `LovelyFriendMod.MOD_ID` (`"lovelyfriend"`), empty `onInitialize()` and `onInitializeClient()` hooks

- [ ] **Step 1: Create `gradle.properties`**

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=false

# Fabric Properties
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT

# Mod Properties
mod_version=1.0.0
maven_group=com.edcl

# Dependencies
fabric_api_version=0.153.0+26.2
```

- [ ] **Step 2: Create `settings.gradle`**

```gradle
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'lovelyfriend'
```

- [ ] **Step 3: Create `build.gradle`**

```gradle
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

version = project.mod_version
group = project.maven_group

loom {
    splitEnvironmentSourceSets()

    mods {
        "lovelyfriend" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

processResources {
    def version = project.version
    inputs.property "version", version

    filesMatching("fabric.mod.json") {
        expand "version": version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

jar {
    def projectName = project.name
    inputs.property "projectName", projectName

    from("LICENSE") {
        rename { "${it}_${projectName}" }
    }
}
```

- [ ] **Step 4: Create `src/main/resources/fabric.mod.json`**

```json
{
    "schemaVersion": 1,
    "id": "lovelyfriend",
    "version": "${version}",
    "name": "Lovely Friend",
    "description": "Adds friendly humanoid NPC companions that spawn near villages",
    "authors": ["EDCL"],
    "license": "MIT",
    "icon": "assets/lovelyfriend/icon.png",
    "environment": "*",
    "entrypoints": {
        "main": ["com.edcl.lovelyfriend.LovelyFriendMod"],
        "client": ["com.edcl.lovelyfriend.client.LovelyFriendModClient"]
    },
    "depends": {
        "fabricloader": ">=0.19.3",
        "minecraft": "~26.2",
        "java": ">=25",
        "fabric-api": "*"
    }
}
```

- [ ] **Step 5: Create `LovelyFriendMod.java`**

```java
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
```

- [ ] **Step 6: Create `LovelyFriendModClient.java`**

```java
package com.edcl.lovelyfriend.client;

import net.fabricmc.api.ClientModInitializer;

public class LovelyFriendModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
    }
}
```

- [ ] **Step 7: Create Gradle wrapper**

Run: `gradle wrapper --gradle-version 9.5.1`

If Gradle is not installed globally, download the wrapper files from the Fabric example mod template at https://fabricmc.net/develop/template/

- [ ] **Step 8: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add gradle.properties settings.gradle build.gradle src/ gradlew gradlew.bat gradle/
git commit -m "feat: project scaffold with Fabric 26.2 build configuration"
```

---

### Task 2: Entity Registration & Spawn Egg Item

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java` (skeleton)
- Create: `src/main/java/com/edcl/lovelyfriend/entity/ModEntityTypes.java`
- Create: `src/main/java/com/edcl/lovelyfriend/item/ModItems.java`
- Modify: `src/main/java/com/edcl/lovelyfriend/LovelyFriendMod.java`
- Create: `src/main/resources/assets/lovelyfriend/lang/en_us.json`
- Create: `src/main/resources/assets/lovelyfriend/items/friendship_stone.json`
- Create: `src/main/resources/assets/lovelyfriend/models/item/friendship_stone.json`

**Interfaces:**
- Consumes: `LovelyFriendMod.MOD_ID` from Task 1
- Produces: `ModEntityTypes.FRIEND` (EntityType&lt;FriendEntity&gt;), `ModItems.FRIENDSHIP_STONE` (Item), `FriendEntity.createFriendAttributes()` (AttributeSupplier.Builder)

- [ ] **Step 1: Create skeleton `FriendEntity.java`**

A minimal PathfinderMob with attributes defined. No AI goals yet — just enough to register and spawn.

```java
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
```

- [ ] **Step 2: Create `ModEntityTypes.java`**

```java
package com.edcl.lovelyfriend.entity;

import com.edcl.lovelyfriend.LovelyFriendMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

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
        LovelyFriendMod.LOGGER.info("Registered entity types for " + LovelyFriendMod.MOD_ID);
    }
}
```

- [ ] **Step 3: Create `ModItems.java`**

```java
package com.edcl.lovelyfriend.item;

import com.edcl.lovelyfriend.LovelyFriendMod;
import com.edcl.lovelyfriend.entity.ModEntityTypes;
import net.fabricmc.fabric.api.itemgroup.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class ModItems {

    public static final Item FRIENDSHIP_STONE = register(
            "friendship_stone",
            SpawnEggItem::new,
            new Item.Properties().spawnEgg(ModEntityTypes.FRIEND)
    );

    private static Item register(String name, Item.Factory factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(LovelyFriendMod.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.ITEM, key, factory.create(properties.setId(key)));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(creativeTab -> creativeTab.accept(FRIENDSHIP_STONE));
        LovelyFriendMod.LOGGER.info("Registered items for " + LovelyFriendMod.MOD_ID);
    }
}
```

- [ ] **Step 4: Wire registrations into `LovelyFriendMod.onInitialize()`**

Replace the `onInitialize()` body:

```java
@Override
public void onInitialize() {
    ModEntityTypes.register();
    ModItems.register();
    LOGGER.info("Lovely Friend mod initialized");
}
```

Add imports:
```java
import com.edcl.lovelyfriend.entity.ModEntityTypes;
import com.edcl.lovelyfriend.item.ModItems;
```

- [ ] **Step 5: Create resource files**

`src/main/resources/assets/lovelyfriend/lang/en_us.json`:
```json
{
    "entity.lovelyfriend.friend": "Friend",
    "item.lovelyfriend.friendship_stone": "Friendship Stone"
}
```

`src/main/resources/assets/lovelyfriend/items/friendship_stone.json`:
```json
{
    "model": {
        "type": "minecraft:model",
        "model": "lovelyfriend:item/friendship_stone"
    }
}
```

`src/main/resources/assets/lovelyfriend/models/item/friendship_stone.json`:
```json
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "lovelyfriend:item/friendship_stone"
    }
}
```

Also create a placeholder 16×16 PNG texture at `src/main/resources/assets/lovelyfriend/textures/item/friendship_stone.png`. Use any simple white square as placeholder.

- [ ] **Step 6: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/ src/main/resources/
git commit -m "feat: register FriendEntity type and Friendship Stone spawn egg"
```

---

### Task 3: Client-Side Rendering

**Files:**
- Create: `src/client/java/com/edcl/lovelyfriend/client/ModEntityModelLayers.java`
- Create: `src/client/java/com/edcl/lovelyfriend/client/FriendModel.java`
- Create: `src/client/java/com/edcl/lovelyfriend/client/FriendEntityRenderState.java`
- Create: `src/client/java/com/edcl/lovelyfriend/client/FriendRenderer.java`
- Modify: `src/client/java/com/edcl/lovelyfriend/client/LovelyFriendModClient.java`

**Interfaces:**
- Consumes: `ModEntityTypes.FRIEND` from Task 2
- Produces: Fully rendered humanoid entity with armor and held items visible in-game

- [ ] **Step 1: Create `ModEntityModelLayers.java`**

```java
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
```

- [ ] **Step 2: Create `FriendModel.java`**

Uses `PlayerModel` which provides the full humanoid body structure (head, body, arms, legs) in 64×64 skin format.

```java
package com.edcl.lovelyfriend.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

public class FriendModel extends PlayerModel<FriendEntityRenderState> {

    public FriendModel(ModelPart root) {
        super(root);
    }
}
```

- [ ] **Step 3: Create `FriendEntityRenderState.java`**

```java
package com.edcl.lovelyfriend.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class FriendEntityRenderState extends LivingEntityRenderState {
    public String selectedTexture = "default";
}
```

- [ ] **Step 4: Create `FriendRenderer.java`**

Includes `ArmorFeatureRenderer` for visible armor and `HeldItemFeatureRenderer` for weapon/shield display.

```java
package com.edcl.lovelyfriend.client;

import com.edcl.lovelyfriend.LovelyFriendMod;
import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;

public class FriendRenderer extends MobRenderer<FriendEntity, FriendEntityRenderState, FriendModel> {

    private static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(
            LovelyFriendMod.MOD_ID, "textures/entity/friend/default.png"
    );

    public FriendRenderer(EntityRendererProvider.Context context) {
        super(context, new FriendModel(context.bakeLayer(ModEntityModelLayers.FRIEND)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this, context.getModelSet()));
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
    }
}
```

Note: `entity.getSelectedTexture()` will be added to FriendEntity in Task 4. For now, add a temporary stub to FriendEntity:

```java
public String getSelectedTexture() {
    return "default";
}
```

- [ ] **Step 5: Wire client registration in `LovelyFriendModClient.java`**

```java
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
```

- [ ] **Step 6: Create a default placeholder texture**

Create a 64×64 PNG player skin texture at:
`src/main/resources/assets/lovelyfriend/textures/entity/friend/default.png`

Use the Steve skin or a simple colored player skin as placeholder.

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Verify in-game**

Run: `./gradlew runClient`
1. Create a creative world
2. Find the Friendship Stone in the Spawn Eggs creative tab
3. Use it to spawn a FriendEntity
4. Verify: entity appears with humanoid player model, stands idle

- [ ] **Step 9: Commit**

```bash
git add src/client/ src/main/resources/assets/lovelyfriend/textures/
git commit -m "feat: client-side rendering with PlayerModel, armor, and held items"
```

---

### Task 4: FriendEntity Core — AI Goals, Hunger, Inventory, Textures

**Files:**
- Modify: `src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java` (full implementation)
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/CollectItemOnGroundGoal.java`
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/EatFoodGoal.java`
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/FollowPlayerGoal.java`

**Interfaces:**
- Consumes: `ModEntityTypes.FRIEND` from Task 2, `ModItems.FRIENDSHIP_STONE` from Task 2
- Produces: Fully functional FriendEntity with all 10 behaviors from the spec

- [ ] **Step 1: Create `CollectItemOnGroundGoal.java`**

Searches for dropped items within 15 blocks, navigates to them, triggers entity pickup.

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class CollectItemOnGroundGoal extends Goal {

    private static final double SEARCH_RADIUS = 15.0;
    private static final double PICKUP_RANGE = 1.5;

    private final FriendEntity entity;
    private ItemEntity targetItem;

    public CollectItemOnGroundGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        AABB searchBox = entity.getBoundingBox().inflate(SEARCH_RADIUS);
        List<ItemEntity> items = entity.level().getEntitiesOfClass(ItemEntity.class, searchBox);
        if (items.isEmpty()) return false;
        targetItem = items.stream()
                .min(Comparator.comparingDouble(entity::distanceToSqr))
                .orElse(null);
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null && targetItem.isAlive();
    }

    @Override
    public void tick() {
        if (targetItem == null) return;
        entity.getNavigation().moveTo(targetItem, 1.0);
        if (entity.distanceToSqr(targetItem) < PICKUP_RANGE * PICKUP_RANGE) {
            entity.pickUpItem(targetItem);
            targetItem = null;
        }
    }

    @Override
    public void stop() {
        targetItem = null;
    }
}
```

- [ ] **Step 2: Create `EatFoodGoal.java`**

When hungry, searches inventory for edible items and eats them.

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class EatFoodGoal extends Goal {

    private final FriendEntity entity;
    private int cooldown;

    public EatFoodGoal(FriendEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return entity.isHungry() && entity.hasFoodInInventory();
    }

    @Override
    public void start() {
        ItemStack food = entity.findFoodInInventory();
        if (!food.isEmpty()) {
            entity.eatFood(food);
            cooldown = 20;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
```

- [ ] **Step 3: Create `FollowPlayerGoal.java`**

8% chance per tick to follow the nearest player, maintaining 1-3 block distance.

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {

    private final FriendEntity entity;
    private final double speed;
    private final double maxFollowRange;
    private final double minFollowRange;
    private Player target;

    public FollowPlayerGoal(FriendEntity entity, double speed, double maxRange, double minRange) {
        this.entity = entity;
        this.speed = speed;
        this.maxFollowRange = maxRange;
        this.minFollowRange = minRange;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.getRandom().nextFloat() > 0.08f) return false;
        target = entity.level().getNearestPlayer(entity, maxFollowRange);
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        double dist = entity.distanceToSqr(target);
        return dist < maxFollowRange * maxFollowRange;
    }

    @Override
    public void tick() {
        if (target == null) return;
        entity.getLookControl().setLookAt(target);
        double dist = entity.distanceToSqr(target);
        if (dist > minFollowRange * minFollowRange) {
            entity.getNavigation().moveTo(target, speed);
        } else {
            entity.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        target = null;
        entity.getNavigation().stop();
    }
}
```

- [ ] **Step 4: Implement full `FriendEntity.java`**

Replace the skeleton with the complete implementation including: AI goals, hunger system, inventory, equipment auto-selection, item pickup, death drops, texture selection, and NBT persistence.

```java
package com.edcl.lovelyfriend.entity;

import com.edcl.lovelyfriend.entity.goal.CollectItemOnGroundGoal;
import com.edcl.lovelyfriend.entity.goal.EatFoodGoal;
import com.edcl.lovelyfriend.entity.goal.FollowPlayerGoal;
import com.edcl.lovelyfriend.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class FriendEntity extends PathfinderMob {

    private static final int MAX_FOOD_LEVEL = 20;
    private static final int HUNGER_DAMAGE_THRESHOLD = 5;
    private static final int DROP_FOOD_LEVEL_TICKS = 600;
    private static final int HUNGER_DAMAGE_TICKS = 50;

    private final SimpleContainer inventory = new SimpleContainer(100);
    private int foodLevel = MAX_FOOD_LEVEL;
    private int foodTickTimer = 0;
    private int hungerDamageTimer = 0;
    private String selectedTexture = "default";

    private static final List<String> TEXTURES = Arrays.asList(
            "default", "steve", "alex",
            "female1", "female2", "female3", "female4", "female5",
            "male1", "male2"
    );

    public FriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired(true);
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

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatFoodGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0, 32.0f));
        this.goalSelector.addGoal(3, new CollectItemOnGroundGoal(this));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(5, new StrollThroughVillageGoal(this, 20));
        this.goalSelector.addGoal(7, new FollowPlayerGoal(this, 0.5, 20.0, 3.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, FriendEntity.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                entity -> !(entity instanceof Creeper)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, true));
    }

    // ---- Hunger System ----

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            reduceHunger();
            applyHungerDamage();
        }
    }

    private void reduceHunger() {
        foodTickTimer++;
        if (foodTickTimer >= DROP_FOOD_LEVEL_TICKS) {
            foodTickTimer = 0;
            if (foodLevel > 0) {
                foodLevel--;
            }
        }
    }

    private void applyHungerDamage() {
        if (foodLevel < HUNGER_DAMAGE_THRESHOLD) {
            hungerDamageTimer++;
            if (hungerDamageTimer >= HUNGER_DAMAGE_TICKS) {
                hungerDamageTimer = 0;
                this.hurt(this.damageSources().starve(), 0.5f);
            }
        } else {
            hungerDamageTimer = 0;
        }
    }

    public boolean isHungry() {
        return foodLevel < MAX_FOOD_LEVEL;
    }

    public boolean hasFoodInInventory() {
        return !findFoodInInventory().isEmpty();
    }

    public ItemStack findFoodInInventory() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            FoodProperties food = stack.getFoodProperties(this);
            if (food != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void eatFood(ItemStack food) {
        FoodProperties props = food.getFoodProperties(this);
        if (props != null) {
            foodLevel = Math.min(MAX_FOOD_LEVEL, foodLevel + props.nutrition());
            food.shrink(1);
        }
    }

    // ---- Inventory & Equipment ----

    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    public void pickUpItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();

        if (tryEquipShield(stack)) {
            itemEntity.discard();
            return;
        }
        if (tryEquipArmor(stack)) {
            itemEntity.discard();
            return;
        }
        if (tryEquipWeapon(stack)) {
            // weapon equipped, but still add old weapon to inventory if swapped
        }

        ItemStack remaining = addToInventory(stack);
        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
    }

    private ItemStack addToInventory(ItemStack stack) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty()) {
                inventory.setItem(i, stack.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) &&
                    existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int toAdd = Math.min(space, stack.getCount());
                existing.grow(toAdd);
                stack.shrink(toAdd);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private boolean tryEquipShield(ItemStack stack) {
        if (stack.getItem() instanceof ShieldItem) {
            ItemStack current = this.getOffhandItem();
            if (current.isEmpty()) {
                this.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());
                return true;
            }
        }
        return false;
    }

    private boolean tryEquipArmor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            EquipmentSlot slot = armorItem.getEquipmentSlot();
            ItemStack current = this.getItemBySlot(slot);
            if (current.isEmpty() || isBetterArmor(stack, current)) {
                if (!current.isEmpty()) {
                    addToInventory(current);
                }
                this.setItemSlot(slot, stack.copy());
                return true;
            }
        }
        return false;
    }

    private boolean isBetterArmor(ItemStack newItem, ItemStack oldItem) {
        if (!(newItem.getItem() instanceof ArmorItem newArmor)) return false;
        if (!(oldItem.getItem() instanceof ArmorItem oldArmor)) return true;
        return newArmor.getDefense() > oldArmor.getDefense();
    }

    private boolean tryEquipWeapon(ItemStack stack) {
        if (isWeapon(stack)) {
            ItemStack current = this.getMainHandItem();
            if (current.isEmpty() || isBetterWeapon(stack, current)) {
                if (!current.isEmpty()) {
                    addToInventory(current);
                }
                this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                return true;
            }
        }
        return false;
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    private boolean isBetterWeapon(ItemStack newItem, ItemStack oldItem) {
        return getWeaponScore(newItem) > getWeaponScore(oldItem);
    }

    private int getWeaponScore(ItemStack stack) {
        int materialScore = 0;
        int typeScore = 0;

        if (stack.getItem() instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            materialScore = (int) (tier.getSpeed() * 10);
        }
        if (stack.getItem() instanceof SwordItem) typeScore = 2;
        else if (stack.getItem() instanceof AxeItem) typeScore = 1;

        return materialScore * 10 + typeScore;
    }

    // ---- Death Drops ----

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        // Drop inventory contents
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(level, stack);
            }
        }
        // Drop equipped items
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = this.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                this.spawnAtLocation(level, equipped);
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        // Drop a Friendship Stone
        this.spawnAtLocation(level, new ItemStack(ModItems.FRIENDSHIP_STONE));
        super.dropAllDeathLoot(level, damageSource);
    }

    // ---- Texture System ----

    public String getSelectedTexture() {
        return selectedTexture;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
        selectedTexture = TEXTURES.get(this.random.nextInt(TEXTURES.size()));
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    // ---- Spawn Rules ----

    public static boolean canSpawn(EntityType<FriendEntity> type, ServerLevelAccessor level,
                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        return serverLevel.isVillage(pos);
    }

    // ---- Persistence (NBT) ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SelectedTexture", selectedTexture);
        tag.putInt("FoodLevel", foodLevel);

        ListTag inventoryTag = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                inventoryTag.add(stack.save(level().registryAccess(), itemTag));
            }
        }
        tag.put("Inventory", inventoryTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SelectedTexture")) {
            selectedTexture = tag.getString("SelectedTexture");
        }
        if (tag.contains("FoodLevel")) {
            foodLevel = tag.getInt("FoodLevel");
        }
        if (tag.contains("Inventory")) {
            ListTag inventoryTag = tag.getList("Inventory", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = inventoryTag.getCompound(i);
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.parse(level().registryAccess(), itemTag).orElse(ItemStack.EMPTY);
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, stack);
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Verify in-game**

Run: `./gradlew runClient`
1. Spawn a FriendEntity with the Friendship Stone
2. Verify: entity walks around, attacks nearby animals and hostile mobs
3. Drop a sword near the entity → verify it picks it up and holds it
4. Drop armor near the entity → verify it picks it up and renders armor visually
5. Drop a shield → verify it appears in off-hand
6. Drop food → verify it picks it up and eats when hungry
7. Kill the entity → verify it drops inventory + a Friendship Stone
8. Verify: entity retaliates when attacked by player

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/
git commit -m "feat: full FriendEntity with AI, hunger, inventory, equipment, and death drops"
```

---

### Task 5: Spawn Rules & Biome Configuration

**Files:**
- Create: `src/main/resources/data/lovelyfriend/fabric/biome_modifier/friend_spawn.json` (if Fabric supports biome modifiers) OR register spawn placement in code
- Modify: `src/main/java/com/edcl/lovelyfriend/entity/ModEntityTypes.java`

**Interfaces:**
- Consumes: `ModEntityTypes.FRIEND`, `FriendEntity.canSpawn()` from Task 4
- Produces: FriendEntity spawns naturally near villages in all overworld biomes

- [ ] **Step 1: Register spawn placement in `ModEntityTypes.register()`**

Add to the end of the `register()` method:

```java
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

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
            200,  // weight
            1,    // minGroupSize
            7     // maxGroupSize
    );

    LovelyFriendMod.LOGGER.info("Registered entity types for " + LovelyFriendMod.MOD_ID);
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify in-game**

Run: `./gradlew runClient`
1. Create a new survival world
2. Find a village (use `/locate structure minecraft:village_plains`)
3. Walk around the village area
4. Verify: FriendEntities spawn naturally near the village

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/ModEntityTypes.java
git commit -m "feat: register biome spawn rules for villages in all overworld biomes"
```

---

### Task 6: Texture Bundle & Final Resources

**Files:**
- Create: additional skin textures in `src/main/resources/assets/lovelyfriend/textures/entity/friend/`
- Modify: `src/main/resources/assets/lovelyfriend/lang/en_us.json` (if needed)

**Interfaces:**
- Consumes: `FriendEntity.TEXTURES` list from Task 4
- Produces: All referenced texture files exist so entity renders don't fall back to missing texture

- [ ] **Step 1: Create placeholder skin textures**

For each texture name in the `TEXTURES` list (`default`, `steve`, `alex`, `female1`–`female5`, `male1`, `male2`), create a 64×64 PNG player skin file at:

`src/main/resources/assets/lovelyfriend/textures/entity/friend/<name>.png`

These can be copies of the Steve/Alex skin with recoloring, or custom skins. The minimum requirement is that every name in the `TEXTURES` list has a corresponding `.png` file.

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify in-game**

Run: `./gradlew runClient`
1. Spawn multiple FriendEntities
2. Verify: different entities have different skin textures
3. Verify: no purple/black missing texture squares

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/lovelyfriend/textures/
git commit -m "feat: add player skin textures for FriendEntity variants"
```

---

## Post-Implementation Notes

**Adding more skins later:** Add the PNG file to `textures/entity/friend/` and add the name to the `TEXTURES` list in `FriendEntity.java`.

**Known simplifications:**
- Weapon scoring uses `Tier.getSpeed()` as a proxy for material tier ranking. This works for vanilla tiers (wood < stone < iron < diamond < netherite) but modded tiers may not sort correctly.
- Village proximity check uses `ServerLevel.isVillage(pos)` — if this method doesn't exist in 26.2, replace with a structure tag query: `level.structureManager().getStructureWithPieceAt(pos, BuiltInStructures.VILLAGE_PLAINS)` or similar.
- `ItemStack.save()` / `ItemStack.parse()` API may have changed in 26.2. If build fails on NBT serialization, check the current API for item stack codec serialization.
