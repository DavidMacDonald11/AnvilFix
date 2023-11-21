package com.googlerooeric.anvilfix.mixin.screen;

import com.googlerooeric.anvilfix.AnvilData;
import com.googlerooeric.anvilfix.AnvilFix;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
    @Shadow @Final private Property levelCost;
    @Shadow private int repairItemUsage;
    @Shadow private String newItemName;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    @Unique
    private static final Map<Enchantment, Integer> ENCHANTMENT_COST_MAP = new HashMap<>();

    @Unique
    private static final Set<Set<Enchantment>> ENCHANTMENT_INCOMPATIBILITY_SETS = new HashSet<>();

    static {
        // Populate the map with enchantments and their base costs
        // Example: ENCHANTMENT_COST_MAP.put(Enchantments.SHARPNESS, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.SHARPNESS, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.EFFICIENCY, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.MENDING, 15);
        ENCHANTMENT_COST_MAP.put(Enchantments.UNBREAKING, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.FORTUNE, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.FIRE_ASPECT, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.SWEEPING, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.INFINITY, 8);
        ENCHANTMENT_COST_MAP.put(Enchantments.SILK_TOUCH, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.THORNS, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.FLAME, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.AQUA_AFFINITY, 6);
        ENCHANTMENT_COST_MAP.put(Enchantments.LUCK_OF_THE_SEA, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.PUNCH, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.RESPIRATION, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.KNOCKBACK, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.LOOTING, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.LURE, 3);

        // Populate the incompatibility sets with groups of incompatible enchantments
        // Note: Does not include trident enchants since channeling + loyalty is valid
        //       Does not include mending + unbreaking, since it depends on gamerule
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.FORTUNE, Enchantments.SILK_TOUCH));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.FROST_WALKER, Enchantments.DEPTH_STRIDER));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.INFINITY, Enchantments.MENDING));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.MULTISHOT, Enchantments.PIERCING));
    }

    /**
     * @author googler_ooeric
     * @reason Make it so repairing doesn't increase cost.
     */
    @Overwrite
    public static int getNextCost(int cost) {
        return cost; // Return the cost as it is
    }

    /**
     * @author DavidMacDonald11
     * @reason Change how anvils work to make more sense
     */
    @Overwrite
    public void updateResult() {
        var item = this.input.getStack(0);

        if(item.isEmpty()) {
            this.output.setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            return;
        }

        this.repairItemUsage = 0;
        var modifier = this.input.getStack(1);
        var data = new AnvilData(item, modifier);

        if(!modifier.isEmpty()) {
            repairAndEnchantItem(data);
        }

        renameItem(data);
        setFinalResult(data);
    }

    @Unique
    private void repairAndEnchantItem(AnvilData data) {
        if(data.canItemBeRepairedByModifier()) {
            repairItem(data);
            return;
        }

        var itemIsEnchantedBook = data.item.isOf(Items.ENCHANTED_BOOK);
        var modifierIsEnchantedBook = data.modifier.isOf(Items.ENCHANTED_BOOK);

        if(!itemIsEnchantedBook && !modifierIsEnchantedBook) {
            mergeItems(data);
        } else if(modifierIsEnchantedBook) {
            addEnchantsToItem(data);
        }
    }

    @Unique
    private void renameItem(AnvilData data) {
        if(!data.modifier.isEmpty() && data.cost == 0) { return; }

        if(StringUtils.isBlank(this.newItemName) || this.newItemName == null) {
            if(data.item.hasCustomName()) {
                data.result.removeCustomName();
                data.cost += 2;
            }

            return;
        }

        if(!this.newItemName.equals(data.item.getName().getString())) {
            data.result.setCustomName(Text.literal(this.newItemName));
            data.cost += 2;
        }
    }

    @Unique
    private void setFinalResult(AnvilData data) {
        var result = (data.cost == 0)? ItemStack.EMPTY : data.result;

        this.levelCost.set(data.cost);
        this.output.setStack(0, result);
        this.sendContentUpdates();
    }

    @Unique
    private void repairItem(AnvilData data) {
        var damage = data.item.getDamage();
        var damageReductionPerRepair = data.item.getMaxDamage() / 4;

        var desiredRepairs = (int)Math.ceil((double)damage / damageReductionPerRepair);
        var possibleRepairs = data.modifier.getCount();
        var repairs = Math.min(desiredRepairs, possibleRepairs);

        var maxDamageReduction = repairs * damageReductionPerRepair;
        var damageReduction = Math.min(damage, maxDamageReduction);

        data.result.setDamage(damage - damageReduction);
        data.cost += 2 * repairs;
        this.repairItemUsage = repairs;
    }

    @Unique
    private void mergeItems(AnvilData data) {
        if(!ItemStack.areItemsEqualIgnoreDamage(data.item, data.modifier)) { return; }
        addEnchantsToItem(data);

        var maxHealth = data.item.getMaxDamage();
        var itemHealth = maxHealth - data.item.getDamage();
        var modifierHealth = maxHealth - data.modifier.getDamage();

        if(itemHealth != maxHealth) {
            var combinedHealth = (int)(itemHealth + modifierHealth + maxHealth * .12);
            var newHealth = Math.min(maxHealth, combinedHealth);

            data.result.setDamage(maxHealth - newHealth);
            data.cost += 2;
        }
    }

    @Unique
    private void addEnchantsToItem(AnvilData data) {
        var enchants = new HashMap<>(EnchantmentHelper.get(data.item));

        var itemIsBook = data.item.isOf(Items.BOOK) || data.item.isOf(Items.ENCHANTED_BOOK);
        var newEnchants = EnchantmentHelper.get(data.modifier);

        for(var newEnchant : newEnchants.keySet()) {
            if(!itemIsBook && !newEnchant.isAcceptableItem(data.item)) { continue; }
            int newLevel = newEnchants.get(newEnchant);

            if(enchants.containsKey(newEnchant)) {
                data.cost += mergeEnchantmentLevels(enchants, newEnchant, newLevel);
            } else {
                data.cost += addNewEnchantment(enchants, newEnchant, newLevel);
            }
        }

        EnchantmentHelper.set(enchants, data.result);
    }

    @Unique
    private int mergeEnchantmentLevels(Map<Enchantment, Integer> enchants, Enchantment newEnchant, int newLevel) {
        int level = enchants.get(newEnchant);
        if(level == newLevel && level < newEnchant.getMaxLevel()) { newLevel++; }

        if(level < newLevel) {
            enchants.put(newEnchant, newLevel);
            return ENCHANTMENT_COST_MAP.getOrDefault(newEnchant, 2) * newLevel;
        }

        return 0;
    }

    @Unique
    private int addNewEnchantment(Map<Enchantment, Integer> enchants, Enchantment newEnchant, int newLevel) {
        var incompatibilitySet = getIncompatibiltySet(newEnchant);
        var enchantKeys = enchants.keySet();

        if(Collections.disjoint(enchantKeys, incompatibilitySet)) {
            enchants.put(newEnchant, newLevel);
            return ENCHANTMENT_COST_MAP.getOrDefault(newEnchant, 2) * newLevel;
        }

        return 0;
    }

    @Unique
    private Set<Enchantment> getIncompatibiltySet(Enchantment enchant) {
        var specialTridentEnchants = Set.of(Enchantments.CHANNELING, Enchantments.LOYALTY);

        if(specialTridentEnchants.contains(enchant)) { return Set.of(Enchantments.RIPTIDE); }
        if(enchant.equals(Enchantments.RIPTIDE)) { return specialTridentEnchants; }

        if(!doesMendingWorkWithUnbreaking()) {
            if(enchant.equals(Enchantments.MENDING)) { return Set.of(Enchantments.UNBREAKING); }
            if(enchant.equals(Enchantments.UNBREAKING)) { return Set.of(Enchantments.MENDING); }
        }

        for(var set : ENCHANTMENT_INCOMPATIBILITY_SETS) {
            if(set.contains(enchant)) { return set; }
        }

        return Set.of();
    }

    @Unique
    private boolean doesMendingWorkWithUnbreaking() {
        var result = new AtomicBoolean(false);
        this.context.run((world, pos) -> result.set(world.getGameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING)));

        return result.get();
    }
}