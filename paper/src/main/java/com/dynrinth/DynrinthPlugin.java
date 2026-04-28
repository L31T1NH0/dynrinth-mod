package com.dynrinth;

import com.dynrinth.command.DynrinthCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DynrinthPlugin extends JavaPlugin {

    public static final String DYNRINTH_BASE_URL = "https://dynrinth.vercel.app";

    private static DynrinthPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        PluginCommand command = getCommand("dynrinth");
        if (command == null) {
            getLogger().severe("Command 'dynrinth' is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DynrinthCommand handler = new DynrinthCommand();
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("Dynrinth plugin loaded.");
    }

    public static DynrinthPlugin getInstance() { return instance; }

    public static String userAgent() {
        return "dynrinth-plugin/" + instance.getDescription().getVersion()
            + " (github.com/L31T1NH0/dynrinth-mod)";
    }
}
