# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the mod JAR
./gradlew build

# Run the dev client (launches Minecraft with the mod loaded)
./gradlew runClient

# Run the dev server
./gradlew runServer
```

Output JAR: `build/libs/lovelyfriend-<version>.jar`

## Stack

- Minecraft **26.2** — fully unobfuscated (Mojang official names, no mappings needed)
- Fabric Loader 0.19.3 · Fabric API 0.153.0+26.2 · Fabric Loom 1.17-SNAPSHOT
- Java 25, Gradle 9.5.1

## Architecture

**Split source sets** (configured in `build.gradle` via `loom.splitEnvironmentSourceSets()`):
- `src/main/` — server + common logic
- `src/client/` — client-only rendering code

**Registration flow** (`LovelyFriendMod.onInitialize`):
1. `ModEntityTypes.register()` — registers the entity type, spawn placements, biome spawn rules, and attributes via `FabricDefaultAttributeRegistry`
2. `ModItems.register()` — registers `FRIENDSHIP_STONE` (`SpawnEggItem`) and adds it to the Spawn Eggs creative tab

**Client init** (`LovelyFriendModClient.onInitializeClient`):
- Registers the model layer (`ModEntityModelLayers.FRIEND`) using `HumanoidModel.createMesh`
- Registers `FriendRenderer` for the entity type

## Key Patterns for MC 26.2

- **Persistence**: Use `ValueOutput`/`ValueInput` (not `CompoundTag`/`NbtCompound`) for `addAdditionalSaveData`/`readAdditionalSaveData`. See `FriendEntity` for the inventory serialization pattern with `childrenList`/`childrenListOrEmpty`.
- **Equippable detection**: Use `DataComponents.EQUIPPABLE` to get `Equippable` and call `.slot()` — not `ArmorItem` instanceof checks.
- **Armor comparison**: Use `ItemAttributeModifiers.compute(Attributes.ARMOR, 0.0, slot)` retrieved via `DataComponents.ATTRIBUTE_MODIFIERS`.
- **Food detection**: `stack.get(DataComponents.FOOD)` returns `FoodProperties` (null if not food).
- **Render state pattern**: `FriendEntityRenderState extends LivingEntityRenderState`; data flows server→client via `extractRenderState()` override in `FriendRenderer`.
- **Dependency scope**: Use `implementation` (not `modImplementation`) for Fabric Loader and Fabric API in this project.

## FriendEntity Overview

`PathfinderMob` with a 100-slot `SimpleContainer` inventory. On `pickUpItem`, the priority is: shield (offhand) → armor (per slot, compare defense) → weapon (main hand, compare attack damage) → stash in inventory. Weapon scoring uses `ItemAttributeModifiers.compute` rather than material tier enums.

Hunger ticks down in `aiStep()` every 600 ticks; `EatFoodGoal` fires when `isHungry() && hasFoodInInventory()`. Starvation damage fires every 50 ticks when `foodLevel < 5`.

Texture is selected randomly from `TEXTURES` list on `finalizeSpawn`, stored in NBT as `SelectedTexture`, and forwarded to the renderer via `FriendEntityRenderState.selectedTexture`. The stored value is a path relative to `textures/` (e.g. `entity/female/1`, `entity/hololive/ayame`). Skin PNGs live in `src/main/resources/assets/lovelyfriend/textures/entity/{female,hololive,myth,promise}/`. To add new skins: drop the PNG in the right subfolder and add the path string to `TEXTURES` in `FriendEntity.java`.
