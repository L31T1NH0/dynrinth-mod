package com.dynrinth.command;

import com.dynrinth.DynrinthPlugin;
import com.dynrinth.api.DynrinthWebApi;
import com.dynrinth.api.ModListState;
import com.dynrinth.api.ModrinthApi;
import com.dynrinth.code.CodeDecoder;
import com.dynrinth.installer.ModInstaller;
import com.dynrinth.tracker.PackTracker;
import com.dynrinth.util.ChatUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class DynrinthCommand {

    public static void register(Commands registrar) {
        registrar.register(
            Commands.literal("dynrinth")
                .then(Commands.argument("code", StringArgumentType.word())
                    .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "code"), false))
                    .then(Commands.literal("force")
                        .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "code"), true)))
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "code"))))
                )
                .build(),
            "Install or remove a Dynrinth modpack"
        );
    }

    private static int run(CommandSourceStack ctx, String rawCode, boolean force) {
        String        code   = rawCode.toUpperCase();
        CommandSender sender = ctx.getSender();

        if (!CodeDecoder.isValidFormat(code)) {
            sender.sendMessage(ChatUtil.error("Invalid code. Expected 10 characters."));
            return 0;
        }

        sender.sendMessage(ChatUtil.info("Fetching modpack..."));
        startDownloadThread(sender, code, force);
        return 1;
    }

    private static int remove(CommandSourceStack ctx, String rawCode) {
        String        code    = rawCode.toUpperCase();
        CommandSender sender  = ctx.getSender();
        Path          plugins = DynrinthPlugin.getInstance().getDataFolder().getParentFile().toPath();

        sender.sendMessage(ChatUtil.info("Removing pack " + code + "..."));

        PackTracker  tracker   = new PackTracker(plugins.getParent()); // server root
        List<String> filenames = tracker.getFilenames(code);

        if (filenames.isEmpty()) {
            sender.sendMessage(ChatUtil.error("No installation record for code " + code + "."));
            return 0;
        }

        int removed = 0;
        for (String filename : filenames) {
            try { if (Files.deleteIfExists(plugins.resolve(filename))) removed++; }
            catch (Exception e) { sender.sendMessage(ChatUtil.warn("Could not delete " + filename)); }
        }

        sender.sendMessage(ChatUtil.ok("Removed " + removed + " plugin(s). Restart server to apply."));
        return 1;
    }

    private static void startDownloadThread(CommandSender sender, String code, boolean force) {
        Path   plugins = DynrinthPlugin.getInstance().getDataFolder().getParentFile().toPath();
        Path   root    = plugins.getParent();
        String ua      = DynrinthPlugin.userAgent();

        Thread thread = new Thread(() -> {
            try {
                ModListState state = DynrinthWebApi.fetchState(DynrinthPlugin.DYNRINTH_BASE_URL, code);
                if (state == null) { sender.sendMessage(ChatUtil.error("Code not found or invalid.")); return; }

                if (!"modrinth".equals(state.source)) {
                    sender.sendMessage(ChatUtil.error("Only Modrinth mod lists are supported."));
                    return;
                }

                // Paper uses plugin loader, treat "paper"/"bukkit"/"spigot" as valid
                String loader = state.pluginLoader != null ? state.pluginLoader
                    : (state.loader != null ? state.loader : "paper");

                sender.sendMessage(ChatUtil.info(
                    "Resolving " + state.mods.size() + " plugin(s) for MC " + state.version + "..."));

                ModrinthApi.ResolveResult resolved =
                    ModrinthApi.resolveVersions(state.mods, state.version, loader, ua);

                if (!resolved.notFound().isEmpty())
                    sender.sendMessage(ChatUtil.warn("Not found: " + String.join(", ", resolved.notFound())));

                if (resolved.versions().isEmpty()) {
                    sender.sendMessage(ChatUtil.error("No compatible plugins found on Modrinth."));
                    return;
                }

                ModInstaller.InstallResult result = ModInstaller.install(
                    resolved.versions(), plugins, ua,
                    progress -> sender.sendMessage(ChatUtil.info(
                        "Downloading (" + progress.current() + "/" + progress.total() + ") "
                        + progress.name())));

                new PackTracker(root).record(code, result.filenames());

                if (!result.hashFailed().isEmpty())
                    sender.sendMessage(ChatUtil.error("Hash mismatch (deleted): " + String.join(", ", result.hashFailed())));

                final int downloaded = result.downloaded();
                final int skipped    = result.skipped();

                if (downloaded == 0 && result.hashFailed().isEmpty()) {
                    sender.sendMessage(ChatUtil.ok("All plugins already installed."));
                } else if (skipped > 0) {
                    sender.sendMessage(ChatUtil.ok("Done! " + downloaded + " installed, " + skipped + " already present."));
                } else {
                    sender.sendMessage(ChatUtil.ok("Done! " + downloaded + " plugin(s) installed."));
                }
                if (downloaded > 0)
                    sender.sendMessage(ChatUtil.warn("Restart the server to activate the plugins."));

            } catch (Exception e) {
                DynrinthPlugin.getInstance().getLogger().severe("Download failed: " + e.getMessage());
                sender.sendMessage(ChatUtil.error("Error: " + e.getMessage()));
            }
        }, "dynrinth-download");

        thread.setDaemon(true);
        thread.start();
    }
}
