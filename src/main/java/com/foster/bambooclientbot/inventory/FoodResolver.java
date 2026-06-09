package com.foster.bambooclientbot.inventory;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

public class FoodResolver {
    public boolean isEdible(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }
}
