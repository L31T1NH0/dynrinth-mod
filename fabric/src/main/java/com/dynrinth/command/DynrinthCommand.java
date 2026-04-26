package com.dynrinth.command;

import com.dynrinth.DynrinthMod;
import com.dynrinth.api.DynrinthWebApi;
import com.dynrinth.api.ModListState;
import com.dynrinth.api.ModrinthApi;
import com.dynrinth.code.CodeDecoder;
import com.dynrinth.installer.ModInstaller;
import com.dynrinth.tracker.PackTracker;
import com.dynrinth.util.ChatUtil;
import com.dynrinth.util.McCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DynrinthCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("dynrinth")
                .then(ClientCommandManager.argument("code", StringArgumentType.word())
                    .executes(ctx -> run(ctx.getSource(),
                        StringArgumentType.getString(ctx, "code"), Action.AUTO))
                    .then(ClientCommandManager.literal("force")
                        .executes(ctx -> run(ctx.getSource(),
                            StringArgumentType.getString(ctx, "code"), Action.FORCE)))
                )
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("code", StringArgumentType.word())
                        .executes(ctx -> remove(ctx.getSource(),
                            StringArgumentType.getString(ctx, "code"))))
                )
        );
    }

    private enum Action { AUTO, FORCE }

    private static int run(FabricClientCommandSource source, String rawCode, Action action) {
        String code = rawCode.toUpperCase();

        if (!CodeDecoder.isValidFormat(code)) {
            source.sendFeedback(ChatUtil.error("Invalid code. Expected 10 characters."));
            return 0;
        }

        if (action == Action.AUTO && code.length() == 10) {
            String codeVersion    = CodeDecoder.decodeMcVersion(code);
            String currentVersion = McCompat.mcVersionName();

            if (codeVersion != null && !codeVersion.equals(currentVersion)) {
                showMismatchPrompt(source, code, codeVersion, currentVersion);
                return 1;
            }
        }

        source.sendFeedback(ChatUtil.info("Fetching modpack..."));
        startDownloadThread(source, code);
        return 1;
    }

    private static int remove(FabricClientCommandSource source, String rawCode) {
        String code = rawCode.toUpperCase();
        source.sendFeedback(ChatUtil.info("Removing pack " + code + "..."));

        Path gameDir = FabricLoader.getInstance().getGameDir();
        PackTracker tracker = new PackTracker(gameDir);
        List<String> filenames = tracker.getFilenames(code);

        if (filenames.isEmpty()) {
            source.sendFeedback(ChatUtil.error("No installation record for code " + code + "."));
            return 0;
        }

        Path modsDir = gameDir.resolve("mods");
        int removed = 0;
        for (String filename : filenames) {
            try {
                if (Files.deleteIfExists(modsDir.resolve(filename))) removed++;
            } catch (Exception e) {
                source.sendFeedback(ChatUtil.warn("Could not delete " + filename + ": " + e.getMessage()));
            }
        }

        final int count = removed;
        source.sendFeedback(ChatUtil.ok("Removed " + count + " mod(s). Restart to apply changes."));
        return 1;
    }

    private static void showMismatchPrompt(FabricClientCommandSource source,
                                           String code,
                                           String codeVersion,
                                           String currentVersion) {
        source.sendFeedback(ChatUtil.warn("This modpack targets MC " + codeVersion));
        source.sendFeedback(ChatUtil.warn("You are running MC " + currentVersion));
        source.sendFeedback(
            Component.empty()
                .append(Component.literal("  "))
                .append(Component.literal("[▶ Download anyway]")
                    .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(McCompat.runCommandEvent("/dynrinth " + code + " force"))))
                .append(Component.literal("  "))
                .append(Component.literal("[✗ Cancel]")
                    .withStyle(s -> s.withColor(ChatFormatting.RED)))
        );
    }

    private static void startDownloadThread(FabricClientCommandSource source, String code) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path modsDir = gameDir.resolve("mods");
        String ua    = DynrinthMod.userAgent();

        Thread thread = new Thread(() -> {
            try {
                ModListState state = DynrinthWebApi.fetchState(DynrinthMod.DYNRINTH_BASE_URL, code);
                if (state == null) {
                    onMain(() -> source.sendFeedback(ChatUtil.error("Code not found or invalid.")));
                    return;
                }

                if (!"modrinth".equals(state.source)) {
                    onMain(() -> source.sendFeedback(
                        ChatUtil.error("Only Modrinth mod lists are supported.")));
                    return;
                }

                onMain(() -> source.sendFeedback(ChatUtil.info(
                    "Resolving " + state.mods.size() + " mod(s) for MC " + state.version + "...")));

                ModrinthApi.ResolveResult resolved =
                    ModrinthApi.resolveVersions(state.mods, state.version, state.loader, ua);

                if (!resolved.notFound().isEmpty()) {
                    String ids = String.join(", ", resolved.notFound());
                    onMain(() -> source.sendFeedback(
                        ChatUtil.warn("Not found for MC " + state.version + ": " + ids)));
                }

                if (resolved.versions().isEmpty()) {
                    onMain(() -> source.sendFeedback(
                        ChatUtil.error("No compatible mods found on Modrinth.")));
                    return;
                }

                ModInstaller.InstallResult result = ModInstaller.install(
                    resolved.versions(), modsDir, ua,
                    progress -> onMain(() -> source.sendFeedback(ChatUtil.info(
                        "Downloading (" + progress.current() + "/" + progress.total() + ") "
                        + progress.name()))));

                // Record installed files for later removal
                new PackTracker(gameDir).record(code, result.filenames());

                if (!result.hashFailed().isEmpty()) {
                    String failed = String.join(", ", result.hashFailed());
                    onMain(() -> source.sendFeedback(
                        ChatUtil.error("Hash mismatch (deleted): " + failed)));
                }

                final int downloaded = result.downloaded();
                final int skipped    = result.skipped();

                onMain(() -> {
                    if (downloaded == 0 && result.hashFailed().isEmpty()) {
                        source.sendFeedback(ChatUtil.ok("All mods already installed."));
                    } else if (skipped > 0) {
                        source.sendFeedback(ChatUtil.ok(
                            "Done! " + downloaded + " installed, " + skipped + " already present."));
                    } else {
                        source.sendFeedback(ChatUtil.ok("Done! " + downloaded + " mod(s) installed."));
                    }
                    if (downloaded > 0) {
                        source.sendFeedback(ChatUtil.warn("Restart the game to activate the mods."));
                    }
                });

            } catch (Exception e) {
                DynrinthMod.LOGGER.error("Download failed", e);
                onMain(() -> source.sendFeedback(ChatUtil.error("Error: " + e.getMessage())));
            }
        }, "dynrinth-download");

        thread.setDaemon(true);
        thread.start();
    }

    private static void onMain(Runnable action) {
        Minecraft.getInstance().execute(action);
    }
}
