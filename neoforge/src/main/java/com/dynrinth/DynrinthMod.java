package com.dynrinth;

import com.dynrinth.command.DynrinthCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("dynrinth")
public class DynrinthMod {

    public static final String MOD_ID          = "dynrinth";
    public static final Logger LOGGER          = LoggerFactory.getLogger(MOD_ID);
    public static final String DYNRINTH_BASE_URL = "https://dynrinth.vercel.app";

    public DynrinthMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
        LOGGER.info("Dynrinth mod loaded.");
    }

    private void registerClientCommands(RegisterClientCommandsEvent event) {
        DynrinthCommand.register(event.getDispatcher());
    }

    public static String userAgent() {
        return ModList.get().getModContainerById(MOD_ID)
            .map(c -> c.getModInfo().getVersion().toString())
            .map(v -> "dynrinth-mod/" + v + " (github.com/L31T1NH0/dynrinth-mod)")
            .orElse("dynrinth-mod/unknown (github.com/L31T1NH0/dynrinth-mod)");
    }
}
