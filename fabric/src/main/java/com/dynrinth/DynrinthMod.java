package com.dynrinth;

import com.dynrinth.command.DynrinthCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynrinthMod implements ClientModInitializer {

    public static final String MOD_ID          = "dynrinth";
    public static final Logger LOGGER          = LoggerFactory.getLogger(MOD_ID);
    public static final String DYNRINTH_BASE_URL = "https://dynrinth.vercel.app";

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            DynrinthCommand.register(dispatcher)
        );
        LOGGER.info("Dynrinth mod loaded.");
    }

    public static String userAgent() {
        String version = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        return "dynrinth-mod/" + version + " (github.com/L31T1NH0/dynrinth-mod)";
    }
}
