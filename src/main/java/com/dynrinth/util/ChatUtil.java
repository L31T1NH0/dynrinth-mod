package com.dynrinth.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ChatUtil {

    private static MutableComponent prefix() {
        return Component.literal("[Dynrinth] ")
            .withStyle(s -> s.withColor(ChatFormatting.DARK_AQUA).withBold(true));
    }

    public static Component info(String message) {
        return Component.empty().append(prefix())
            .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    public static Component ok(String message) {
        return Component.empty().append(prefix())
            .append(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    public static Component warn(String message) {
        return Component.empty().append(prefix())
            .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    public static Component error(String message) {
        return Component.empty().append(prefix())
            .append(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
