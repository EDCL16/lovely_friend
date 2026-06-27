package com.edcl.lovelyfriend.entity;

import com.edcl.lovelyfriend.entity.goal.BreakBlockGoal;
import com.edcl.lovelyfriend.entity.goal.StageAdvancerGoal;
import com.edcl.lovelyfriend.entity.goal.BreedFriendGoal;
import com.edcl.lovelyfriend.entity.goal.ChopTreeGoal;
import com.edcl.lovelyfriend.entity.goal.CraftWoodenToolsGoal;
import com.edcl.lovelyfriend.entity.goal.MineStoneGoal;
import com.edcl.lovelyfriend.entity.goal.MineIronGoal;
import com.edcl.lovelyfriend.entity.goal.DiggingEscapeGoal;
import com.edcl.lovelyfriend.entity.goal.ExtinguishFireGoal;
import com.edcl.lovelyfriend.entity.goal.FishingGoal;
import com.edcl.lovelyfriend.entity.goal.PlaceBlockToClimbGoal;
import com.edcl.lovelyfriend.entity.goal.CollectItemOnGroundGoal;
import com.edcl.lovelyfriend.entity.goal.ContemplateLifeGoal;
import com.edcl.lovelyfriend.entity.goal.EatFoodGoal;
import com.edcl.lovelyfriend.entity.goal.EquipBestToolGoal;
import com.edcl.lovelyfriend.entity.goal.ExploreGoal;
import com.edcl.lovelyfriend.entity.goal.FleeDangerGoal;
import com.edcl.lovelyfriend.entity.goal.FindFoodGoal;
import com.edcl.lovelyfriend.entity.goal.FollowPlayerGoal;
import com.edcl.lovelyfriend.entity.goal.HuntLivestockGoal;
import com.edcl.lovelyfriend.entity.goal.LeashMobGoal;
import com.edcl.lovelyfriend.entity.goal.PlantCropGoal;
import com.edcl.lovelyfriend.entity.goal.RideFriendGoal;
import com.edcl.lovelyfriend.entity.goal.ShareArmorGoal;
import com.edcl.lovelyfriend.entity.goal.ShareFoodGoal;
import com.edcl.lovelyfriend.entity.goal.ShareWeaponGoal;
import com.edcl.lovelyfriend.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.tags.ItemTags;

import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.List;

public class FriendEntity extends PathfinderMob implements RangedAttackMob {

    private static final int MAX_FOOD_LEVEL = 20;
    private static final int HUNGER_DAMAGE_THRESHOLD = 5;
    private static final int DROP_FOOD_LEVEL_TICKS = 600;
    private static final int HUNGER_DAMAGE_TICKS = 50;
    private static final int GOAL_UPDATE_INTERVAL = 20;

    private static final EntityDataAccessor<String> TEXTURE_ID =
            SynchedEntityData.defineId(FriendEntity.class, EntityDataSerializers.STRING);
    
    private static final EntityDataAccessor<String> CURRENT_GOAL =
            SynchedEntityData.defineId(FriendEntity.class, EntityDataSerializers.STRING);

    private final SimpleContainer inventory = new SimpleContainer(100);
    private int foodLevel = MAX_FOOD_LEVEL;
    private int foodTickTimer = 0;
    private int hungerDamageTimer = 0;
    private int goalUpdateTimer = 0;
    private String lastGoalName = "";
    private int breedCooldown = 0;
    private int regenTimer = 0;
    private GameStage gameStage = GameStage.WOOD;
    @Nullable private BlockPos homePos;
    private PlayMode playMode = PlayMode.AUTONOMOUS;
    private int playModeCheckTimer = 0;
    private boolean endPortalActivated = false;

    public boolean isBreedingOnCooldown() { return breedCooldown > 0; }
    public void setBreedCooldown(int ticks) { breedCooldown = ticks; }

    public GameStage getGameStage() { return gameStage; }
    public PlayMode getPlayMode() { return playMode; }
    @Nullable public BlockPos getHomePos() { return homePos; }
    public void setHomePos(BlockPos pos) { this.homePos = pos; }

    public void advanceStage() {
        gameStage = gameStage.next();
    }

    public boolean isEndPortalActivated() { return endPortalActivated; }
    public void setEndPortalActivated(boolean v) { this.endPortalActivated = v; }

    private static final List<String> TEXTURES = Arrays.asList(
            "entity/female/1",  "entity/female/2",  "entity/female/3",  "entity/female/4",
            "entity/female/5",  "entity/female/6",  "entity/female/7",  "entity/female/8",
            "entity/female/9",  "entity/female/10", "entity/female/11", "entity/female/12",
            "entity/female/13", "entity/female/14", "entity/female/15", "entity/female/16",
            "entity/female/17", "entity/female/18", "entity/female/19", "entity/female/20",
            "entity/female/21", "entity/female/22", "entity/female/23", "entity/female/24",
            "entity/female/25", "entity/female/26", "entity/female/27", "entity/female/28",
            "entity/female/29", "entity/female/30", "entity/female/31", "entity/female/32",
            "entity/female/33", "entity/female/34", "entity/female/35", "entity/female/36",
            "entity/female/37", "entity/female/38", "entity/female/39", "entity/female/40",
            "entity/female/41", "entity/female/42", "entity/female/43", "entity/female/44",
            "entity/female/45", "entity/female/46", "entity/female/47", "entity/female/48",
            "entity/female/49", "entity/female/50", "entity/female/51", "entity/female/52",
            "entity/female/53", "entity/female/54", "entity/female/55", "entity/female/56",
            "entity/female/57", "entity/female/58", "entity/female/59", "entity/female/60",
            "entity/female/61", "entity/female/62", "entity/female/63", "entity/female/64",
            "entity/female/65", "entity/female/66", "entity/female/67", "entity/female/68",
            "entity/female/69", "entity/female/70", "entity/female/71", "entity/female/72",
            "entity/female/73", "entity/female/74", "entity/female/75", "entity/female/76",
            "entity/female/77",
            "entity/hololive/anyanya", "entity/hololive/ayame",  "entity/hololive/botan",
            "entity/hololive/coco",    "entity/hololive/kanata", "entity/hololive/kobo",
            "entity/hololive/korone",  "entity/hololive/marine", "entity/hololive/miko",
            "entity/hololive/pekora",  "entity/hololive/rushia", "entity/hololive/subaru",
            "entity/hololive/susei",   "entity/hololive/towa",
            "entity/myth/ame",      "entity/myth/calliope", "entity/myth/gura",
            "entity/myth/ina",      "entity/myth/kiara",
            "entity/promise/baelz", "entity/promise/fauna",  "entity/promise/irys",
            "entity/promise/kronii","entity/promise/mumei"
    );

    public FriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TEXTURE_ID, TEXTURES.get(0));
        builder.define(CURRENT_GOAL, "");
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
        // 0: 核心本能 / 狀態改變 (避免淹死、吃食物、裝備工具、尋找食物)
        this.goalSelector.addGoal(0, new StageAdvancerGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new EatFoodGoal(this));
        this.goalSelector.addGoal(0, new EquipBestToolGoal(this));
        this.goalSelector.addGoal(0, new FindFoodGoal(this));

        // 3: 環境互動 (撿東西、騎乘朋友、挖開阻礙、搭方塊爬高、製作工具)
        this.goalSelector.addGoal(3, new CollectItemOnGroundGoal(this));
        this.goalSelector.addGoal(3, new RideFriendGoal(this));
        this.goalSelector.addGoal(3, new DiggingEscapeGoal(this));
        this.goalSelector.addGoal(3, new PlaceBlockToClimbGoal(this));
        this.goalSelector.addGoal(3, new CraftWoodenToolsGoal(this));
        this.goalSelector.addGoal(3, new MineStoneGoal(this));
        this.goalSelector.addGoal(3, new MineIronGoal(this));

        // 3.5: 自主行為 (狩獵、種植、砍樹)
        this.goalSelector.addGoal(3, new HuntLivestockGoal(this));
        this.goalSelector.addGoal(3, new PlantCropGoal(this));
        this.goalSelector.addGoal(4, new ChopTreeGoal(this));

        // 1: 致命危險防禦 (滅火、逃離爆炸、火源)
        this.goalSelector.addGoal(1, new ExtinguishFireGoal(this));
        this.goalSelector.addGoal(1, new FleeDangerGoal(this));

        // 2: 戰鬥行為 (遠程攻擊、近戰攻擊、追擊目標)
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 15.0f) {
            @Override public boolean canUse() {
                return FriendEntity.this.hasRangedWeaponAndAmmo() && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return FriendEntity.this.hasRangedWeaponAndAmmo() && super.canContinueToUse();
            }
            @Override public void start() {
                FriendEntity.this.equipRangedWeapon();
                super.start();
                if (FriendEntity.this.getMainHandItem().getItem() instanceof BowItem) {
                    FriendEntity.this.startUsingItem(InteractionHand.MAIN_HAND);
                }
            }
            @Override public void stop() {
                if (FriendEntity.this.isUsingItem()) FriendEntity.this.stopUsingItem();
                FriendEntity.this.restoreWeapon();
                super.stop();
            }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 1.0, 32.0f));


        // 4: 環境互動 / 搜刮 (破壞方塊、釣魚、分享裝備/盔甲/食物、栓繩套動物)
        this.goalSelector.addGoal(4, new BreakBlockGoal(this));
        this.goalSelector.addGoal(4, new FishingGoal(this));
        this.goalSelector.addGoal(2, new ShareWeaponGoal(this) {
            @Override public boolean canUse() {
                return FriendEntity.this.getPlayMode() == PlayMode.COMPANION && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new ShareArmorGoal(this) {
            @Override public boolean canUse() {
                return FriendEntity.this.getPlayMode() == PlayMode.COMPANION && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new ShareFoodGoal(this) {
            @Override public boolean canUse() {
                return FriendEntity.this.getPlayMode() == PlayMode.COMPANION && super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new LeashMobGoal(this));

        // 5: 自主生活 / 閒逛 (繁殖、村莊徘徊、隨機走動、探索新生態域)
        this.goalSelector.addGoal(5, new BreedFriendGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.2) {
            @Override public boolean canUse() {
                if (FriendEntity.this.isLeashed() && FriendEntity.this.getRandom().nextInt(10) != 0) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new StrollThroughVillageGoal(this, 20) {
            @Override public boolean canUse() {
                if (FriendEntity.this.isLeashed()) return false;
                return super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new ExploreGoal(this));
        this.goalSelector.addGoal(5, new ContemplateLifeGoal(this));

        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, FriendEntity.class, 8.0f));
        this.goalSelector.addGoal(3, new FollowPlayerGoal(this, 0.5, 20.0, 3.0) {
            @Override public boolean canUse() {
                return FriendEntity.this.getPlayMode() == PlayMode.COMPANION && super.canUse();
            }
        });
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, true,
                (entity, level) -> !(entity instanceof Creeper)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, true));
    }

    // ---- Hunger System ----

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            reduceHunger();
            applyHungerDamage();
            applyNaturalRegen();
            updateCurrentGoalDisplay();
            if (breedCooldown > 0) breedCooldown--;
            if (++playModeCheckTimer >= 100) {
                playModeCheckTimer = 0;
                boolean playerNearby = this.level().getNearestPlayer(this, 32) != null;
                playMode = playerNearby ? PlayMode.COMPANION : PlayMode.AUTONOMOUS;
            }
        }
    }

    private void updateCurrentGoalDisplay() {
        goalUpdateTimer++;
        if (goalUpdateTimer < GOAL_UPDATE_INTERVAL) return;
        goalUpdateTimer = 0;

        String newGoal = getActiveGoalName();
        if (!newGoal.equals(lastGoalName)) {
            lastGoalName = newGoal;
            this.getEntityData().set(CURRENT_GOAL, "[" + gameStage.name() + "] " + newGoal);
        }
    }

    public String getActiveGoalName() {
        // Note: full goal detection requires more complex tracking.
        // Returns lastGoalName (the raw goal without stage prefix) or "Idle".
        // Future tasks will replace this with actual goal-selector inspection.
        return lastGoalName.isEmpty() ? "Idle" : lastGoalName;
    }

    public String getCurrentGoalDisplay() {
        return this.getEntityData().get(CURRENT_GOAL);
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

    private void applyNaturalRegen() {
        if (foodLevel >= MAX_FOOD_LEVEL - 2 && this.getHealth() < this.getMaxHealth()) {
            if (++regenTimer >= 80) {
                regenTimer = 0;
                this.heal(1.0f);
                foodLevel = Math.max(0, foodLevel - 1);
            }
        } else {
            regenTimer = 0;
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
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void eatFood(ItemStack food) {
        FoodProperties props = food.get(DataComponents.FOOD);
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
    protected void pickUpItem(ServerLevel serverLevel, ItemEntity itemEntity) {
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
            itemEntity.discard();
            return;
        }
        if (stack.getItem() instanceof FishingRodItem && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            itemEntity.discard();
            return;
        }

        ItemStack remaining = addToInventory(stack);
        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
    }

    public void collectItem(ItemEntity itemEntity) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.pickUpItem(serverLevel, itemEntity);
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
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            if (slot.isArmor()) {
                ItemStack current = this.getItemBySlot(slot);
                if (current.isEmpty() || isBetterArmor(stack, current)) {
                    if (!current.isEmpty()) {
                        addToInventory(current);
                    }
                    this.setItemSlot(slot, stack.copy());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBetterArmor(ItemStack newItem, ItemStack oldItem) {
        ItemAttributeModifiers newMods = newItem.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers oldMods = oldItem.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Equippable equippable = newItem.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return false;
        EquipmentSlot slot = equippable.slot();
        double newArmor = newMods.compute(Attributes.ARMOR, 0.0, slot);
        double oldArmor = oldMods.compute(Attributes.ARMOR, 0.0, slot);
        return newArmor > oldArmor;
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

    public boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || isTrident(stack);
    }

    private boolean isTrident(ItemStack stack) {
        return stack.getItem() instanceof TridentItem;
    }

    public boolean isBetterWeapon(ItemStack newItem, ItemStack oldItem) {
        return getWeaponScore(newItem) > getWeaponScore(oldItem);
    }

    public int getWeaponScore(ItemStack stack) {
        int typeScore = 0;
        if (stack.is(ItemTags.SWORDS)) typeScore = 4;
        else if (stack.is(ItemTags.AXES)) typeScore = 3;
        else if (stack.is(ItemTags.PICKAXES)) typeScore = 2;
        else if (stack.is(ItemTags.SHOVELS)) typeScore = 1;
        else if (stack.is(ItemTags.HOES)) typeScore = 1;
        else if (isTrident(stack)) typeScore = 5;

        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double attackDamage = mods.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);

        return (int)(attackDamage * 10) + typeScore;
    }

    // ---- Ranged combat ----

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        ItemStack hand = this.getMainHandItem();
        if (!(hand.getItem() instanceof BowItem) && !(hand.getItem() instanceof CrossbowItem)) return;

        ItemStack ammo = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(ItemTags.ARROWS)) { ammo = s; break; }
        }
        if (ammo.isEmpty()) return;

        Arrow arrow = new Arrow(serverLevel, this, ammo.copyWithCount(1), hand);
        double dx = target.getX() - this.getX();
        double dy = target.getEyeY() - arrow.getY() - 0.1;
        double dz = target.getZ() - this.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizDist * 0.2, dz, pullProgress * 3.0f, 1.0f);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        serverLevel.addFreshEntity(arrow);
        ammo.shrink(1);

        // Restart bow-drawing animation for the next shot
        if (hand.getItem() instanceof BowItem) {
            this.stopUsingItem();
            this.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    public boolean isRangedWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem);
    }

    public boolean hasRangedWeaponAndAmmo() {
        boolean hasWeapon = isRangedWeapon(this.getMainHandItem());
        if (!hasWeapon) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (isRangedWeapon(inventory.getItem(i))) { hasWeapon = true; break; }
            }
        }
        if (!hasWeapon) return false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ItemTags.ARROWS)) return true;
        }
        return false;
    }

    public boolean equipRangedWeapon() {
        if (isRangedWeapon(this.getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isRangedWeapon(stack)) {
                ItemStack hand = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                inventory.setItem(i, hand);
                return true;
            }
        }
        return false;
    }

    // ---- Fishing helpers ----

    public boolean hasFishingRod() {
        if (this.getMainHandItem().getItem() instanceof FishingRodItem) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof FishingRodItem) return true;
        }
        return false;
    }

    public boolean equipFishingRod() {
        if (this.getMainHandItem().getItem() instanceof FishingRodItem) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof FishingRodItem) {
                ItemStack hand = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                inventory.setItem(i, hand);
                return true;
            }
        }
        return false;
    }

    // ---- Mining and Tool helpers ----

    public boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.AXES);
    }

    public boolean hasAxe() {
        if (isAxe(this.getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isAxe(inventory.getItem(i))) return true;
        }
        return false;
    }

    public boolean equipAxe() {
        if (isAxe(this.getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isAxe(stack)) {
                ItemStack hand = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                inventory.setItem(i, hand);
                return true;
            }
        }
        return false;
    }

    public boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
    }

    public boolean hasPickaxe() {
        if (isPickaxe(this.getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isPickaxe(inventory.getItem(i))) return true;
        }
        return false;
    }

    public boolean equipPickaxe() {
        if (isPickaxe(this.getMainHandItem())) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isPickaxe(stack)) {
                ItemStack hand = this.getMainHandItem().copy();
                this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                inventory.setItem(i, hand);
                return true;
            }
        }
        return false;
    }

    public void restoreWeapon() {
        int bestSlot = -1;
        ItemStack bestWeapon = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isWeapon(stack)) {
                if (bestWeapon.isEmpty() || isBetterWeapon(stack, bestWeapon)) {
                    bestWeapon = stack;
                    bestSlot = i;
                }
            }
        }

        ItemStack hand = this.getMainHandItem();
        if (!bestWeapon.isEmpty()) {
            ItemStack oldHand = hand.copy();
            this.setItemSlot(EquipmentSlot.MAINHAND, bestWeapon.copy());
            inventory.setItem(bestSlot, oldHand);
        } else if (isPickaxe(hand)) {
            ItemStack remaining = addToInventory(hand.copy());
            if (remaining.isEmpty()) {
                this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
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
        // 5% chance to drop a Friendship Stone
        if (this.random.nextFloat() < 0.05f) {
            this.spawnAtLocation(level, new ItemStack(ModItems.FRIENDSHIP_STONE));
        }
        super.dropAllDeathLoot(level, damageSource);
    }

    // ---- Leash Support ----
    // FriendEntity can be leashed by players using a lead.
    // The Mob base class handles the leash logic via setLeashedTo/dropLeash.
    // We just need to make sure the leash knot rendering works properly.

    @Override
    public boolean canBeLeashed() {
        return !this.isLeashed();
    }

    // ---- Riding Support ----

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    // ---- Texture System ----

    public String getSelectedTexture() {
        return this.getEntityData().get(TEXTURE_ID);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
        this.getEntityData().set(TEXTURE_ID, TEXTURES.get(this.random.nextInt(TEXTURES.size())));
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    // ---- Spawn Rules ----

    public static boolean canSpawn(EntityType<FriendEntity> type, ServerLevelAccessor level,
                                    EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        return serverLevel.isVillage(pos);
    }

    // ---- Persistence (ValueOutput/ValueInput) ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("SelectedTexture", this.getEntityData().get(TEXTURE_ID));
        output.putInt("FoodLevel", foodLevel);
        output.putString("GameStage", gameStage.name());
        if (homePos != null) {
            output.store("HomePos", BlockPos.CODEC, homePos);
        }
        output.putBoolean("EndPortalActivated", endPortalActivated);

        ValueOutput.ValueOutputList inventoryList = output.childrenList("Inventory");
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                ValueOutput child = inventoryList.addChild();
                child.putInt("Slot", i);
                child.store("Item", ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String tex = input.getStringOr("SelectedTexture", "");
        if (!TEXTURES.contains(tex)) {
            tex = TEXTURES.get(this.random.nextInt(TEXTURES.size()));
        }
        this.getEntityData().set(TEXTURE_ID, tex);
        foodLevel = input.getIntOr("FoodLevel", MAX_FOOD_LEVEL);
        try {
            gameStage = GameStage.valueOf(input.getStringOr("GameStage", "WOOD"));
        } catch (IllegalArgumentException e) {
            gameStage = GameStage.WOOD;
        }
        input.read("HomePos", BlockPos.CODEC).ifPresent(pos -> homePos = pos);
        endPortalActivated = input.getBooleanOr("EndPortalActivated", false);

        ValueInput.ValueInputList inventoryList = input.childrenListOrEmpty("Inventory");
        for (ValueInput child : inventoryList) {
            int slot = child.getIntOr("Slot", -1);
            child.read("Item", ItemStack.CODEC).ifPresent(stack -> {
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, stack);
                }
            });
        }
    }
}
