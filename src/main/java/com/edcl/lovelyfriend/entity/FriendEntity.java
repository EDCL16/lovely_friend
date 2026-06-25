package com.edcl.lovelyfriend.entity;

import com.edcl.lovelyfriend.entity.goal.CollectItemOnGroundGoal;
import com.edcl.lovelyfriend.entity.goal.EatFoodGoal;
import com.edcl.lovelyfriend.entity.goal.FollowPlayerGoal;
import com.edcl.lovelyfriend.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
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
            "entity/female/1",  "entity/female/2",  "entity/female/3",  "entity/female/4",
            "entity/female/5",  "entity/female/6",  "entity/female/7",  "entity/female/8",
            "entity/female/9",  "entity/female/10", "entity/female/11", "entity/female/12",
            "entity/female/13", "entity/female/14", "entity/female/15", "entity/female/16",
            "entity/female/17", "entity/female/18", "entity/female/19", "entity/female/20",
            "entity/female/21", "entity/female/22", "entity/female/23", "entity/female/24",
            "entity/female/25", "entity/female/26", "entity/female/27", "entity/female/28",
            "entity/female/29", "entity/female/30", "entity/female/31", "entity/female/32",
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

    private boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof AxeItem;
    }

    private boolean isBetterWeapon(ItemStack newItem, ItemStack oldItem) {
        return getWeaponScore(newItem) > getWeaponScore(oldItem);
    }

    private int getWeaponScore(ItemStack stack) {
        int typeScore = 0;
        if (stack.is(ItemTags.SWORDS)) typeScore = 2;
        else if (stack.getItem() instanceof AxeItem) typeScore = 1;

        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double attackDamage = mods.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);

        return (int)(attackDamage * 10) + typeScore;
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

    // ---- Persistence (ValueOutput/ValueInput) ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("SelectedTexture", selectedTexture);
        output.putInt("FoodLevel", foodLevel);

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
        selectedTexture = input.getStringOr("SelectedTexture", "");
        if (!TEXTURES.contains(selectedTexture)) {
            selectedTexture = TEXTURES.get(this.random.nextInt(TEXTURES.size()));
        }
        foodLevel = input.getIntOr("FoodLevel", MAX_FOOD_LEVEL);

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
