package com.googlerooeric.anvilfix;

import net.minecraft.item.ItemStack;

public class AnvilData {
    public ItemStack item, modifier, result;
    public int cost;

    public AnvilData(ItemStack item, ItemStack modifier) {
        this.item = item;
        this.modifier = modifier;
        this.result = item.copy();
        this.cost = 0;
    }

    public boolean canItemBeRepairedByModifier() {
        return item.isDamageable() && item.getItem().canRepair(item, modifier);
    }
}
