package com.dynrinth.command;

import com.dynrinth.DynrinthPlugin;
import com.dynrinth.api.DynrinthWebApi;
import com.dynrinth.api.ModListState;
import com.dynrinth.api.ModrinthApi;
import com.dynrinth.code.CodeDecoder;
import com.dynrinth.installer.ModInstaller;
import com.dynrinth.tracker.PackTracker;
import com.dynrinth.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class DynrinthCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatUtil.error("Usage: /" + label + " <code> [force] | /" + label + " remove <code>"));
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                sender.sendMessage(ChatUtil.error("Usage: /" + label + " remove <code>"));
                return true;
            }
            return remove(sender, args[1]);
        }

        if (args.length > 2 || (args.length == 2 && !"force".equalsIgnoreCase(args[1]))) {
            sender.sendMessage(ChatUtil.error("Usage: /" + label + " <code> [force]"));
            return true;
        }

        return run(sender, args[0], args.length == 2);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("remove".startsWith(input)) return Collections.singletonList("remove");
        }

        if (args.length == 2 && !"remove".equalsIgnoreCase(args[0])) {
            String input = args[1].toLowerCase(Locale.ROOT);
            if ("force".startsWith(input)) return Collections.singletonList("force");
        }

        return Collections.emptyList();
    }

    private static boolean run(CommandSender sender, String rawCode, boolean force) {
        String code = rawCode.toUpperCase();

        if (!CodeDecoder.isValidFormat(code)) {
            sender.sendMessage(ChatUtil.error("Invalid code. Expected 8 or 10 characters."));
            return true;
        }

        if (!force && code.length() == 10) {
            String codeVersion    = CodeDecoder.decodeMcVersion(code);
            String currentVersion = currentMinecraftVersion();

            if (codeVersion != null && !codeVersion.equals(currentVersion)) {
                sender.sendMessage(ChatUtil.warn("This pack targets MC " + codeVersion));
                sender.sendMessage(ChatUtil.warn("You are running MC " + currentVersion));
                sender.sendMessage(ChatUtil.info("Run /dynrinth " + code + " force to install anyway."));
                return true;
            }
        }

        sender.sendMessage(ChatUtil.info("Fetching pack..."));
        startDownloadThread(sender, code);
        return true;
    }

    private static boolean remove(CommandSender sender, String rawCode) {
        String code    = rawCode.toUpperCase();
        Path   plugins = DynrinthPlugin.getInstance().getDataFolder().getParentFile().toPath();
        Path   root    = plugins.getParent();

        sender.sendMessage(ChatUtil.info("Removing pack " + code + "..."));

        PackTracker  tracker  = new PackTracker(root);
        List<String> entries  = tracker.getFilenames(code);

        if (entries.isEmpty()) {
            sender.sendMessage(ChatUtil.error("No installation record for code " + code + "."));
            return true;
        }

        int removed = 0;
        for (String entry : entries) {
            try {
                Path target;
                if (entry.contains("/")) {
                    // "subdir/filename" format — resolve relative to server root
                    target = ModInstaller.resolveGamePath(root, entry);
                } else {
                    // Legacy bare filename — assume plugins/
                    target = ModInstaller.resolveInstallPath(plugins, entry);
                }

                if (target == null) {
                    sender.sendMessage(ChatUtil.warn("Skipping unsafe tracked path: " + entry));
                    continue;
                }

                if (Files.deleteIfExists(target)) removed++;
            } catch (Exception e) {
                sender.sendMessage(ChatUtil.warn("Could not delete " + entry));
            }
        }

        sender.sendMessage(ChatUtil.ok("Removed " + removed + " plugin(s). Restart server to apply."));
        return true;
    }

    private static void startDownloadThread(final CommandSender sender, final String code) {
        final Path   plugins = DynrinthPlugin.getInstance().getDataFolder().getParentFile().toPath();
        final Path   root    = plugins.getParent();
        final String ua      = DynrinthPlugin.userAgent();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ModListState state = DynrinthWebApi.fetchState(DynrinthPlugin.DYNRINTH_BASE_URL, code);
                    if (state == null) {
                        sendSync(sender, ChatUtil.error("Code not found or invalid."));
                        return;
                    }

                    if (!"modrinth".equals(state.source)) {
                        sendSync(sender, ChatUtil.error("Only Modrinth packs are supported."));
                        return;
                    }

                    List<ModListState.ContentGroup> groups = extractGroups(state);
                    if (groups.isEmpty()) {
                        sendSync(sender, ChatUtil.error("Pack has no content."));
                        return;
                    }

                    boolean anyPluginGroup = false;
                    boolean anyResolved    = false;
                    boolean restartHint    = false;
                    List<String> tracked   = new ArrayList<String>();

                    for (ModListState.ContentGroup group : groups) {
                        String ct = group.contentType != null ? group.contentType : "plugin";
                        if (!"plugin".equals(ct)) {
                            sendSync(sender, ChatUtil.warn(
                                "Skipping non-plugin entries on Paper (" + ct + ")."));
                            continue;
                        }

                        anyPluginGroup = true;

                        String loader = group.pluginLoader != null ? group.pluginLoader
                            : (group.loader != null ? group.loader
                            : (state.pluginLoader != null ? state.pluginLoader
                            : (state.loader != null ? state.loader : "paper")));

                        sendSync(sender, ChatUtil.info(
                            "Resolving " + group.mods.size() + " plugin(s) for MC " + state.version + "..."));

                        ModrinthApi.ResolveResult resolved =
                            ModrinthApi.resolveVersions(group.mods, state.version, loader, ua);

                        if (!resolved.notFound().isEmpty()) {
                            sendSync(sender, ChatUtil.warn("Not found: " + join(resolved.notFound())));
                        }

                        if (resolved.versions().isEmpty()) {
                            sendSync(sender, ChatUtil.warn("No compatible plugins found on Modrinth for this group."));
                            continue;
                        }
                        anyResolved = true;

                        if (resolved.depsAdded() > 0) {
                            int deps = resolved.depsAdded();
                            sendSync(sender, ChatUtil.info(
                                "Auto-resolved " + deps + " required dependenc" + (deps == 1 ? "y" : "ies") + "."));
                        }

                        ModInstaller.InstallResult result = ModInstaller.install(
                            resolved.versions(), plugins, ua,
                            new java.util.function.Consumer<ModInstaller.Progress>() {
                                @Override
                                public void accept(ModInstaller.Progress progress) {
                                    sendSync(sender, ChatUtil.info(
                                        "Downloading (" + progress.current() + "/" + progress.total() + ") "
                                            + progress.name()));
                                }
                            });

                        for (String fn : result.downloadedFilenames()) {
                            tracked.add("plugins/" + fn);
                        }

                        if (!result.hashFailed().isEmpty()) {
                            sendSync(sender, ChatUtil.error("Hash mismatch (deleted): " + join(result.hashFailed())));
                        }

                        int downloaded = result.downloaded();
                        int skipped    = result.skipped();

                        if (downloaded == 0 && result.hashFailed().isEmpty()) {
                            sendSync(sender, ChatUtil.ok("All plugins already installed."));
                        } else if (skipped > 0) {
                            sendSync(sender, ChatUtil.ok(
                                "Done! " + downloaded + " installed, " + skipped + " already present."));
                        } else {
                            sendSync(sender, ChatUtil.ok("Done! " + downloaded + " plugin(s) installed."));
                        }

                        if (downloaded > 0) {
                            restartHint = true;
                        }
                    }

                    if (!anyPluginGroup) {
                        sendSync(sender, ChatUtil.error(
                            "This pack has no plugin entries. Only plugin packs can be installed on Paper."));
                        return;
                    }

                    if (!anyResolved) {
                        sendSync(sender, ChatUtil.error("No compatible plugins found on Modrinth."));
                        return;
                    }

                    if (!tracked.isEmpty()) {
                        PackTracker tracker = new PackTracker(root);
                        tracker.record(code, mergeTrackedFilenames(tracker.getFilenames(code), tracked));
                    }

                    if (restartHint) {
                        sendSync(sender, ChatUtil.warn("Restart the server to activate the plugins."));
                    }
                } catch (Exception e) {
                    DynrinthPlugin.getInstance().getLogger().severe("Download failed: " + e.getMessage());
                    sendSync(sender, ChatUtil.error("Error: " + e.getMessage()));
                }
            }
        }, "dynrinth-download");

        thread.setDaemon(true);
        thread.start();
    }

    private static void sendSync(final CommandSender sender, final String message) {
        Bukkit.getScheduler().runTask(DynrinthPlugin.getInstance(), new Runnable() {
            @Override
            public void run() {
                sender.sendMessage(message);
            }
        });
    }

    private static String currentMinecraftVersion() {
        String version   = Bukkit.getBukkitVersion();
        int    separator = version.indexOf('-');
        return separator >= 0 ? version.substring(0, separator) : version;
    }

    private static List<ModListState.ContentGroup> extractGroups(ModListState state) {
        List<ModListState.ContentGroup> groups = new ArrayList<ModListState.ContentGroup>();

        if (state.groups != null) {
            for (ModListState.ContentGroup group : state.groups) {
                if (group == null || group.mods == null || group.mods.isEmpty()) continue;
                groups.add(group);
            }
        }

        if (!groups.isEmpty()) return groups;

        if (state.mods != null && !state.mods.isEmpty()) {
            ModListState.ContentGroup fallback = new ModListState.ContentGroup();
            fallback.contentType  = state.contentType != null ? state.contentType : "plugin";
            fallback.loader       = state.loader;
            fallback.shaderLoader = state.shaderLoader;
            fallback.pluginLoader = state.pluginLoader;
            fallback.mods         = state.mods;
            groups.add(fallback);
        }

        return groups;
    }

    private static List<String> mergeTrackedFilenames(List<String> existing, List<String> downloaded) {
        LinkedHashSet<String> merged = new LinkedHashSet<String>(existing);
        merged.addAll(downloaded);
        return new ArrayList<String>(merged);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }
}
