package com.foster.bambooclientbot.reflex;

import com.foster.bambooclientbot.actions.ActionExecutor;
import com.foster.bambooclientbot.inventory.FoodResolver;
import com.foster.bambooclientbot.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;

public class AutoEatReflex implements Reflex {
    public static final int DEFAULT_HUNGER_THRESHOLD = 12;
    public static final int DEFAULT_PREFERRED_HOTBAR_SLOT = 8;

    private static final long COOLDOWN_MILLIS = 5_000L;
    private static final int HOTBAR_START = 0;
    private static final int HOTBAR_END = 8;
    private static final int INVENTORY_START = 9;
    private static final int INVENTORY_END = 35;

    private final FoodResolver foodResolver = new FoodResolver();
    private final int hungerThreshold;
    private final int preferredHotbarSlot;
    private int previousSlot = -1;

    public AutoEatReflex() {
        this(DEFAULT_HUNGER_THRESHOLD, DEFAULT_PREFERRED_HOTBAR_SLOT);
    }

    public AutoEatReflex(int hungerThreshold, int preferredHotbarSlot) {
        this.hungerThreshold = hungerThreshold;
        this.preferredHotbarSlot = preferredHotbarSlot;
    }

    @Override
    public String name() {
        return "auto_eat";
    }

    @Override
    public boolean isEnabled(BotState state) {
        return true;
    }

    @Override
    public void tick(MinecraftClient client, BotState state, ActionExecutor actions) {
        if (client == null || client.player == null || client.options == null) {
            if (state.autoEatActive()) {
                stopEating(client, state, actions, "hunger_ok");
            } else {
                state.setLastAutoEatResult("hunger_ok");
            }
            return;
        }

        ClientPlayerEntity player = client.player;

        if (state.autoEatActive()) {
            tickActiveEating(client, state, actions, player);
            return;
        }

        if (player.isDead() || !player.isAlive()) {
            state.setLastAutoEatResult("hunger_ok");
            return;
        }

        if (isContainerOpen(client)) {
            state.setLastAutoEatResult("container_open");
            return;
        }

        if (player.getHungerManager().getFoodLevel() > hungerThreshold) {
            state.setLastAutoEatResult("hunger_ok");
            return;
        }

        if (player.isUsingItem()) {
            state.setLastAutoEatResult("already_eating");
            return;
        }

        long now = System.currentTimeMillis();

        if (now - state.lastAutoEatTimeMillis() < COOLDOWN_MILLIS) {
            return;
        }

        int foodSlot = findHotbarFoodSlot(player.getInventory());

        if (foodSlot < 0) {
            foodSlot = moveInventoryFoodToHotbar(client, actions, player.getInventory());
        }

        if (foodSlot < 0) {
            state.setLastAutoEatTimeMillis(now);
            state.setLastAutoEatResult("no_food_found");
            return;
        }

        startEating(client, state, actions, player, foodSlot, now);
    }

    private void tickActiveEating(MinecraftClient client, BotState state, ActionExecutor actions, ClientPlayerEntity player) {
        if (player.isDead() || !player.isAlive()) {
            stopEating(client, state, actions, "hunger_ok");
            return;
        }

        if (isContainerOpen(client)) {
            stopEating(client, state, actions, "container_open");
            return;
        }

        if (player.getHungerManager().getFoodLevel() > hungerThreshold && !player.isUsingItem()) {
            stopEating(client, state, actions, "ate_food");
            return;
        }

        if (!player.isUsingItem() && !foodResolver.isEdible(player.getMainHandStack())) {
            stopEating(client, state, actions, "ate_food");
            return;
        }

        actions.setReflexUse(client, true, "auto_eat");
    }

    private void startEating(MinecraftClient client, BotState state, ActionExecutor actions,
                             ClientPlayerEntity player, int foodSlot, long now) {
        PlayerInventory inventory = player.getInventory();
        previousSlot = inventory.getSelectedSlot();
        actions.selectHotbarSlot(client, foodSlot);
        actions.setReflexUse(client, true, "auto_eat");
        state.setAutoEatActive(true);
        state.setLastAutoEatTimeMillis(now);
        state.setLastAutoEatResult("already_eating");
    }

    private void stopEating(MinecraftClient client, BotState state, ActionExecutor actions, String result) {
        actions.setReflexUse(client, false, "auto_eat");

        if (client != null && client.player != null && previousSlot >= 0
                && PlayerInventory.isValidHotbarIndex(previousSlot)) {
            actions.selectHotbarSlot(client, previousSlot);
        }

        previousSlot = -1;
        state.setAutoEatActive(false);
        state.setLastAutoEatTimeMillis(System.currentTimeMillis());
        state.setLastAutoEatResult(result);
    }

    private int findHotbarFoodSlot(PlayerInventory inventory) {
        for (int slot = HOTBAR_START; slot <= HOTBAR_END; slot++) {
            if (foodResolver.isEdible(inventory.getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private int moveInventoryFoodToHotbar(MinecraftClient client, ActionExecutor actions, PlayerInventory inventory) {
        if (client.interactionManager == null || client.player == null) {
            return -1;
        }

        int inventorySlot = findInventoryFoodSlot(inventory);

        if (inventorySlot < 0) {
            return -1;
        }

        if (!actions.swapInventorySlotWithHotbar(client, inventorySlot, preferredHotbarSlot)) {
            return -1;
        }

        return foodResolver.isEdible(inventory.getStack(preferredHotbarSlot)) ? preferredHotbarSlot : -1;
    }

    private int findInventoryFoodSlot(PlayerInventory inventory) {
        for (int slot = INVENTORY_START; slot <= INVENTORY_END; slot++) {
            if (foodResolver.isEdible(inventory.getStack(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isContainerOpen(MinecraftClient client) {
        return client.currentScreen instanceof HandledScreen<?> && !(client.currentScreen instanceof InventoryScreen);
    }
}
