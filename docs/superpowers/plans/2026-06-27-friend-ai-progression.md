# Friend AI Progression System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `GameStage` progression system to `FriendEntity` so the AI plays through Minecraft like a real human — from wooden tools to defeating the Ender Dragon, building a home, then exploring indefinitely.

**Architecture:** A `GameStage` enum (9 stages, stored in NBT) gates which goals are active. A `StageAdvancerGoal` (priority 0) checks advancement conditions every 1200 ticks and promotes the stage. A `PlayMode` enum switches between COMPANION (player nearby) and AUTONOMOUS (player away), adjusting goal priorities at runtime.

**Tech Stack:** Java 25, Minecraft 26.2 (Mojang official names), Fabric Loader 0.19.3, Fabric API 0.153.0+26.2

## Global Constraints

- MC 26.2 NBT API: `output.putString(key, val)` / `input.getStringOr(key, default)`, `output.putInt` / `input.getIntOr`, `output.store(key, BlockPos.CODEC, pos)` / `input.read(key, BlockPos.CODEC).ifPresent(...)`.
- Goal block-breaking pattern: equip tool → navigate → `sl.destroyBlockProgress(entity.getId(), pos, progress)` → `entity.level().destroyBlock(pos, true, entity)`.
- All new Goal files: `src/main/java/com/edcl/lovelyfriend/entity/goal/`
- All new enum files: `src/main/java/com/edcl/lovelyfriend/entity/`
- Lower priority number = higher priority.
- Build: `./gradlew build`. Dev client: `./gradlew runClient`.
- One commit per task.

---

## File Map

**New files:**
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

**Modified files:**
- `entity/FriendEntity.java` — fields, NBT, PlayMode switch, advanceStage(), registerGoals(), CURRENT_GOAL format
- `entity/goal/ChopTreeGoal.java` — add stage check in canUse()
- `entity/goal/CraftWoodenToolsGoal.java` — add stage check
- `entity/goal/HuntLivestockGoal.java` — add stage check
- `entity/goal/FishingGoal.java` — add stage check
- `entity/goal/PlantCropGoal.java` — add stage check
- `entity/goal/BreedFriendGoal.java` — add stage check
- `entity/goal/ExploreGoal.java` — add stage check
- `entity/goal/ContemplateLifeGoal.java` — add stage check

---

### Task 1: GameStage + PlayMode Enums

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/GameStage.java`
- Create: `src/main/java/com/edcl/lovelyfriend/entity/PlayMode.java`

**Interfaces:**
- Produces: `GameStage` enum with 9 values; `PlayMode` enum with 2 values — used by every subsequent task.

- [ ] **Step 1: Create GameStage.java**

```java
package com.edcl.lovelyfriend.entity;

public enum GameStage {
    WOOD,
    STONE,
    IRON,
    DIAMOND,
    NETHER,
    STRONGHOLD,
    END,
    HOME_BUILDING,
    POST_GAME;

    public GameStage next() {
        GameStage[] values = values();
        int i = ordinal() + 1;
        return i < values.length ? values[i] : POST_GAME;
    }
}
```

- [ ] **Step 2: Create PlayMode.java**

```java
package com.edcl.lovelyfriend.entity;

public enum PlayMode {
    COMPANION,
    AUTONOMOUS
}
```

- [ ] **Step 3: Build to verify compilation**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/GameStage.java
git add src/main/java/com/edcl/lovelyfriend/entity/PlayMode.java
git commit -m "feat: add GameStage and PlayMode enums for AI progression"
```

---

### Task 2: FriendEntity Core Infrastructure

**Files:**
- Modify: `src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java`

**Interfaces:**
- Consumes: `GameStage`, `PlayMode` from Task 1.
- Produces: `entity.getGameStage()`, `entity.getPlayMode()`, `entity.advanceStage()`, `entity.getHomePos()`, `entity.setHomePos(BlockPos)` — used by all subsequent tasks.

- [ ] **Step 1: Add imports to FriendEntity.java**

Add at the top with other imports:
```java
import com.edcl.lovelyfriend.entity.GameStage;
import com.edcl.lovelyfriend.entity.PlayMode;
import net.minecraft.core.BlockPos;
```

- [ ] **Step 2: Add new fields after the existing `private int breedCooldown = 0;` line**

```java
private GameStage gameStage = GameStage.WOOD;
@Nullable private BlockPos homePos;
private PlayMode playMode = PlayMode.AUTONOMOUS;
private int playModeCheckTimer = 0;
```

- [ ] **Step 3: Add public accessors after `public void setBreedCooldown(int ticks)` line**

```java
public GameStage getGameStage() { return gameStage; }
public PlayMode getPlayMode() { return playMode; }
@Nullable public BlockPos getHomePos() { return homePos; }
public void setHomePos(BlockPos pos) { this.homePos = pos; }

public void advanceStage() {
    gameStage = gameStage.next();
}
```

- [ ] **Step 4: Add PlayMode switch to aiStep(), inside the `if (!this.level().isClientSide())` block, after the existing timer decrements**

```java
if (++playModeCheckTimer >= 100) {
    playModeCheckTimer = 0;
    boolean playerNearby = this.level().getNearestPlayer(this, 32) != null;
    playMode = playerNearby ? PlayMode.COMPANION : PlayMode.AUTONOMOUS;
}
```

- [ ] **Step 5: Update CURRENT_GOAL display format in updateCurrentGoalDisplay()**

Replace the line:
```java
this.getEntityData().set(CURRENT_GOAL, newGoal);
```
With:
```java
this.getEntityData().set(CURRENT_GOAL, "[" + gameStage.name() + "] " + newGoal);
```

- [ ] **Step 6: Add NBT persistence to addAdditionalSaveData(), after `output.putInt("FoodLevel", foodLevel);`**

```java
output.putString("GameStage", gameStage.name());
if (homePos != null) {
    output.store("HomePos", BlockPos.CODEC, homePos);
}
```

- [ ] **Step 7: Add NBT loading to readAdditionalSaveData(), after `foodLevel = input.getIntOr("FoodLevel", MAX_FOOD_LEVEL);`**

```java
try {
    gameStage = GameStage.valueOf(input.getStringOr("GameStage", "WOOD"));
} catch (IllegalArgumentException e) {
    gameStage = GameStage.WOOD;
}
input.read("HomePos", BlockPos.CODEC).ifPresent(pos -> homePos = pos);
```

- [ ] **Step 8: Build to verify compilation**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: In-game smoke test**

Run `./gradlew runClient`. Spawn a FriendEntity. Use F3 or name tag to confirm `[WOOD] Idle` appears in the current goal display. Kill and re-spawn — stage should still be WOOD.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add GameStage/PlayMode fields, NBT persistence, and stage display to FriendEntity"
```

---

### Task 3: StageAdvancerGoal + Stage-Gate Existing Goals

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/StageAdvancerGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)
- Modify: `goal/ChopTreeGoal.java`, `goal/CraftWoodenToolsGoal.java`, `goal/HuntLivestockGoal.java`, `goal/FishingGoal.java`, `goal/PlantCropGoal.java`, `goal/BreedFriendGoal.java`, `goal/ExploreGoal.java`, `goal/ContemplateLifeGoal.java`

**Interfaces:**
- Consumes: `entity.getGameStage()`, `entity.advanceStage()`, `entity.getPlayMode()`.
- Produces: Stage gates on all affected goals.

- [ ] **Step 1: Create StageAdvancerGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;

import java.util.EnumSet;

public class StageAdvancerGoal extends Goal {

    private static final int CHECK_INTERVAL = 1200; // 1 minute

    private final FriendEntity entity;
    private int timer = CHECK_INTERVAL;

    public StageAdvancerGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (--timer > 0) return false;
        timer = CHECK_INTERVAL;
        return shouldAdvance();
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        entity.advanceStage();
    }

    private boolean shouldAdvance() {
        return switch (entity.getGameStage()) {
            case WOOD        -> hasStonePickaxe();
            case STONE       -> hasIronPickaxe();
            case IRON        -> hasFullArmorOf(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case DIAMOND     -> hasFullArmorOf(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            case NETHER      -> countInInventory(Items.BLAZE_ROD) >= 6;
            case STRONGHOLD  -> entity.getPersistentData().getBoolean("endPortalActivated");
            case END         -> {
                if (entity.level() instanceof ServerLevel sl) {
                    var fight = sl.getDragonFight();
                    yield fight != null && fight.hasPreviouslyKilled();
                }
                yield false;
            }
            case HOME_BUILDING -> entity.getHomePos() != null;
            case POST_GAME   -> false;
        };
    }

    private boolean hasStonePickaxe() {
        return hasItemInInventory(Items.STONE_PICKAXE);
    }

    private boolean hasIronPickaxe() {
        return hasItemInInventory(Items.IRON_PICKAXE);
    }

    private boolean hasFullArmorOf(net.minecraft.world.item.Item helmet,
                                   net.minecraft.world.item.Item chest,
                                   net.minecraft.world.item.Item legs,
                                   net.minecraft.world.item.Item boots) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(helmet)
            && entity.getItemBySlot(EquipmentSlot.CHEST).is(chest)
            && entity.getItemBySlot(EquipmentSlot.LEGS).is(legs)
            && entity.getItemBySlot(EquipmentSlot.FEET).is(boots);
    }

    private boolean hasItemInInventory(net.minecraft.world.item.Item item) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        // Also check main hand
        return entity.getMainHandItem().is(item);
    }

    private int countInInventory(net.minecraft.world.item.Item item) {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
```

- [ ] **Step 2: Add stage gate to ChopTreeGoal.canUse() — add at the top of the method**

After `if (cooldown-- > 0) return false;` add:
```java
// ChopTreeGoal only active during WOOD, STONE, HOME_BUILDING
GameStage stage = entity.getGameStage();
if (stage != com.edcl.lovelyfriend.entity.GameStage.WOOD
 && stage != com.edcl.lovelyfriend.entity.GameStage.STONE
 && stage != com.edcl.lovelyfriend.entity.GameStage.HOME_BUILDING) return false;
```

- [ ] **Step 3: Add stage gate to CraftWoodenToolsGoal.canUse() — add at the top**

```java
if (entity.getGameStage() != com.edcl.lovelyfriend.entity.GameStage.WOOD) return false;
```

- [ ] **Step 4: Add stage gate to HuntLivestockGoal.canUse() — add at the top**

```java
if (entity.getGameStage().ordinal() < com.edcl.lovelyfriend.entity.GameStage.IRON.ordinal()) return false;
```

- [ ] **Step 5: Add stage gate to FishingGoal.canUse() — add at the top**

```java
if (entity.getGameStage().ordinal() < com.edcl.lovelyfriend.entity.GameStage.IRON.ordinal()) return false;
```

- [ ] **Step 6: Add stage gate to PlantCropGoal.canUse() — add at the top**

```java
if (entity.getGameStage().ordinal() < com.edcl.lovelyfriend.entity.GameStage.IRON.ordinal()) return false;
```

- [ ] **Step 7: Add stage gate to BreedFriendGoal.canUse() — add at the top**

```java
if (entity.getGameStage() != com.edcl.lovelyfriend.entity.GameStage.POST_GAME) return false;
```

- [ ] **Step 8: Add stage gate to ExploreGoal.canUse() — add at the top**

```java
GameStage s = entity.getGameStage();
if (s == com.edcl.lovelyfriend.entity.GameStage.WOOD) return false;
if (s == com.edcl.lovelyfriend.entity.GameStage.POST_GAME) return false;
```

- [ ] **Step 9: Add stage gate to ContemplateLifeGoal.canUse() — add after the leash/target/passenger checks**

```java
GameStage s = entity.getGameStage();
if (s == com.edcl.lovelyfriend.entity.GameStage.WOOD) return false;
if (s == com.edcl.lovelyfriend.entity.GameStage.POST_GAME) return false;
```

- [ ] **Step 10: Register StageAdvancerGoal in FriendEntity.registerGoals() — add as the very first line**

```java
this.goalSelector.addGoal(0, new StageAdvancerGoal(this));
```

Also add the import:
```java
import com.edcl.lovelyfriend.entity.goal.StageAdvancerGoal;
```

- [ ] **Step 11: Update FollowPlayerGoal registration to respect PlayMode (COMPANION = priority 3, AUTONOMOUS = disabled)**

Replace the existing `FollowPlayerGoal` registration:
```java
this.goalSelector.addGoal(7, new FollowPlayerGoal(this, 0.5, 20.0, 3.0));
```
With:
```java
this.goalSelector.addGoal(3, new FollowPlayerGoal(this, 0.5, 20.0, 3.0) {
    @Override public boolean canUse() {
        return FriendEntity.this.getPlayMode() == com.edcl.lovelyfriend.entity.PlayMode.COMPANION
            && super.canUse();
    }
});
```

- [ ] **Step 12: Promote Share goals to priority 2 in COMPANION mode**

Replace all three Share goal registrations:
```java
this.goalSelector.addGoal(4, new ShareWeaponGoal(this));
this.goalSelector.addGoal(4, new ShareArmorGoal(this));
this.goalSelector.addGoal(4, new ShareFoodGoal(this));
```
With:
```java
this.goalSelector.addGoal(2, new ShareWeaponGoal(this) {
    @Override public boolean canUse() {
        return FriendEntity.this.getPlayMode() == com.edcl.lovelyfriend.entity.PlayMode.COMPANION
            && super.canUse();
    }
});
this.goalSelector.addGoal(2, new ShareArmorGoal(this) {
    @Override public boolean canUse() {
        return FriendEntity.this.getPlayMode() == com.edcl.lovelyfriend.entity.PlayMode.COMPANION
            && super.canUse();
    }
});
this.goalSelector.addGoal(2, new ShareFoodGoal(this) {
    @Override public boolean canUse() {
        return FriendEntity.this.getPlayMode() == com.edcl.lovelyfriend.entity.PlayMode.COMPANION
            && super.canUse();
    }
});
```

- [ ] **Step 13: Build to verify compilation**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: In-game test**

Run `./gradlew runClient`. Give a FriendEntity a stone pickaxe via `/give @p stone_pickaxe`, then right-click to give it to the Friend. Wait 60 seconds. Confirm stage display changes from `[WOOD]` to `[STONE]`. Confirm ChopTreeGoal is no longer active in DIAMOND stage (give full iron armor instead and verify stage advances again).

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/goal/StageAdvancerGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/ChopTreeGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/CraftWoodenToolsGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/HuntLivestockGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/FishingGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/PlantCropGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/BreedFriendGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/ExploreGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/ContemplateLifeGoal.java
git commit -m "feat: add StageAdvancerGoal and stage gates to existing goals"
```

---

### Task 4: MineStoneGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/MineStoneGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()`, `entity.equipPickaxe()`, `entity.restoreWeapon()`.
- Produces: AI mines stone during STONE stage to get cobblestone for crafting.

- [ ] **Step 1: Create MineStoneGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class MineStoneGoal extends Goal {

    private static final int SEARCH_RADIUS = 12;
    private static final int COOLDOWN = 200;
    private static final int TARGET_COBBLE = 16; // stop when have enough

    private final FriendEntity entity;
    private BlockPos target;
    private int breakTick;
    private int breakDuration;
    private int cooldown;
    private boolean usedPickaxe;

    public MineStoneGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.STONE) return false;
        if (entity.getTarget() != null) return false;
        if (countCobble() >= TARGET_COBBLE) return false;
        if (entity.getRandom().nextFloat() > 0.1f) return false;
        target = findStone();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && entity.getTarget() == null && countCobble() < TARGET_COBBLE;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe();
        breakDuration = calcBreakTicks();
        entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedPickaxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        target = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (target == null || !(entity.level() instanceof ServerLevel sl)) return;
        BlockState state = entity.level().getBlockState(target);
        if (!isStone(state)) { target = findStone(); if (target == null) return; breakTick = 0; breakDuration = calcBreakTicks(); }
        entity.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 6.0) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), target, Math.min(breakTick * 10 / breakDuration, 9));
        if (++breakTick >= breakDuration) {
            clearProgress();
            entity.level().destroyBlock(target, true, entity);
            breakTick = 0;
            target = findStone();
            if (target != null) { breakDuration = calcBreakTicks(); entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0); }
        }
    }

    private void clearProgress() {
        if (target != null && entity.level() instanceof ServerLevel sl)
            sl.destroyBlockProgress(entity.getId(), target, -1);
    }

    private BlockPos findStone() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null; double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -3; dy <= 3; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isStone(entity.level().getBlockState(pos))) {
                        double d = origin.distSqr(pos);
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
        return best;
    }

    private boolean isStone(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.DEEPSLATE)
            || s.is(Blocks.ANDESITE) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE);
    }

    private int calcBreakTicks() {
        if (target == null) return 20;
        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness <= 0) return 4;
        ItemStack tool = entity.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0f : Math.max(1.0f, tool.getDestroySpeed(state));
        return Math.max(1, (int) Math.ceil(hardness * 1.5f / speed * 20));
    }

    private int countCobble() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(Blocks.COBBLESTONE.asItem()) || s.is(Blocks.STONE.asItem()) || s.is(Blocks.COBBLED_DEEPSLATE.asItem())) count += s.getCount();
        }
        return count;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals() in the priority-3 environment block**

```java
import com.edcl.lovelyfriend.entity.goal.MineStoneGoal;
// ...
this.goalSelector.addGoal(3, new MineStoneGoal(this));
```

- [ ] **Step 3: Build**

```
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: In-game test**

Set `gameStage` to STONE by giving the Friend a stone pickaxe and waiting for StageAdvancerGoal to fire. Confirm `[STONE] MineStoneGoal` appears in the display. Confirm the Friend walks to nearby stone and breaks it.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/edcl/lovelyfriend/entity/goal/MineStoneGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add MineStoneGoal for STONE stage"
```

---

### Task 5: MineIronGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/MineIronGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()`, `entity.equipPickaxe()`, `entity.restoreWeapon()`.
- Produces: AI digs to Y=32 during IRON stage and mines iron ore.

- [ ] **Step 1: Create MineIronGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class MineIronGoal extends Goal {

    private static final int TARGET_Y = 32;
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_Y_RANGE = 20;
    private static final int TARGET_IRON = 8;
    private static final int COOLDOWN = 300;

    private final FriendEntity entity;
    private BlockPos target;
    private int breakTick, breakDuration, cooldown;
    private boolean usedPickaxe;

    public MineIronGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.IRON) return false;
        if (entity.getTarget() != null) return false;
        if (countRawIron() >= TARGET_IRON) return false;
        if (entity.getRandom().nextFloat() > 0.08f) return false;
        target = findIronOre();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && entity.getTarget() == null && countRawIron() < TARGET_IRON;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe();
        breakDuration = calcBreakTicks();
        entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedPickaxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        target = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (target == null || !(entity.level() instanceof ServerLevel sl)) return;
        if (!isIronOre(entity.level().getBlockState(target))) {
            target = findIronOre(); if (target == null) return;
            breakTick = 0; breakDuration = calcBreakTicks();
        }
        entity.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 6.0) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), target, Math.min(breakTick * 10 / breakDuration, 9));
        if (++breakTick >= breakDuration) {
            clearProgress();
            entity.level().destroyBlock(target, true, entity);
            breakTick = 0;
            target = findIronOre();
            if (target != null) { breakDuration = calcBreakTicks(); entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0); }
        }
    }

    private void clearProgress() {
        if (target != null && entity.level() instanceof ServerLevel sl)
            sl.destroyBlockProgress(entity.getId(), target, -1);
    }

    private BlockPos findIronOre() {
        BlockPos origin = entity.blockPosition();
        int searchY = Math.max(TARGET_Y - SEARCH_Y_RANGE, entity.level().getMinY() + 5);
        BlockPos best = null; double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -SEARCH_Y_RANGE; dy <= SEARCH_Y_RANGE; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (pos.getY() < searchY || pos.getY() > TARGET_Y + SEARCH_Y_RANGE) continue;
                    if (isIronOre(entity.level().getBlockState(pos))) {
                        double d = origin.distSqr(pos);
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
        // If no ore found near surface, head to TARGET_Y to dig
        if (best == null && origin.getY() > TARGET_Y + 5) {
            return new BlockPos(origin.getX(), TARGET_Y, origin.getZ());
        }
        return best;
    }

    private boolean isIronOre(BlockState s) {
        return s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE);
    }

    private int calcBreakTicks() {
        if (target == null) return 40;
        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness <= 0) return 4;
        ItemStack tool = entity.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0f : Math.max(1.0f, tool.getDestroySpeed(state));
        return Math.max(1, (int) Math.ceil(hardness * 1.5f / speed * 20));
    }

    private int countRawIron() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i); if (s.is(Items.RAW_IRON)) count += s.getCount();
        }
        return count;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.MineIronGoal;
// ...
this.goalSelector.addGoal(3, new MineIronGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/MineIronGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add MineIronGoal for IRON stage"
```

---

### Task 6: SmeltOreGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/SmeltOreGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (IRON and later), entity inventory.
- Produces: AI finds or places a furnace and smelts raw iron/gold into ingots.

- [ ] **Step 1: Create SmeltOreGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class SmeltOreGoal extends Goal {

    private static final int SEARCH_RADIUS = 24;
    private static final int COOLDOWN = 400;
    private static final int SMELT_WAIT = 200; // ticks to wait for smelting

    private final FriendEntity entity;
    private BlockPos furnacePos;
    private int cooldown;
    private int waitTimer;
    private boolean waiting;

    public SmeltOreGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage().ordinal() < GameStage.IRON.ordinal()) return false;
        if (entity.getTarget() != null) return false;
        if (!hasSmeltableOre()) return false;
        if (entity.getRandom().nextFloat() > 0.05f) return false;
        furnacePos = findFurnace();
        if (furnacePos == null) furnacePos = tryPlaceFurnace();
        return furnacePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (furnacePos == null) return false;
        if (!hasSmeltableOre() && !waiting) return false;
        return entity.getTarget() == null;
    }

    @Override
    public void start() {
        waiting = false;
        waitTimer = 0;
        entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        furnacePos = null;
        waiting = false;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (furnacePos == null) return;
        if (entity.distanceToSqr(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5) > 9.0) {
            entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        if (!waiting) {
            loadFurnace();
            waiting = true;
            waitTimer = 0;
        } else {
            if (++waitTimer >= SMELT_WAIT) {
                collectFurnaceOutput();
                waiting = false;
            }
        }
    }

    private boolean hasSmeltableOre() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER)) return true;
        }
        return false;
    }

    private BlockPos findFurnace() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -4; dy <= 4; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (entity.level().getBlockState(pos).is(Blocks.FURNACE)) return pos;
                }
        return null;
    }

    private BlockPos tryPlaceFurnace() {
        // Check if we have a furnace in inventory
        var inv = entity.getInventory();
        int furnaceSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Blocks.FURNACE.asItem())) { furnaceSlot = i; break; }
        }
        if (furnaceSlot < 0) return null;

        // Find a place to put it — the block in front of us at feet level
        BlockPos placePos = entity.blockPosition().relative(entity.getDirection());
        if (!entity.level().getBlockState(placePos).isAir()) return null;
        if (!(entity.level() instanceof ServerLevel sl)) return null;

        sl.setBlock(placePos, Blocks.FURNACE.defaultBlockState(), 3);
        inv.removeItem(furnaceSlot, 1);
        return placePos;
    }

    private void loadFurnace() {
        if (!(entity.level() instanceof ServerLevel)) return;
        BlockEntity be = entity.level().getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        var inv = entity.getInventory();
        // Slot 0 = input ore, slot 1 = fuel, slot 2 = output
        // Load ore into slot 0
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.RAW_IRON) || s.is(Items.RAW_GOLD) || s.is(Items.RAW_COPPER)) {
                if (furnace.getItem(0).isEmpty()) {
                    furnace.setItem(0, s.split(s.getCount()));
                    inv.setItem(i, ItemStack.EMPTY);
                }
                break;
            }
        }
        // Load fuel into slot 1 (coal or wood)
        if (furnace.getItem(1).isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.COAL_BLOCK)) {
                    furnace.setItem(1, s.split(Math.min(8, s.getCount())));
                    break;
                }
                // Use wooden planks as fallback fuel
                if (s.is(net.minecraft.tags.ItemTags.PLANKS)) {
                    furnace.setItem(1, s.split(Math.min(8, s.getCount())));
                    break;
                }
            }
        }
    }

    private void collectFurnaceOutput() {
        BlockEntity be = entity.level().getBlockEntity(furnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack output = furnace.getItem(2);
        if (!output.isEmpty()) {
            entity.getInventory(); // trigger pickup via addToInventory pattern
            // Directly add to inventory (same as addToInventory in FriendEntity)
            var inv = entity.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);
                if (slot.isEmpty()) { inv.setItem(i, output.copy()); furnace.setItem(2, ItemStack.EMPTY); break; }
            }
        }
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.SmeltOreGoal;
// ...
this.goalSelector.addGoal(3, new SmeltOreGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/SmeltOreGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add SmeltOreGoal for smelting ore into ingots"
```

---

### Task 7: MineDiamondGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/MineDiamondGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (DIAMOND), entity inventory.
- Produces: AI mines at Y=-58 to Y=16 for diamond ore during DIAMOND stage.

- [ ] **Step 1: Create MineDiamondGoal.java**

Same structure as `MineIronGoal` with these differences:
- `TARGET_Y = -20` (optimal diamond level in 1.18+)
- `SEARCH_Y_RANGE = 20` (covers Y=-40 to Y=0)
- `TARGET_DIAMONDS = 5` (enough for armor)
- `isOre()` checks `Blocks.DIAMOND_ORE` and `Blocks.DEEPSLATE_DIAMOND_ORE`
- `countTarget()` checks `Items.DIAMOND`

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class MineDiamondGoal extends Goal {

    private static final int TARGET_Y = -20;
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_Y_RANGE = 20;
    private static final int TARGET_DIAMONDS = 5;
    private static final int COOLDOWN = 400;

    private final FriendEntity entity;
    private BlockPos target;
    private int breakTick, breakDuration, cooldown;
    private boolean usedPickaxe;

    public MineDiamondGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.DIAMOND) return false;
        if (entity.getTarget() != null) return false;
        if (countDiamonds() >= TARGET_DIAMONDS) return false;
        if (entity.getRandom().nextFloat() > 0.08f) return false;
        target = findDiamond();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && entity.getTarget() == null && countDiamonds() < TARGET_DIAMONDS;
    }

    @Override
    public void start() {
        breakTick = 0;
        usedPickaxe = entity.equipPickaxe();
        breakDuration = calcBreakTicks();
        entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        clearProgress();
        if (usedPickaxe) entity.restoreWeapon();
        entity.getNavigation().stop();
        target = null;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (target == null || !(entity.level() instanceof ServerLevel sl)) return;
        if (!isDiamond(entity.level().getBlockState(target))) {
            target = findDiamond(); if (target == null) return;
            breakTick = 0; breakDuration = calcBreakTicks();
        }
        entity.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 6.0) {
            entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        entity.swing(InteractionHand.MAIN_HAND);
        sl.destroyBlockProgress(entity.getId(), target, Math.min(breakTick * 10 / breakDuration, 9));
        if (++breakTick >= breakDuration) {
            clearProgress();
            entity.level().destroyBlock(target, true, entity);
            breakTick = 0;
            target = findDiamond();
            if (target != null) { breakDuration = calcBreakTicks(); entity.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0); }
        }
    }

    private void clearProgress() {
        if (target != null && entity.level() instanceof ServerLevel sl)
            sl.destroyBlockProgress(entity.getId(), target, -1);
    }

    private BlockPos findDiamond() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null; double bestDist = Double.MAX_VALUE;
        int minY = TARGET_Y - SEARCH_Y_RANGE, maxY = TARGET_Y + SEARCH_Y_RANGE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -SEARCH_Y_RANGE; dy <= SEARCH_Y_RANGE; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (pos.getY() < minY || pos.getY() > maxY) continue;
                    if (isDiamond(entity.level().getBlockState(pos))) {
                        double d = origin.distSqr(pos);
                        if (d < bestDist) { bestDist = d; best = pos; }
                    }
                }
        if (best == null && origin.getY() > TARGET_Y + 10)
            return new BlockPos(origin.getX(), TARGET_Y, origin.getZ());
        return best;
    }

    private boolean isDiamond(BlockState s) {
        return s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    private int calcBreakTicks() {
        if (target == null) return 40;
        Level level = entity.level();
        BlockState state = level.getBlockState(target);
        float hardness = state.getDestroySpeed(level, target);
        if (hardness <= 0) return 4;
        ItemStack tool = entity.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0f : Math.max(1.0f, tool.getDestroySpeed(state));
        return Math.max(1, (int) Math.ceil(hardness * 1.5f / speed * 20));
    }

    private int countDiamonds() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i); if (s.is(Items.DIAMOND)) count += s.getCount();
        }
        return count;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.MineDiamondGoal;
// ...
this.goalSelector.addGoal(3, new MineDiamondGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/MineDiamondGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add MineDiamondGoal for DIAMOND stage"
```

---

### Task 8: BuildNetherPortalGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/BuildNetherPortalGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (NETHER), entity inventory (needs 10 obsidian + flint & steel).
- Produces: AI builds a 2×3 nether portal frame and lights it. Portal entry teleports the entity automatically.

- [ ] **Step 1: Create BuildNetherPortalGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class BuildNetherPortalGoal extends Goal {

    private static final int COOLDOWN = 600;
    private static final int MIN_OBSIDIAN = 10;

    private final FriendEntity entity;
    private int cooldown;
    private List<BlockPos> framePlan;
    private BlockPos buildOrigin;
    private int buildIndex;
    private boolean building;
    private boolean lighting;

    public BuildNetherPortalGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.NETHER) return false;
        if (entity.getTarget() != null) return false;
        if (entity.getRandom().nextFloat() > 0.03f) return false;
        return countObsidian() >= MIN_OBSIDIAN && hasFlintAndSteel();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getGameStage() == GameStage.NETHER && entity.getTarget() == null && (building || lighting);
    }

    @Override
    public void start() {
        buildOrigin = entity.blockPosition().relative(entity.getDirection(), 2);
        framePlan = buildFramePlan(buildOrigin);
        buildIndex = 0;
        building = true;
        lighting = false;
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        building = false;
        lighting = false;
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (!(entity.level() instanceof ServerLevel sl)) return;

        if (building) {
            if (buildIndex >= framePlan.size()) {
                building = false;
                lighting = true;
                return;
            }
            BlockPos pos = framePlan.get(buildIndex);
            if (entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9.0) {
                entity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
                return;
            }
            entity.getNavigation().stop();
            if (entity.level().getBlockState(pos).isAir() || entity.level().getBlockState(pos).canBeReplaced()) {
                sl.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                removeFromInventory(Items.OBSIDIAN);
            }
            buildIndex++;
        }

        if (lighting) {
            // Light the portal — find an air block inside the frame
            BlockPos insidePos = buildOrigin.above(1).relative(entity.getDirection().getOpposite(), 0);
            if (entity.distanceToSqr(insidePos.getX() + 0.5, insidePos.getY() + 0.5, insidePos.getZ() + 0.5) > 9.0) {
                entity.getNavigation().moveTo(insidePos.getX() + 0.5, insidePos.getY(), insidePos.getZ() + 0.5, 1.0);
                return;
            }
            entity.getNavigation().stop();
            // Try to light using flint and steel from inventory
            var inv = entity.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(Items.FLINT_AND_STEEL)) {
                    // Activate the portal block
                    Blocks.FIRE.defaultBlockState(); // ensure Fire is registered
                    sl.setBlock(insidePos, Blocks.FIRE.defaultBlockState(), 3);
                    entity.playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0f, 1.0f);
                    inv.getItem(i).hurtAndBreak(1, entity, entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND, item -> {});
                    lighting = false;
                    break;
                }
            }
        }
    }

    private List<BlockPos> buildFramePlan(BlockPos origin) {
        // Build a 4-wide × 5-tall frame (2×3 inner) facing the entity's direction
        // Bottom row: 4 blocks, then 3 high on sides, top row: 4 blocks
        Direction facing = entity.getDirection();
        Direction right = facing.getClockWise();
        List<BlockPos> plan = new ArrayList<>();
        // Bottom: 4 blocks
        for (int i = 0; i < 4; i++) plan.add(origin.relative(right, i));
        // Left side: 3 blocks up
        for (int y = 1; y <= 4; y++) plan.add(origin.above(y));
        // Right side: 3 blocks up
        for (int y = 1; y <= 4; y++) plan.add(origin.relative(right, 3).above(y));
        // Top: 4 blocks
        for (int i = 0; i < 4; i++) plan.add(origin.above(5).relative(right, i));
        return plan;
    }

    private int countObsidian() {
        int count = 0;
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i); if (s.is(Items.OBSIDIAN)) count += s.getCount();
        }
        return count;
    }

    private boolean hasFlintAndSteel() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Items.FLINT_AND_STEEL)) return true;
        return false;
    }

    private void removeFromInventory(net.minecraft.world.item.Item item) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(item)) { s.shrink(1); return; }
        }
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.BuildNetherPortalGoal;
// ...
this.goalSelector.addGoal(3, new BuildNetherPortalGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/BuildNetherPortalGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add BuildNetherPortalGoal for NETHER stage"
```

---

### Task 9: FindNetherFortressGoal + KillBlazeGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/FindNetherFortressGoal.java`
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/KillBlazeGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (NETHER).
- Produces: AI navigates to nether fortress and targets Blaze mobs for blaze rods.

- [ ] **Step 1: Create FindNetherFortressGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FindNetherFortressGoal extends Goal {

    private static final int COOLDOWN_MIN = 3600;
    private static final double ARRIVAL_DIST_SQ = 100.0;
    private static final int MAX_STUCK_TIME = 800;

    private final FriendEntity entity;
    private BlockPos targetPos;
    private int cooldown;
    private int stuckTimer;
    private Vec3 lastPos;

    public FindNetherFortressGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
        cooldown = COOLDOWN_MIN;
    }

    @Override
    public boolean canUse() {
        if (entity.getGameStage() != GameStage.NETHER) return false;
        if (entity.getTarget() != null) return false;
        if (!(entity.level() instanceof ServerLevel sl)) return false;
        if (targetPos != null) {
            if (entity.blockPosition().distSqr(targetPos) < ARRIVAL_DIST_SQ) { targetPos = null; cooldown = COOLDOWN_MIN; return false; }
            return true;
        }
        if (cooldown-- > 0) return false;
        targetPos = sl.findNearestMapStructure(StructureTags.NETHER_COMPLEXES, entity.blockPosition(), 64, false);
        if (targetPos == null) { cooldown = 200; return false; }
        stuckTimer = 0; lastPos = entity.position();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null && entity.getGameStage() == GameStage.NETHER && entity.getTarget() == null;
    }

    @Override
    public void start() { if (targetPos != null) navigate(); }

    @Override
    public void stop() { entity.getNavigation().stop(); }

    @Override
    public void tick() {
        if (targetPos == null) return;
        Vec3 cur = entity.position();
        if (cur.distanceToSqr(lastPos) < 0.05) { if (++stuckTimer > MAX_STUCK_TIME) { targetPos = null; cooldown = 200; return; } }
        else stuckTimer = 0;
        lastPos = cur;
        if (!entity.getNavigation().isInProgress()) navigate();
    }

    private void navigate() {
        entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
    }
}
```

- [ ] **Step 2: Create KillBlazeGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Blaze;

public class KillBlazeGoal extends NearestAttackableTargetGoal<Blaze> {

    private final FriendEntity friend;

    public KillBlazeGoal(FriendEntity entity) {
        super(entity, Blaze.class, true);
        this.friend = entity;
    }

    @Override
    public boolean canUse() {
        return friend.getGameStage() == GameStage.NETHER && super.canUse();
    }
}
```

- [ ] **Step 3: Register both in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.FindNetherFortressGoal;
import com.edcl.lovelyfriend.entity.goal.KillBlazeGoal;
// ...
this.goalSelector.addGoal(3, new FindNetherFortressGoal(this));
this.targetSelector.addGoal(1, new KillBlazeGoal(this));
```

- [ ] **Step 4: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/FindNetherFortressGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/KillBlazeGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add FindNetherFortressGoal and KillBlazeGoal for NETHER stage"
```

---

### Task 10: GatherEnderPearlsGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/GatherEnderPearlsGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals + targetSelector)

**Interfaces:**
- Consumes: `entity.getGameStage()` (STRONGHOLD).
- Produces: AI targets Endermen at night to collect ender pearls.

- [ ] **Step 1: Create GatherEnderPearlsGoal.java (target selector goal)**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enderman;

public class GatherEnderPearlsGoal extends NearestAttackableTargetGoal<Enderman> {

    private static final int TARGET_PEARLS = 12;
    private final FriendEntity friend;

    public GatherEnderPearlsGoal(FriendEntity entity) {
        super(entity, Enderman.class, true);
        this.friend = entity;
    }

    @Override
    public boolean canUse() {
        if (friend.getGameStage() != GameStage.STRONGHOLD) return false;
        if (countPearls() >= TARGET_PEARLS) return false;
        return super.canUse();
    }

    private int countPearls() {
        int count = 0;
        var inv = friend.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(net.minecraft.world.item.Items.ENDER_PEARL)) count += s.getCount();
        }
        return count;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals() — targetSelector**

```java
import com.edcl.lovelyfriend.entity.goal.GatherEnderPearlsGoal;
// ...
this.targetSelector.addGoal(1, new GatherEnderPearlsGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/GatherEnderPearlsGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add GatherEnderPearlsGoal for STRONGHOLD stage"
```

---

### Task 11: FindStrongholdGoal + ActivateEndPortalGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/FindStrongholdGoal.java`
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/ActivateEndPortalGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (STRONGHOLD), entity inventory (needs 12 ender pearls + blaze powder for eyes).
- Produces: AI navigates to stronghold, places Eyes of Ender in portal frame, sets `endPortalActivated = true` in persistent data.

- [ ] **Step 1: Create FindStrongholdGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FindStrongholdGoal extends Goal {

    private static final int COOLDOWN_MIN = 2400;
    private static final double ARRIVAL_DIST_SQ = 100.0;
    private static final int MAX_STUCK_TIME = 1200;

    private final FriendEntity entity;
    private BlockPos targetPos;
    private int cooldown = COOLDOWN_MIN;
    private int stuckTimer;
    private Vec3 lastPos;

    public FindStrongholdGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (entity.getGameStage() != GameStage.STRONGHOLD) return false;
        if (entity.getTarget() != null) return false;
        if (!(entity.level() instanceof ServerLevel sl)) return false;
        if (targetPos != null) {
            if (entity.blockPosition().distSqr(targetPos) < ARRIVAL_DIST_SQ) { targetPos = null; cooldown = COOLDOWN_MIN; return false; }
            return true;
        }
        if (cooldown-- > 0) return false;
        targetPos = sl.findNearestMapStructure(StructureTags.STRONGHOLD, entity.blockPosition(), 128, false);
        if (targetPos == null) { cooldown = 400; return false; }
        stuckTimer = 0; lastPos = entity.position();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null && entity.getGameStage() == GameStage.STRONGHOLD && entity.getTarget() == null;
    }

    @Override
    public void start() { if (targetPos != null) navigate(); }

    @Override
    public void stop() { entity.getNavigation().stop(); }

    @Override
    public void tick() {
        if (targetPos == null) return;
        Vec3 cur = entity.position();
        if (cur.distanceToSqr(lastPos) < 0.05) { if (++stuckTimer > MAX_STUCK_TIME) { targetPos = null; cooldown = 400; return; } }
        else stuckTimer = 0;
        lastPos = cur;
        if (!entity.getNavigation().isInProgress()) navigate();
    }

    private void navigate() {
        entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.9);
    }
}
```

- [ ] **Step 2: Create ActivateEndPortalGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ActivateEndPortalGoal extends Goal {

    private static final int SEARCH_RADIUS = 24;
    private static final int COOLDOWN = 600;

    private final FriendEntity entity;
    private int cooldown;
    private BlockPos targetFrame;

    public ActivateEndPortalGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.STRONGHOLD) return false;
        if (!hasEyeOfEnder()) return false;
        if (entity.getTarget() != null) return false;
        targetFrame = findFrameWithoutEye();
        return targetFrame != null;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getGameStage() == GameStage.STRONGHOLD && entity.getTarget() == null;
    }

    @Override
    public void start() {
        entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 0.9);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (targetFrame == null) { targetFrame = findFrameWithoutEye(); if (targetFrame == null) return; }
        entity.getLookControl().setLookAt(targetFrame.getX() + 0.5, targetFrame.getY() + 0.5, targetFrame.getZ() + 0.5);
        if (entity.distanceToSqr(targetFrame.getX() + 0.5, targetFrame.getY() + 0.5, targetFrame.getZ() + 0.5) > 9.0) {
            entity.getNavigation().moveTo(targetFrame.getX() + 0.5, targetFrame.getY(), targetFrame.getZ() + 0.5, 0.9);
            return;
        }
        entity.getNavigation().stop();
        if (!(entity.level() instanceof ServerLevel sl)) return;
        BlockState state = sl.getBlockState(targetFrame);
        if (state.is(Blocks.END_PORTAL_FRAME) && !state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            sl.setBlock(targetFrame, state.setValue(EndPortalFrameBlock.HAS_EYE, true), 3);
            removeEyeFromInventory();
            entity.getPersistentData().putBoolean("endPortalActivated", true);
        }
        targetFrame = findFrameWithoutEye();
    }

    private BlockPos findFrameWithoutEye() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dy = -8; dy <= 8; dy++)
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState s = entity.level().getBlockState(pos);
                    if (s.is(Blocks.END_PORTAL_FRAME) && !s.getValue(EndPortalFrameBlock.HAS_EYE)) return pos;
                }
        return null;
    }

    private boolean hasEyeOfEnder() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Items.ENDER_EYE)) return true;
        return false;
    }

    private void removeEyeFromInventory() {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.is(Items.ENDER_EYE)) { s.shrink(1); return; }
        }
    }
}
```

- [ ] **Step 3: Register both in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.FindStrongholdGoal;
import com.edcl.lovelyfriend.entity.goal.ActivateEndPortalGoal;
// ...
this.goalSelector.addGoal(3, new FindStrongholdGoal(this));
this.goalSelector.addGoal(3, new ActivateEndPortalGoal(this));
```

- [ ] **Step 4: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/FindStrongholdGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/goal/ActivateEndPortalGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add FindStrongholdGoal and ActivateEndPortalGoal for STRONGHOLD stage"
```

---

### Task 12: FightEnderDragonGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/FightEnderDragonGoal.java`
- Modify: `entity/FriendEntity.java` (targetSelector)

**Interfaces:**
- Consumes: `entity.getGameStage()` (END).
- Produces: AI targets End Crystals first, then targets Ender Dragon. Stage advances to HOME_BUILDING when dragon dies (handled by StageAdvancerGoal via `hasPreviouslyKilled()`).

- [ ] **Step 1: Create FightEnderDragonGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.EnderDragonPart;

public class FightEnderDragonGoal extends NearestAttackableTargetGoal<EnderDragon> {

    private final FriendEntity friend;

    public FightEnderDragonGoal(FriendEntity entity) {
        super(entity, EnderDragon.class, false);
        this.friend = entity;
    }

    @Override
    public boolean canUse() {
        return friend.getGameStage() == GameStage.END && super.canUse();
    }
}
```

Also create a companion goal for targeting End Crystals:

```java
// Add to the same file as a second class (or separate file — here it's kept in the same file for simplicity)
// In FriendEntity.registerGoals(), target End Crystals via NearestAttackableTargetGoal<EndCrystal>
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.FightEnderDragonGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.LightningBolt; // placeholder for EndCrystal import
import net.minecraft.world.entity.boss.EnderDragonPart;
// Target End Crystals first (priority 1), then Dragon (priority 2)
this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
    this, net.minecraft.world.entity.decoration.ArmorStand.class, false) {
    // ponytail: End Crystal class is net.minecraft.world.entity.boss.enderdragon.EndCrystal — verify import name
});
this.targetSelector.addGoal(1, new FightEnderDragonGoal(this));
```

**Note:** Verify the exact class name for End Crystal in MC 26.2. Search for `EndCrystal` in the Minecraft source:
```
./gradlew genSources  # if needed
```
Then update the `NearestAttackableTargetGoal<>` for End Crystal with the verified class.

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/FightEnderDragonGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add FightEnderDragonGoal for END stage"
```

---

### Task 13: BuildHomeGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/BuildHomeGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (HOME_BUILDING), `entity.setHomePos(BlockPos)`.
- Produces: AI builds a 5×5 house with door, bed, crafting table, furnace, chest. Sets `homePos` when done.

- [ ] **Step 1: Create BuildHomeGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

public class BuildHomeGoal extends Goal {

    private enum BuildPhase { FIND_SITE, BUILD, FURNISH, DONE }

    private static final int COOLDOWN = 400;

    private final FriendEntity entity;
    private BuildPhase phase = BuildPhase.FIND_SITE;
    private BlockPos siteOrigin; // SW corner of 5×5 floor
    private int buildStep;
    private int cooldown;

    public BuildHomeGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;
        if (entity.getGameStage() != GameStage.HOME_BUILDING) return false;
        if (entity.getTarget() != null) return false;
        if (entity.getRandom().nextFloat() > 0.05f) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getGameStage() == GameStage.HOME_BUILDING
            && phase != BuildPhase.DONE
            && entity.getTarget() == null;
    }

    @Override
    public void start() {
        if (phase == BuildPhase.FIND_SITE || siteOrigin == null) {
            siteOrigin = findFlatSite();
            if (siteOrigin == null) { cooldown = COOLDOWN; return; }
            phase = BuildPhase.BUILD;
            buildStep = 0;
        }
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        cooldown = COOLDOWN;
    }

    @Override
    public void tick() {
        if (siteOrigin == null || !(entity.level() instanceof ServerLevel sl)) return;

        switch (phase) {
            case BUILD -> tickBuild(sl);
            case FURNISH -> tickFurnish(sl);
            default -> {}
        }
    }

    private void tickBuild(ServerLevel sl) {
        // Place walls: 5×5 floor footprint, 3 high walls, roof
        // We encode all positions as a flat list in buildOrder()
        var plan = buildOrder();
        if (buildStep >= plan.size()) { phase = BuildPhase.FURNISH; buildStep = 0; return; }

        BlockPos pos = plan.get(buildStep);
        if (entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 16.0) {
            entity.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();
        BlockState existing = sl.getBlockState(pos);
        if (existing.isAir() || existing.canBeReplaced()) {
            sl.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }
        buildStep++;
    }

    private void tickFurnish(ServerLevel sl) {
        // Place: bed, crafting table, furnace, chest inside the house
        BlockPos center = siteOrigin.offset(2, 1, 2);
        if (entity.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) > 16.0) {
            entity.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
            return;
        }
        entity.getNavigation().stop();

        tryPlaceBlock(sl, siteOrigin.offset(1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Items.CRAFTING_TABLE);
        tryPlaceBlock(sl, siteOrigin.offset(3, 1, 1), Blocks.FURNACE.defaultBlockState(), Items.FURNACE);
        tryPlaceBlock(sl, siteOrigin.offset(1, 1, 3), Blocks.CHEST.defaultBlockState(), Items.CHEST);
        // Bed requires two blocks — simplified: place red bed head+foot
        BlockPos bedFoot = siteOrigin.offset(3, 1, 3);
        if (sl.getBlockState(bedFoot).isAir() && hasInInventory(Items.RED_BED)) {
            sl.setBlock(bedFoot, Blocks.RED_BED.defaultBlockState(), 3);
            removeFromInventory(Items.RED_BED);
        }

        entity.setHomePos(siteOrigin.offset(2, 1, 2));
        phase = BuildPhase.DONE;
    }

    private java.util.List<BlockPos> buildOrder() {
        java.util.List<BlockPos> plan = new java.util.ArrayList<>();
        // Floor (y=0 relative to siteOrigin)
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            plan.add(siteOrigin.offset(x, 0, z));
        // Walls (y=1,2,3 on perimeter)
        for (int y = 1; y <= 3; y++)
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4)
                    plan.add(siteOrigin.offset(x, y, z));
            }
        // Roof (y=4)
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            plan.add(siteOrigin.offset(x, 4, z));
        // Door gap (remove wall at front center y=1 and y=2)
        plan.remove(siteOrigin.offset(2, 1, 0));
        plan.remove(siteOrigin.offset(2, 2, 0));
        return plan;
    }

    private void tryPlaceBlock(ServerLevel sl, BlockPos pos, BlockState state, net.minecraft.world.item.Item item) {
        if (sl.getBlockState(pos).isAir() && hasInInventory(item)) {
            sl.setBlock(pos, state, 3);
            removeFromInventory(item);
        }
    }

    private boolean hasInInventory(net.minecraft.world.item.Item item) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(item)) return true;
        return false;
    }

    private void removeFromInventory(net.minecraft.world.item.Item item) {
        var inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i); if (s.is(item)) { s.shrink(1); return; }
        }
    }

    private BlockPos findFlatSite() {
        if (!(entity.level() instanceof ServerLevel sl)) return null;
        BlockPos origin = entity.blockPosition();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = (entity.getRandom().nextInt(21) - 10);
            int dz = (entity.getRandom().nextInt(21) - 10);
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            int topY = sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos candidate = new BlockPos(x, topY, z);
            if (isFlatEnough(sl, candidate, 5)) return candidate;
        }
        return null;
    }

    private boolean isFlatEnough(ServerLevel sl, BlockPos corner, int size) {
        int refY = corner.getY();
        for (int dx = 0; dx < size; dx++) for (int dz = 0; dz < size; dz++) {
            int y = sl.getHeight(Heightmap.Types.WORLD_SURFACE, corner.getX() + dx, corner.getZ() + dz);
            if (Math.abs(y - refY) > 2) return false;
        }
        return true;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.BuildHomeGoal;
// ...
this.goalSelector.addGoal(2, new BuildHomeGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/BuildHomeGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add BuildHomeGoal for HOME_BUILDING stage"
```

---

### Task 14: PostGameGoal

**Files:**
- Create: `src/main/java/com/edcl/lovelyfriend/entity/goal/PostGameGoal.java`
- Modify: `entity/FriendEntity.java` (registerGoals)

**Interfaces:**
- Consumes: `entity.getGameStage()` (POST_GAME), `entity.getHomePos()`.
- Produces: AI randomly selects from objective pool (EXPLORE_BIOME, BUILD_FARM, FIND_OCEAN_MONUMENT, FIND_WOODLAND_MANSION, RETURN_HOME, IDLE_EXPLORE) and pursues them indefinitely.

- [ ] **Step 1: Create PostGameGoal.java**

```java
package com.edcl.lovelyfriend.entity.goal;

import com.edcl.lovelyfriend.entity.FriendEntity;
import com.edcl.lovelyfriend.entity.GameStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class PostGameGoal extends Goal {

    private enum Objective {
        EXPLORE_BIOME, FIND_OCEAN_MONUMENT, FIND_WOODLAND_MANSION, RETURN_HOME, IDLE_EXPLORE
    }

    private static final double ARRIVAL_DIST_SQ = 100.0;
    private static final int MAX_STUCK_TIME = 1200;
    private static final int[] WEIGHTS = {3, 1, 1, 2, 4}; // matches Objective ordinal order
    private static final int WEIGHT_SUM = 11;

    private final FriendEntity entity;
    private Objective current = Objective.IDLE_EXPLORE;
    private BlockPos targetPos;
    private int stuckTimer;
    private Vec3 lastPos;
    private int cooldown;

    public PostGameGoal(FriendEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (entity.getGameStage() != GameStage.POST_GAME) return false;
        if (entity.isLeashed() || entity.getTarget() != null) return false;
        if (cooldown-- > 0) return false;
        if (targetPos != null && !hasArrived()) return true;
        pickNewObjective();
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getGameStage() == GameStage.POST_GAME
            && entity.getTarget() == null && !entity.isLeashed() && targetPos != null;
    }

    @Override
    public void start() {
        stuckTimer = 0; lastPos = entity.position();
        if (targetPos != null) navigate();
    }

    @Override
    public void stop() { entity.getNavigation().stop(); cooldown = 200; }

    @Override
    public void tick() {
        if (targetPos == null) return;
        if (hasArrived()) { targetPos = null; cooldown = 200; return; }
        Vec3 cur = entity.position();
        if (cur.distanceToSqr(lastPos) < 0.05) { if (++stuckTimer > MAX_STUCK_TIME) { targetPos = null; cooldown = 200; return; } }
        else stuckTimer = 0;
        lastPos = cur;
        if (!entity.getNavigation().isInProgress()) navigate();
    }

    private void navigate() {
        entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0);
    }

    private boolean hasArrived() {
        return targetPos != null && entity.blockPosition().distSqr(targetPos) < ARRIVAL_DIST_SQ;
    }

    private void pickNewObjective() {
        // RETURN_HOME if far from home
        if (entity.getHomePos() != null && entity.blockPosition().distSqr(entity.getHomePos()) > 250000) {
            current = Objective.RETURN_HOME;
            targetPos = entity.getHomePos();
            return;
        }
        // Weighted random pick
        int roll = entity.getRandom().nextInt(WEIGHT_SUM);
        int acc = 0;
        Objective[] values = Objective.values();
        for (int i = 0; i < values.length; i++) {
            acc += WEIGHTS[i];
            if (roll < acc) { current = values[i]; break; }
        }
        if (!(entity.level() instanceof ServerLevel sl)) { targetPos = null; return; }
        targetPos = switch (current) {
            case FIND_OCEAN_MONUMENT -> sl.findNearestMapStructure(StructureTags.OCEAN_MONUMENTS, entity.blockPosition(), 64, false);
            case FIND_WOODLAND_MANSION -> sl.findNearestMapStructure(StructureTags.WOODLAND_MANSIONS, entity.blockPosition(), 64, false);
            case RETURN_HOME -> entity.getHomePos();
            default -> randomFarTarget(sl);
        };
    }

    private BlockPos randomFarTarget(ServerLevel sl) {
        BlockPos origin = entity.blockPosition();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dist = 200 + entity.getRandom().nextInt(400);
            float angle = entity.getRandom().nextFloat() * (float) Math.PI * 2;
            int x = origin.getX() + (int)(Math.cos(angle) * dist);
            int z = origin.getZ() + (int)(Math.sin(angle) * dist);
            if (!sl.isLoaded(new BlockPos(x, 0, z))) continue;
            int y = sl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (y > sl.getMinY()) return new BlockPos(x, y, z);
        }
        return null;
    }
}
```

- [ ] **Step 2: Register in FriendEntity.registerGoals()**

```java
import com.edcl.lovelyfriend.entity.goal.PostGameGoal;
// ...
this.goalSelector.addGoal(5, new PostGameGoal(this));
```

- [ ] **Step 3: Build and commit**

```
./gradlew build
git add src/main/java/com/edcl/lovelyfriend/entity/goal/PostGameGoal.java
git add src/main/java/com/edcl/lovelyfriend/entity/FriendEntity.java
git commit -m "feat: add PostGameGoal for infinite POST_GAME exploration"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|---|---|
| GameStage enum (9 stages) | Task 1 |
| PlayMode enum | Task 1 |
| FriendEntity fields + NBT | Task 2 |
| PlayMode auto-switch (100 ticks) | Task 2 |
| CURRENT_GOAL format `[STAGE] goal` | Task 2 |
| StageAdvancerGoal | Task 3 |
| Stage gates on existing goals | Task 3 |
| COMPANION mode priority for Follow/Share | Task 3 |
| MineStoneGoal | Task 4 |
| MineIronGoal | Task 5 |
| SmeltOreGoal | Task 6 |
| MineDiamondGoal | Task 7 |
| BuildNetherPortalGoal | Task 8 |
| FindNetherFortressGoal + KillBlazeGoal | Task 9 |
| GatherEnderPearlsGoal | Task 10 |
| FindStrongholdGoal + ActivateEndPortalGoal | Task 11 |
| FightEnderDragonGoal | Task 12 |
| BuildHomeGoal | Task 13 |
| PostGameGoal with weighted objective pool | Task 14 |

**Known issue flagged in Task 12:** End Crystal class name needs to be verified for MC 26.2. The plan notes this with a `ponytail:` comment.

**Type consistency:** All goals use `entity.getGameStage()` (defined in Task 2). All goals use `entity.getInventory()` (existing). `entity.setHomePos(BlockPos)` defined in Task 2, consumed in Task 13. `entity.getHomePos()` defined in Task 2, consumed in Task 14. ✓
