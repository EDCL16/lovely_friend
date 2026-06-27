# Friend AI Progression System — Design Spec

**Date:** 2026-06-27
**Status:** Approved

## Problem

The current FriendEntity AI has 23+ flat-priority goals with no game progression awareness. Goals fire opportunistically based on immediate conditions, so the AI may chop trees indefinitely without ever progressing to mining, smelting, or the Ender Dragon fight. There is no concept of "what stage of the game am I in?"

## Goals

1. AI plays like a real human — follows a natural game progression (wood → stone → iron → diamond → nether → end)
2. AI can defeat the Ender Dragon autonomously
3. AI plays indefinitely after the dragon fight with meaningful POST_GAME content
4. When a player is nearby, AI switches to companion/support mode
5. When a player is offline/away, AI plays autonomously

---

## Architecture

### GameStage Enum

Stored in NBT as `gameStage` (string). Persists across restarts.

```java
public enum GameStage {
    WOOD,         // Chop trees, craft wooden tools
    STONE,        // Mine stone, craft stone tools
    IRON,         // Mine iron, smelt, craft iron gear
    DIAMOND,      // Mine diamonds, craft diamond gear
    NETHER,       // Build portal, enter nether, kill blazes
    STRONGHOLD,   // Gather ender pearls, find stronghold, activate portal
    END,          // Enter The End, destroy crystals, kill Ender Dragon
    HOME_BUILDING,// Build a home base with bed/furnace/chest/crafting table
    POST_GAME     // Infinite exploration with random objective pool
}
```

### PlayMode Enum

Runtime only — not stored in NBT. Re-evaluated every 100 ticks.

```java
public enum PlayMode { COMPANION, AUTONOMOUS }
```

**Switch logic (in `aiStep`):**
```java
if (++playModeCheckTimer >= 100) {
    playModeCheckTimer = 0;
    boolean playerNearby = level().getNearestPlayer(this, 32) != null;
    playMode = playerNearby ? PlayMode.COMPANION : PlayMode.AUTONOMOUS;
}
```

**COMPANION mode effects:**
- `FollowPlayerGoal` priority: 7 → 3 (higher priority, stays close to player)
- Share goals (`ShareWeaponGoal`, `ShareArmorGoal`, `ShareFoodGoal`) priority: 4 → 2
- Progression goals run at normal priority but yield to player-adjacent tasks

**AUTONOMOUS mode effects:**
- `FollowPlayerGoal` disabled (`canUse()` returns false)
- All progression goals run at full speed

---

## New Fields in FriendEntity

```java
private GameStage gameStage = GameStage.WOOD;
@Nullable private BlockPos homePos;       // Set when HOME_BUILDING completes
private PlayMode playMode = PlayMode.AUTONOMOUS;
private int playModeCheckTimer = 0;
```

### NBT Serialization

```java
// addAdditionalSaveData
output.store("gameStage", gameStage.name());
if (homePos != null) BlockPos.CODEC.encode(homePos, ...);

// readAdditionalSaveData
gameStage = GameStage.valueOf(input.read("gameStage", "WOOD"));
homePos = ... // read BlockPos if present
```

### CURRENT_GOAL Display (existing SynchedEntityData)

Format updated to: `"[STAGE] GoalName"` — e.g. `"[IRON] SmeltOreGoal"`

---

## StageAdvancerGoal (priority 0)

Runs every 1200 ticks (1 minute). Checks advancement condition for the current stage; calls `entity.advanceStage()` if met. No movement, no flags.

### Advancement Conditions

| Current Stage   | Condition to Advance                                    |
|-----------------|---------------------------------------------------------|
| WOOD            | Inventory contains a stone pickaxe                      |
| STONE           | Inventory contains an iron pickaxe                      |
| IRON            | Inventory contains full iron armor (helmet/chest/legs/boots) |
| DIAMOND         | Inventory contains full diamond armor                   |
| NETHER          | Inventory contains ≥ 6 blaze rods                       |
| STRONGHOLD      | NBT flag `endPortalActivated = true`                    |
| END             | `ServerLevel.getDragonFight().hasPreviouslyKilled()`    |
| HOME_BUILDING   | `homePos != null` (set by `BuildHomeGoal` on completion)|
| POST_GAME       | Never (permanent)                                       |

---

## Goal Activation Rules

### Existing Goals — Stage Binding

| Goal | Active Stages |
|------|--------------|
| `FloatGoal`, `EatFoodGoal`, `EquipBestToolGoal`, `FindFoodGoal` | All |
| `FleeDangerGoal`, `ExtinguishFireGoal` | All |
| `CollectItemOnGroundGoal`, `BreakBlockGoal`, `DiggingEscapeGoal`, `PlaceBlockToClimbGoal` | All |
| `CraftWoodenToolsGoal` | WOOD only |
| `ChopTreeGoal` | WOOD, STONE, HOME_BUILDING |
| `HuntLivestockGoal`, `FishingGoal`, `PlantCropGoal` | IRON and later |
| `ExploreGoal`, `ContemplateLifeGoal` | STONE and later (replaced by PostGameGoal in POST_GAME) |
| `ShareWeaponGoal`, `ShareArmorGoal`, `ShareFoodGoal` | All stages; priority 4 normally, promoted to 2 in COMPANION mode |
| `BreedFriendGoal` | POST_GAME only |
| `LeashMobGoal`, `RideFriendGoal` | All |

### New Goals

| Goal | Priority | Active Stage(s) | Purpose |
|------|----------|-----------------|---------|
| `StageAdvancerGoal` | 0 | All | Checks and advances GameStage |
| `MineStoneGoal` | 3 | STONE | Dig down from surface, mine stone, craft stone tools |
| `MineIronGoal` | 3 | IRON | Mine at Y=16–54, collect iron ore |
| `SmeltOreGoal` | 3 | IRON and later | Place/find furnace, smelt ore with fuel |
| `MineDiamondGoal` | 3 | DIAMOND | Mine at Y=-58–16, collect diamonds |
| `BuildNetherPortalGoal` | 3 | NETHER | Collect obsidian + flint & steel, build portal |
| `FindNetherFortressGoal` | 3 | NETHER | Navigate in nether to locate fortress |
| `KillBlazeGoal` | 2 | NETHER | Kill Blazes in fortress, collect blaze rods |
| `GatherEnderPearlsGoal` | 3 | STRONGHOLD | Hunt Endermen at night for pearls |
| `FindStrongholdGoal` | 3 | STRONGHOLD | Use Eyes of Ender to locate stronghold |
| `ActivateEndPortalGoal` | 3 | STRONGHOLD | Insert eyes into portal frame |
| `FightEnderDragonGoal` | 2 | END | Destroy end crystals, attack Ender Dragon |
| `BuildHomeGoal` | 3 | HOME_BUILDING | Find flat land, build 5×5 house, furnish it |
| `PostGameGoal` | 5 | POST_GAME | Random objective pool (see below) |

---

## BuildHomeGoal

Steps executed in sequence (internal state machine within the goal):

1. **FIND_SITE** — Scan for 7×7 flat area within 100 blocks. On failure, wander and retry.
2. **GATHER_MATERIALS** — Ensure inventory has: ≥64 wood planks (or other solid blocks), 1 bed, 1 crafting table, 1 furnace, 1 chest. Trigger ChopTreeGoal/CraftWoodenToolsGoal if short.
3. **BUILD** — Place floor (5×5), walls (3 high), roof. Place door on south face.
4. **FURNISH** — Place bed, crafting table, furnace, chest inside.
5. **DONE** — Set `entity.homePos` to house center. Stage advances to POST_GAME.

---

## PostGameGoal — Random Objective Pool

On completion of each objective, draw the next one at random (weighted). `IDLE_EXPLORE` is always available as fallback.

| Objective | Weight | Completion Condition |
|-----------|--------|---------------------|
| `EXPLORE_BIOME` | 3 | Reach a biome not previously visited |
| `BUILD_FARM` | 2 | Place ≥16 farmland blocks near water |
| `FIND_OCEAN_MONUMENT` | 1 | Navigate to nearest ocean monument |
| `FIND_WOODLAND_MANSION` | 1 | Navigate to nearest woodland mansion |
| `RAID_NETHER_FORTRESS` | 2 | Kill ≥3 Blazes in nether fortress |
| `FIND_END_CITY` | 1 | Navigate to nearest End City |
| `RETURN_HOME` | 2 | Return to `homePos` (triggers when >500 blocks away) |
| `IDLE_EXPLORE` | 4 | Explore random far destination (fallback) |

Stored in NBT: `postGameObjective` (String), `postGameTargetPos` (BlockPos).

---

## Files to Create / Modify

### New files
- `entity/GameStage.java`
- `entity/PlayMode.java`
- `entity/goal/StageAdvancerGoal.java`
- `entity/goal/MineStoneGoal.java`
- `entity/goal/MineIronGoal.java`
- `entity/goal/SmeltOreGoal.java`
- `entity/goal/MineDiamondGoal.java`
- `entity/goal/BuildNetherPortalGoal.java`
- `entity/goal/FindNetherFortressGoal.java`
- `entity/goal/KillBlazeGoal.java`
- `entity/goal/GatherEnderPearlsGoal.java`
- `entity/goal/FindStrongholdGoal.java`
- `entity/goal/ActivateEndPortalGoal.java`
- `entity/goal/FightEnderDragonGoal.java`
- `entity/goal/BuildHomeGoal.java`
- `entity/goal/PostGameGoal.java`

### Modified files
- `entity/FriendEntity.java` — add fields, NBT, PlayMode switch, advanceStage(), update registerGoals()
- Existing goals — add `entity.getGameStage()` / `entity.getPlayMode()` checks in `canUse()`
