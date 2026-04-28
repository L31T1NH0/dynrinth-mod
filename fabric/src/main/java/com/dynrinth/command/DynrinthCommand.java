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
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
            source.sendFeedback(ChatUtil.error("Invalid code. Expected 8 or 10 characters."));
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

        source.sendFeedback(ChatUtil.info("Fetching pack..."));
        startDownloadThread(source, code);
        return 1;
    }

    private static int remove(FabricClientCommandSource source, String rawCode) {
        String code = rawCode.toUpperCase();
        source.sendFeedback(ChatUtil.info("Removing pack " + code + "..."));

        Path gameDir = FabricLoader.getInstance().getGameDir();
        PackTracker tracker = new PackTracker(gameDir);
        List<String> entries = tracker.getFilenames(code);

        if (entries.isEmpty()) {
            source.sendFeedback(ChatUtil.error("No installation record for code " + code + "."));
            return 0;
        }

        Path modsDir = gameDir.resolve("mods");
        int removed = 0;
        for (String entry : entries) {
            try {
                Path target;
                if (entry.contains("/")) {
                    // "subdir/filename" or "saves/World/datapacks/filename" — resolve from gameDir
                    target = ModInstaller.resolveGamePath(gameDir, entry);
                } else {
                    // Legacy bare filename — assume mods/
                    target = ModInstaller.resolveInstallPath(modsDir, entry);
                }

                if (target == null) {
                    source.sendFeedback(ChatUtil.warn("Skipping unsafe tracked path: " + entry));
                    continue;
                }

                if (Files.deleteIfExists(target)) removed++;
            } catch (Exception e) {
                source.sendFeedback(ChatUtil.warn("Could not delete " + entry + ": " + e.getMessage()));
            }
        }

        final int count = removed;
        source.sendFeedback(ChatUtil.ok("Removed " + count + " file(s). Restart to apply changes."));
        return 1;
    }

    private static void showMismatchPrompt(FabricClientCommandSource source,
                                           String code,
                                           String codeVersion,
                                           String currentVersion) {
        source.sendFeedback(ChatUtil.warn("This pack targets MC " + codeVersion));
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
                        ChatUtil.error("Only Modrinth packs are supported.")));
                    return;
                }

                List<ModListState.ContentGroup> groups = extractGroups(state);
                if (groups.isEmpty()) {
                    onMain(() -> source.sendFeedback(ChatUtil.error("Pack has no content.")));
                    return;
                }

                boolean anySupported = false;
                boolean anyResolved  = false;
                boolean restartHint  = false;
                boolean reloadHint   = false;

                List<String> tracked = new ArrayList<>();

                for (ModListState.ContentGroup group : groups) {
                    String ct = group.contentType != null ? group.contentType : "mod";

                    final Path installDir;
                    final String resolvedLoader;
                    final String contentLabel;

                    switch (ct) {
                        case "mod":
                            installDir     = gameDir.resolve("mods");
                            resolvedLoader = group.loader != null ? group.loader : (state.loader != null ? state.loader : "fabric");
                            contentLabel   = "mod(s)";
                            break;
                        case "resourcepack":
                            installDir     = gameDir.resolve("resourcepacks");
                            resolvedLoader = null;
                            contentLabel   = "resource pack(s)";
                            break;
                        case "shader":
                            installDir     = gameDir.resolve("shaderpacks");
                            resolvedLoader = group.shaderLoader != null ? group.shaderLoader : (state.shaderLoader != null ? state.shaderLoader : "iris");
                            contentLabel   = "shader(s)";
                            break;
                        case "datapack": {
                            var server = Minecraft.getInstance().getSingleplayerServer();
                            if (server == null) {
                                onMain(() -> source.sendFeedback(ChatUtil.warn(
                                    "Skipping datapacks: you must be in a singleplayer world to install them.")));
                                continue;
                            }
                            installDir     = server.getWorldPath(LevelResource.ROOT).resolve("datapacks");
                            resolvedLoader = "datapack";
                            contentLabel   = "datapack(s)";
                            break;
                        }
                        case "plugin":
                            onMain(() -> source.sendFeedback(ChatUtil.warn(
                                "Skipping plugin entries: plugins are server-side. Use the Dynrinth Paper plugin.")));
                            continue;
                        default:
                            onMain(() -> source.sendFeedback(ChatUtil.warn(
                                "Skipping unsupported content type on Fabric: '" + ct + "'.")));
                            continue;
                    }

                    anySupported = true;

                    // Tracker prefix: path relative to gameDir (e.g. "mods", "saves/World/datapacks")
                    final String prefix;
                    try {
                        prefix = gameDir.toAbsolutePath().normalize()
                            .relativize(installDir.toAbsolutePath().normalize())
                            .toString()
                            .replace(java.io.File.separatorChar, '/');
                    } catch (IllegalArgumentException e) {
                        onMain(() -> source.sendFeedback(ChatUtil.warn(
                            "Install directory is outside the game folder. Skipping this group.")));
                        continue;
                    }

                    final int requested = group.mods.size();
                    onMain(() -> source.sendFeedback(ChatUtil.info(
                        "Resolving " + requested + " " + contentLabel
                            + " for MC " + state.version + "...")));

                    ModrinthApi.ResolveResult resolved =
                        ModrinthApi.resolveVersions(group.mods, state.version, resolvedLoader, ua);

                    if (!resolved.notFound().isEmpty()) {
                        String ids = String.join(", ", resolved.notFound());
                        onMain(() -> source.sendFeedback(
                            ChatUtil.warn("Not found for MC " + state.version + ": " + ids)));
                    }

                    if (resolved.versions().isEmpty()) {
                        onMain(() -> source.sendFeedback(
                            ChatUtil.warn("No compatible " + contentLabel + " found on Modrinth.")));
                        continue;
                    }
                    anyResolved = true;

                    if (resolved.depsAdded() > 0) {
                        final int deps = resolved.depsAdded();
                        onMain(() -> source.sendFeedback(ChatUtil.info(
                            "Auto-resolved " + deps + " required dependenc" + (deps == 1 ? "y" : "ies") + ".")));
                    }

                    ModInstaller.InstallResult result = ModInstaller.install(
                        resolved.versions(), installDir, ua,
                        progress -> onMain(() -> source.sendFeedback(ChatUtil.info(
                            "Downloading (" + progress.current() + "/" + progress.total() + ") "
                                + progress.name()))));

                    for (String fn : result.downloadedFilenames()) {
                        tracked.add(prefix + "/" + fn);
                    }

                    if (!result.hashFailed().isEmpty()) {
                        String failed = String.join(", ", result.hashFailed());
                        onMain(() -> source.sendFeedback(
                            ChatUtil.error("Hash mismatch (deleted): " + failed)));
                    }

                    final int downloaded = result.downloaded();
                    final int skipped    = result.skipped();

                    onMain(() -> {
                        if (downloaded == 0 && result.hashFailed().isEmpty()) {
                            source.sendFeedback(ChatUtil.ok("All " + contentLabel + " already installed."));
                        } else if (skipped > 0) {
                            source.sendFeedback(ChatUtil.ok(
                                "Done! " + downloaded + " installed, " + skipped + " already present."));
                        } else {
                            source.sendFeedback(ChatUtil.ok(
                                "Done! " + downloaded + " " + contentLabel + " installed."));
                        }
                    });

                    if (downloaded > 0) {
                        if ("datapack".equals(ct)) reloadHint = true;
                        else restartHint = true;
                    }
                }

                if (!anySupported) {
                    onMain(() -> source.sendFeedback(ChatUtil.error(
                        "Pack has no content type supported on Fabric.")));
                    return;
                }

                if (!anyResolved) {
                    onMain(() -> source.sendFeedback(ChatUtil.error(
                        "No compatible content found on Modrinth for this pack.")));
                    return;
                }

                if (!tracked.isEmpty()) {
                    PackTracker tracker = new PackTracker(gameDir);
                    tracker.record(code, mergeTrackedFilenames(tracker.getFilenames(code), tracked));
                }

                if (restartHint) {
                    onMain(() -> source.sendFeedback(ChatUtil.warn("Restart the game to activate.")));
                }
                if (reloadHint) {
                    onMain(() -> source.sendFeedback(ChatUtil.warn("Run /reload to activate datapacks in-game.")));
                }

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

    private static List<ModListState.ContentGroup> extractGroups(ModListState state) {
        List<ModListState.ContentGroup> groups = new ArrayList<>();

        if (state.groups != null) {
            for (ModListState.ContentGroup group : state.groups) {
                if (group == null || group.mods == null || group.mods.isEmpty()) continue;
                groups.add(group);
            }
        }

        if (!groups.isEmpty()) return groups;

        if (state.mods != null && !state.mods.isEmpty()) {
            ModListState.ContentGroup fallback = new ModListState.ContentGroup();
            fallback.contentType  = state.contentType != null ? state.contentType : "mod";
            fallback.loader       = state.loader;
            fallback.shaderLoader = state.shaderLoader;
            fallback.pluginLoader = state.pluginLoader;
            fallback.mods         = state.mods;
            groups.add(fallback);
        }

        return groups;
    }

    private static List<String> mergeTrackedFilenames(List<String> existing, List<String> downloaded) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
        merged.addAll(downloaded);
        return new ArrayList<>(merged);
    }
}
