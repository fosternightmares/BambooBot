package com.foster.bambooclientbot.sensors;

import com.foster.bambooclientbot.state.BotState;
import com.foster.bambooclientbot.state.ContainerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ContainerSensor {
    public void update(MinecraftClient client, BotState state) {
        if (client == null || client.currentScreen == null) {
            state.setContainerState(ContainerState.closed());
            return;
        }

        Screen screen = client.currentScreen;

        if (!(screen instanceof HandledScreen<?>) || screen instanceof InventoryScreen) {
            state.setContainerState(ContainerState.closed());
            return;
        }

        String title = screen.getTitle().getString();
        String type = screenType(screen);
        state.setContainerState(new ContainerState(true, type, title));
    }

    private String screenType(Screen screen) {
        if (screen instanceof HandledScreen<?> handledScreen) {
            ScreenHandlerType<?> handlerType = handledScreen.getScreenHandler().getType();
            Identifier id = Registries.SCREEN_HANDLER.getId(handlerType);

            if (id != null) {
                return id.toString();
            }
        }

        return screen.getClass().getSimpleName();
    }
}
