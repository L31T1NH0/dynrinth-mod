package com.dynrinth.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class ChatUtil {

    private static Component prefix() {
        return Component.text("[Dynrinth] ")
            .color(NamedTextColor.DARK_AQUA)
            .decorate(TextDecoration.BOLD);
    }

    public static Component info(String message) {
        return prefix().append(Component.text(message).color(NamedTextColor.GRAY));
    }

    public static Component ok(String message) {
        return prefix().append(Component.text(message).color(NamedTextColor.GREEN));
    }

    public static Component warn(String message) {
        return prefix().append(Component.text(message).color(NamedTextColor.YELLOW));
    }

    public static Component error(String message) {
        return prefix().append(Component.text(message).color(NamedTextColor.RED));
    }
}
