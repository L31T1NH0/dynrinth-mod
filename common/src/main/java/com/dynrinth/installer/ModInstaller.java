package com.dynrinth.installer;

import com.dynrinth.api.ModrinthVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ModInstaller {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public record Progress(int current, int total, String name) {}
    public record InstallResult(int downloaded, int skipped, List<String> hashFailed, List<String> filenames) {}

    public static InstallResult install(List<ModrinthVersion> versions, Path installDir,
                                        String userAgent, Consumer<Progress> onProgress)
            throws Exception {

        List<ModrinthVersion> toDownload = versions.stream()
            .filter(v -> {
                ModrinthVersion.ModrinthFile f = v.getPrimaryFile();
                return f != null && f.filename != null && !Files.exists(installDir.resolve(f.filename));
            })
            .toList();

        int skipped = versions.size() - toDownload.size();
        int total   = toDownload.size();

        AtomicInteger counter        = new AtomicInteger(0);
        List<String>  hashFailed     = new CopyOnWriteArrayList<>();
        List<String>  installedFiles = new CopyOnWriteArrayList<>();

        // Collect already-present filenames too
        versions.stream()
            .filter(v -> {
                ModrinthVersion.ModrinthFile f = v.getPrimaryFile();
                return f != null && f.filename != null && Files.exists(installDir.resolve(f.filename));
            })
            .map(v -> v.getPrimaryFile().filename)
            .forEach(installedFiles::add);

        int threads = Math.max(1, Math.min(total, 4));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<Void>> futures = toDownload.stream().map(v ->
            CompletableFuture.runAsync(() -> {
                ModrinthVersion.ModrinthFile file = v.getPrimaryFile();
                int   idx         = counter.incrementAndGet();
                String displayName = (v.name != null && !v.name.isBlank()) ? v.name : file.filename;
                onProgress.accept(new Progress(idx, total, displayName));

                Path dest = installDir.resolve(file.filename);
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(file.url))
                        .header("User-Agent", userAgent)
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();
                    HTTP.send(req, HttpResponse.BodyHandlers.ofFile(dest));

                    // Verify SHA-1 if available
                    String expectedSha1 = file.hashes != null ? file.hashes.get("sha1") : null;
                    if (expectedSha1 != null) {
                        byte[]        bytes  = Files.readAllBytes(dest);
                        MessageDigest md     = MessageDigest.getInstance("SHA-1");
                        String        actual = HexFormat.of().formatHex(md.digest(bytes));
                        if (!actual.equalsIgnoreCase(expectedSha1)) {
                            Files.deleteIfExists(dest);
                            hashFailed.add(displayName);
                            return;
                        }
                    }

                    installedFiles.add(file.filename);
                } catch (Exception e) {
                    try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
                    hashFailed.add(displayName);
                }
            }, pool)
        ).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        pool.shutdown();

        int downloaded = toDownload.size() - hashFailed.size();
        return new InstallResult(downloaded, skipped, List.copyOf(hashFailed), List.copyOf(installedFiles));
    }
}
