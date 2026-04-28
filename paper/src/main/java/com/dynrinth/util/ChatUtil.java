package com.dynrinth.util;

import org.bukkit.ChatColor;

public class ChatUtil {

    private static final String PREFIX = ChatColor.DARK_AQUA.toString()
        + ChatColor.BOLD + "[Dynrinth] " + ChatColor.RESET;

    public static String info(String message) {
        return PREFIX + ChatColor.GRAY + message;
    }

    public static String ok(String message) {
        return PREFIX + ChatColor.GREEN + message;
    }

    public static String warn(String message) {
        return PREFIX + ChatColor.YELLOW + message;
    }

    public static String error(String message) {
        return PREFIX + ChatColor.RED + message;
    }
}
