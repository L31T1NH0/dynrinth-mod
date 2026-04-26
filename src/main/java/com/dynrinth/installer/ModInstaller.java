package com.dynrinth.installer;

import com.dynrinth.DynrinthMod;
import com.dynrinth.api.ModrinthVersion;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class ModInstaller {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    public record Progress(int current, int total, String name) {}
    public record InstallResult(int downloaded, int skipped) {}

    /**
     * Downloads each version's primary JAR to the mods folder.
     * Already-present files are counted as skipped. Progress fires only for actual downloads.
     */
    public static InstallResult install(List<ModrinthVersion> versions, Consumer<Progress> onProgress)
            throws Exception {

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");

        List<ModrinthVersion> toDownload = versions.stream()
            .filter(v -> {
                ModrinthVersion.ModrinthFile f = v.getPrimaryFile();
                return f != null && f.filename != null && !Files.exists(modsDir.resolve(f.filename));
            })
            .toList();

        int skipped = versions.size() - toDownload.size();

        for (int i = 0; i < toDownload.size(); i++) {
            ModrinthVersion v = toDownload.get(i);
            ModrinthVersion.ModrinthFile file = v.getPrimaryFile();

            String displayName = (v.name != null && !v.name.isBlank()) ? v.name : file.filename;
            onProgress.accept(new Progress(i + 1, toDownload.size(), displayName));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(file.url))
                .header("User-Agent", DynrinthMod.userAgent())
                .GET()
                .build();

            HTTP.send(req, HttpResponse.BodyHandlers.ofFile(modsDir.resolve(file.filename)));
        }

        return new InstallResult(toDownload.size(), skipped);
    }
}
