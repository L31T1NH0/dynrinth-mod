package com.dynrinth;

import com.dynrinth.command.DynrinthCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class DynrinthPlugin extends JavaPlugin {

    public static final String DYNRINTH_BASE_URL = "https://dynrinth.vercel.app";

    private static DynrinthPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
            DynrinthCommand.register(event.registrar())
        );
        getLogger().info("Dynrinth plugin loaded.");
    }

    public static DynrinthPlugin getInstance() { return instance; }

    public static String userAgent() {
        return "dynrinth-plugin/" + instance.getPluginMeta().getVersion()
            + " (github.com/L31T1NH0/dynrinth-mod)";
    }
}
