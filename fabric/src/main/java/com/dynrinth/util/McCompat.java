package com.dynrinth.util;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;

public class McCompat {

    public static String mcVersionName() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static ClickEvent runCommandEvent(String command) {
        return new ClickEvent.RunCommand(command);
    }
}
