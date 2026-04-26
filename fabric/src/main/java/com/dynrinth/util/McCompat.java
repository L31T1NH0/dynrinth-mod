package com.dynrinth.util;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;

public class McCompat {

    public static String mcVersionName() {
        Object wv = SharedConstants.getCurrentVersion();
        // 1.21+: WorldVersion is a record — accessor is name()
        // pre-1.21: getName()
        for (String method : new String[]{"name", "getName"}) {
            try {
                return (String) wv.getClass().getMethod(method).invoke(wv);
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ClickEvent runCommandEvent(String command) {
        // 1.21+: ClickEvent is abstract, concrete type is ClickEvent$RunCommand(String)
        try {
            Class<?> cls = Class.forName("net.minecraft.network.chat.ClickEvent$RunCommand");
            return (ClickEvent) cls.getConstructor(String.class).newInstance(command);
        } catch (Exception ignored) {}

        // pre-1.21: new ClickEvent(ClickEvent.Action.RUN_COMMAND, String)
        try {
            Class<Enum> actionCls = (Class<Enum>) Class.forName("net.minecraft.network.chat.ClickEvent$Action");
            Object action = Enum.valueOf(actionCls, "RUN_COMMAND");
            return (ClickEvent) ClickEvent.class
                .getConstructor(actionCls, String.class)
                .newInstance(action, command);
        } catch (Exception ignored) {}

        return null;
    }
}
