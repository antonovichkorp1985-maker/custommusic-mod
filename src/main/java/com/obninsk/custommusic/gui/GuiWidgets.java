package com.obninsk.custommusic.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Small compatibility helper.
 *
 * <p>Since 1.19.4 the {@code new Button(x, y, w, h, message, onPress)} constructor is
 * gone - buttons are created through {@code Button.builder(message, onPress)}. This keeps
 * the screen code readable and makes the 1.19.2 -> 1.21.1 port a one-to-one change.
 */
public final class GuiWidgets {

    private GuiWidgets() {
    }

    public static Button button(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        return Button.builder(message, onPress)
                .bounds(x, y, width, height)
                .build();
    }
}
