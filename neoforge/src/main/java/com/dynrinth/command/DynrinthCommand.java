package com.dynrinth.command;

import com.dynrinth.DynrinthMod;
import com.dynrinth.api.DynrinthWebApi;
import com.dynrinth.api.ModListState;
import com.dynrinth.api.ModrinthApi;
import com.dynrinth.code.CodeDecoder;
import com.dynrinth.installer.ModInstaller;
import com.dynrinth.tracker.PackTracker;
import com.dynrinth.util.ChatUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DynrinthCommand {

    public static void register(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        dispatcher.register(
            Commands.literal("dynrinth")
                .then(Commands.argument("code", StringArgumentType.word())
                    .executes(ctx -> run(StringArgumentType.getString(ctx, "code"), Action.AUTO))
                    .then(Commands.literal("force")
                        .executes(ctx -> run(StringArgumentType.getString(ctx, "code"), Action.FORCE)))
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> remove(StringArgumentType.getString(ctx, "code"))))
                )
        );
    }

    private enum Action { AUTO, FORCE }

    private static int run(String rawCode, Action action) {
        String code = rawCode.toUpperCase();

        if (!CodeDecoder.isValidFormat(code)) {
            send(ChatUtil.error("Invalid code. Expected 10 characters."));
            return 0;
        }

        if (action == Action.AUTO && code.length() == 10) {
            String codeVersion    = CodeDecoder.decodeMcVersion(code);
            String currentVersion = SharedConstants.getCurrentVersion().name();

            if (codeVersion != null && !codeVersion.equals(currentVersion)) {
                showMismatchPrompt(code, codeVersion, currentVersion);
                return 1;
            }
        }

        send(ChatUtil.info("Fetching modpack..."));
        startDownloadThread(code);
        return 1;
    }

    private static int remove(String rawCode) {
        String code    = rawCode.toUpperCase();
        Path   gameDir = FMLPaths.GAMEDIR.get();
        send(ChatUtil.info("Removing pack " + code + "..."));

        PackTracker  tracker   = new PackTracker(gameDir);
        List<String> filenames = tracker.getFilenames(code);

        if (filenames.isEmpty()) {
            send(ChatUtil.error("No installation record for code " + code + "."));
            return 0;
        }

        Path modsDir = gameDir.resolve("mods");
        int  removed = 0;
        for (String filename : filenames) {
            try { if (Files.deleteIfExists(modsDir.resolve(filename))) removed++; }
            catch (Exception e) { send(ChatUtil.warn("Could not delete " + filename)); }
        }

        final int count = removed;
        onMain(() -> send(ChatUtil.ok("Removed " + count + " mod(s). Restart to apply changes.")));
        return 1;
    }

    private static void showMismatchPrompt(String code, String codeVersion, String currentVersion) {
        send(ChatUtil.warn("This modpack targets MC " + codeVersion));
        send(ChatUtil.warn("You are running MC " + currentVersion));
        send(
            Component.empty()
                .append(Component.literal("  "))
                .append(Component.literal("[▶ Download anyway]")
                    .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/dynrinth " + code + " force"))))
                .append(Component.literal("  "))
                .append(Component.literal("[✗ Cancel]")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)))
        );
    }

    private static void startDownloadThread(String code) {
        Path   gameDir = FMLPaths.GAMEDIR.get();
        Path   modsDir = gameDir.resolve("mods");
        String ua      = DynrinthMod.userAgent();

        Thread thread = new Thread(() -> {
            try {
                ModListState state = DynrinthWebApi.fetchState(DynrinthMod.DYNRINTH_BASE_URL, code);
                if (state == null) { onMain(() -> send(ChatUtil.error("Code not found or invalid."))); return; }

                if (!"modrinth".equals(state.source)) {
                    onMain(() -> send(ChatUtil.error("Only Modrinth mod lists are supported.")));
                    return;
                }

                onMain(() -> send(ChatUtil.info(
                    "Resolving " + state.mods.size() + " mod(s) for MC " + state.version + "...")));

                ModrinthApi.ResolveResult resolved =
                    ModrinthApi.resolveVersions(state.mods, state.version, state.loader, ua);

                if (!resolved.notFound().isEmpty()) {
                    String ids = String.join(", ", resolved.notFound());
                    onMain(() -> send(ChatUtil.warn("Not found for MC " + state.version + ": " + ids)));
                }

                if (resolved.versions().isEmpty()) {
                    onMain(() -> send(ChatUtil.error("No compatible mods found on Modrinth.")));
                    return;
                }

                ModInstaller.InstallResult result = ModInstaller.install(
                    resolved.versions(), modsDir, ua,
                    progress -> onMain(() -> send(ChatUtil.info(
                        "Downloading (" + progress.current() + "/" + progress.total() + ") "
                        + progress.name()))));

                new PackTracker(gameDir).record(code, result.filenames());

                if (!result.hashFailed().isEmpty())
                    onMain(() -> send(ChatUtil.error("Hash mismatch (deleted): " + String.join(", ", result.hashFailed()))));

                final int downloaded = result.downloaded();
                final int skipped    = result.skipped();

                onMain(() -> {
                    if (downloaded == 0 && result.hashFailed().isEmpty()) {
                        send(ChatUtil.ok("All mods already installed."));
                    } else if (skipped > 0) {
                        send(ChatUtil.ok("Done! " + downloaded + " installed, " + skipped + " already present."));
                    } else {
                        send(ChatUtil.ok("Done! " + downloaded + " mod(s) installed."));
                    }
                    if (downloaded > 0)
                        send(ChatUtil.warn("Restart the game to activate the mods."));
                });

            } catch (Exception e) {
                DynrinthMod.LOGGER.error("Download failed", e);
                onMain(() -> send(ChatUtil.error("Error: " + e.getMessage())));
            }
        }, "dynrinth-download");

        thread.setDaemon(true);
        thread.start();
    }

    private static void send(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(component);
    }

    private static void onMain(Runnable action) {
        Minecraft.getInstance().execute(action);
    }
}
