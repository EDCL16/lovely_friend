# Lovely Friend Mod — Design Spec

**Date:** 2026-06-25
**Target:** Minecraft Java Edition 26.2 · Fabric · Java 25

## Overview

A Fabric mod that adds a humanoid NPC companion entity ("FriendEntity") that spawns near villages, fights hostile mobs and animals, manages its own inventory/equipment, and has a hunger system. On death it drops a "Friendship Stone" spawn egg.

## Technical Stack

| Component | Version |
|-----------|---------|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric Loom | 1.17-SNAPSHOT |
| Fabric API | 0.153.0+26.2 |
| Gradle | 9.5.1 |
| Java | 25 |

- No mappings needed (MC 26.1+ is unobfuscated, Mojang official names)
- `implementation` instead of `modImplementation`
- Split source sets: `src/main/` (common) and `src/client/` (client-only)

## Project Structure

```
lovely_friend/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── src/
│   ├── main/
│   │   ├── java/com/edcl/lovelyfriend/
│   │   │   ├── LovelyFriendMod.java              # ModInitializer
│   │   │   ├── entity/
│   │   │   │   ├── FriendEntity.java              # PathfinderMob
│   │   │   │   ├── ModEntityTypes.java            # EntityType registration
│   │   │   │   └── goal/
│   │   │   │       ├── CollectItemOnGroundGoal.java
│   │   │   │       ├── EatFoodGoal.java
│   │   │   │       └── FollowPlayerGoal.java
│   │   │   └── item/
│   │   │       └── ModItems.java                  # SpawnEggItem registration
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       └── assets/lovelyfriend/
│   │           ├── lang/en_us.json
│   │           ├── models/item/friendship_stone.json
│   │           ├── items/friendship_stone.json
│   │           └── textures/
│   │               ├── item/friendship_stone.png
│   │               └── entity/friend/
│   │                   └── (50+ player skin textures)
│   └── client/
│       └── java/com/edcl/lovelyfriend/client/
│           ├── LovelyFriendModClient.java         # ClientModInitializer
│           ├── FriendEntityRenderState.java       # RenderState (26.2 pattern)
│           ├── FriendModel.java                   # PlayerModel-based
│           ├── FriendRenderer.java                # MobRenderer + ArmorFeatureRenderer
│           └── ModEntityModelLayers.java          # ModelLayerLocation
```

## Entity: FriendEntity

### Inheritance

`PathfinderMob` + implements `InventoryCarrier`

### Attributes

| Attribute | Value |
|-----------|-------|
| MAX_HEALTH | 20 |
| MOVEMENT_SPEED | 0.5 |
| ATTACK_DAMAGE | 3 |
| ARMOR | 2.0 |
| ARMOR_TOUGHNESS | 0.25 |
| KNOCKBACK_RESISTANCE | 0.1 |
| ATTACK_SPEED | 2 |

### AI Goals (goalSelector)

| Priority | Goal | Details |
|----------|------|---------|
| 1 | `FloatGoal` | Swim |
| 1 | `EatFoodGoal` (custom) | Eat food from inventory when hungry, 20 tick cooldown |
| 1 | `MeleeAttackGoal` | Melee attack with speed 1.0 |
| 2 | `MoveTowardsTargetGoal` | Move toward attack target |
| 3 | `CollectItemOnGroundGoal` (custom) | 15-block radius, pick up items |
| 4 | `RandomStrollGoal` | Wander around |
| 5 | `StrollThroughVillageGoal` | Walk through villages, 20-block range |
| 7 | `FollowPlayerGoal` (custom) | 8% chance to start, 1-3 block distance |
| 8 | `LookAtPlayerGoal` × 2 | Look at players and other FriendEntities |
| 8 | `RandomLookAroundGoal` | Random head movement |

### AI Goals (targetSelector)

| Priority | Goal | Details |
|----------|------|---------|
| 2 | `HurtByTargetGoal` | Retaliate against anything that hits it (including players) |
| 3 | `NearestAttackableTargetGoal` → `Monster` | Attack hostile mobs (exclude Creeper) |
| 3 | `NearestAttackableTargetGoal` → `Animal` | Attack all animals |

## Inventory & Equipment System

### Inventory
- `SimpleContainer(100)` — 100-slot inventory
- Items auto-stack when picked up
- All contents drop on death

### Weapon Selection
- Auto-equip best weapon to main hand on pickup
- Material priority: Netherite (6) > Diamond (5) > Iron (4) > Gold (3) > Stone (2) > Wood (1)
- Type priority: Sword > Axe

### Armor System
- Auto-equip best armor per slot (head/chest/legs/feet) on pickup
- Compare by defense value
- **Armor IS rendered** via `ArmorFeatureRenderer` on the renderer

### Shield
- Auto-equip shield to off-hand slot when picked up

## Hunger System

| Parameter | Value |
|-----------|-------|
| `foodLevel` range | 0–20 (initial: 20) |
| Hunger tick interval | 600 ticks (30 seconds) per -1 |
| Damage threshold | foodLevel < 5 |
| Damage amount | 0.5 per 50 ticks (2.5 seconds) |
| EatFoodGoal cooldown | 20 ticks |

- `EatFoodGoal` searches inventory for edible items, consumes them, restores food value
- Hunger ticks down in `aiStep()`

## Friendship Stone (Spawn Egg)

- Registered as `SpawnEggItem` with `Item.Properties().spawnEgg(ModEntityTypes.FRIEND)`
- Added to `CreativeModeTabs.SPAWN_EGGS` via `CreativeModeTabEvents`
- FriendEntity always drops one Friendship Stone on death (in addition to inventory)

## Spawn Rules

- Spawns in all Overworld biomes
- Spawn weight: 200
- Group size: 1–7
- Additional constraint: `canSpawn()` checks within 5 blocks of a village structure

## Rendering

### FriendRenderer
- Extends `MobRenderer<FriendEntity, FriendEntityRenderState, FriendModel>`
- Feature renderers:
  - `ArmorFeatureRenderer` — renders worn armor visually
  - `HeldItemFeatureRenderer` — renders main hand weapon + off-hand shield
- Shadow size: 0.5f
- Baby entities scaled to 0.5x

### FriendModel
- Based on `PlayerModel` (humanoid structure, 64×64 skin format)
- Standard player body parts: head, body, arms, legs

### FriendEntityRenderState
- Extends `LivingEntityRenderState`
- Passes texture selection from server entity to client renderer

### Texture System
- 50+ player skin textures bundled in `textures/entity/friend/`
- Entity randomly selects one on spawn
- Selection stored in NBT (`selectedTexture`) for persistence

## Persistence

- `addAdditionalSaveData()` / `readAdditionalSaveData()` save/load:
  - `selectedTexture` (skin name)
  - `foodLevel` (hunger)
  - Inventory contents
- Entity has `PersistenceRequired` flag set
