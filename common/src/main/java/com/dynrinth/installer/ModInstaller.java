package com.dynrinth.installer;

import com.dynrinth.api.ModrinthVersion;
import com.dynrinth.net.HttpUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ModInstaller {

    public static final class Progress {
        private final int current;
        private final int total;
        private final String name;

        public Progress(int current, int total, String name) {
            this.current = current;
            this.total = total;
            this.name = name;
        }

        public int current() {
            return current;
        }

        public int total() {
            return total;
        }

        public String name() {
            return name;
        }
    }

    public static final class InstallResult {
        private final int downloaded;
        private final int skipped;
        private final List<String> hashFailed;
        private final List<String> downloadedFilenames;

        public InstallResult(int downloaded, int skipped, List<String> hashFailed,
                             List<String> downloadedFilenames) {
            this.downloaded = downloaded;
            this.skipped = skipped;
            this.hashFailed = hashFailed;
            this.downloadedFilenames = downloadedFilenames;
        }

        public int downloaded() {
            return downloaded;
        }

        public int skipped() {
            return skipped;
        }

        public List<String> hashFailed() {
            return hashFailed;
        }

        public List<String> downloadedFilenames() {
            return downloadedFilenames;
        }
    }

    public static Path resolveInstallPath(Path installDir, String filename) {
        if (filename == null || filename.trim().isEmpty()) return null;

        Path root = installDir.toAbsolutePath().normalize();
        Path dest = root.resolve(filename).normalize();
        if (!dest.startsWith(root) || !root.equals(dest.getParent())) return null;

        return dest;
    }

    /**
     * Resolves a game-dir-relative path for removal purposes.
     * Accepts any depth (e.g. "mods/file.jar", "saves/World/datapacks/pack.zip").
     * Only rejects paths that escape gameDir or resolve to gameDir itself.
     */
    public static Path resolveGamePath(Path gameDir, String relPath) {
        if (relPath == null || relPath.trim().isEmpty()) return null;

        Path root = gameDir.toAbsolutePath().normalize();
        Path dest = root.resolve(relPath).normalize();
        if (!dest.startsWith(root) || dest.equals(root)) return null;

        return dest;
    }

    public static InstallResult install(List<ModrinthVersion> versions, Path installDir,
                                        String userAgent, Consumer<Progress> onProgress)
            throws Exception {

        // Scan install directory once for existing slugs to detect already-installed mods
        // whose filenames differ only in version (e.g. fabric-api-0.91 vs fabric-api-0.92)
        Set<String> installedSlugs = scanSlugs(installDir);

        List<ModrinthVersion> toDownload = new ArrayList<ModrinthVersion>();
        int skipped = 0;

        for (ModrinthVersion version : versions) {
            ModrinthVersion.ModrinthFile file = version.getPrimaryFile();
            if (file == null || file.filename == null) {
                // No downloadable file — not a skip (already present), just nothing to do
                continue;
            }

            Path dest = resolveInstallPath(installDir, file.filename);
            if (dest == null) {
                throw new IllegalArgumentException("Unsafe filename from Modrinth: " + file.filename);
            }

            if (Files.exists(dest)) {
                skipped++;
            } else {
                String slug = extractModSlug(file.filename);
                if (slug != null && installedSlugs.contains(slug)) {
                    skipped++;
                } else {
                    toDownload.add(version);
                }
            }
        }

        int total   = toDownload.size();

        AtomicInteger counter         = new AtomicInteger(0);
        List<String>  hashFailed      = new CopyOnWriteArrayList<>();
        List<String>  downloadedFiles = new CopyOnWriteArrayList<>();

        if (total == 0) {
            return new InstallResult(0, skipped, Collections.<String>emptyList(), Collections.<String>emptyList());
        }

        int threads = Math.max(1, Math.min(total, 4));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<CompletableFuture<Void>> futures = new ArrayList<CompletableFuture<Void>>();
        for (final ModrinthVersion version : toDownload) {
            futures.add(CompletableFuture.runAsync(() -> {
                ModrinthVersion.ModrinthFile file = version.getPrimaryFile();
                int    idx         = counter.incrementAndGet();
                String displayName = (version.name != null && !version.name.trim().isEmpty())
                    ? version.name
                    : file.filename;
                onProgress.accept(new Progress(idx, total, displayName));

                Path dest = resolveInstallPath(installDir, file.filename);
                if (dest == null) {
                    throw new IllegalStateException("Unsafe filename from Modrinth: " + file.filename);
                }

                try {
                    HttpUtil.downloadToFile(file.url, dest, userAgent, 10_000, 60_000);

                    // Verify SHA-1 if available
                    String expectedSha1 = file.hashes != null ? file.hashes.get("sha1") : null;
                    if (expectedSha1 != null) {
                        byte[]        bytes  = Files.readAllBytes(dest);
                        MessageDigest md     = MessageDigest.getInstance("SHA-1");
                        String        actual = toHex(md.digest(bytes));
                        if (!actual.equalsIgnoreCase(expectedSha1)) {
                            Files.deleteIfExists(dest);
                            hashFailed.add(displayName + " (hash mismatch)");
                            return;
                        }
                    }

                    downloadedFiles.add(file.filename);
                } catch (Exception e) {
                    try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
                    hashFailed.add(displayName + " (" + e.getMessage() + ")");
                }
            }, pool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
        } finally {
            pool.shutdown();
        }

        int downloaded = downloadedFiles.size();
        return new InstallResult(
            downloaded,
            skipped,
            Collections.unmodifiableList(new ArrayList<String>(hashFailed)),
            Collections.unmodifiableList(new ArrayList<String>(downloadedFiles)));
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            int value = b & 0xFF;
            if (value < 16) builder.append('0');
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    /**
     * Scans the install directory and returns a set of mod slugs already present.
     * Only considers .jar and .zip files.
     */
    private static Set<String> scanSlugs(Path installDir) {
        Set<String> slugs = new HashSet<String>();
        if (!Files.isDirectory(installDir)) return slugs;
        try (java.util.stream.Stream<Path> stream = Files.list(installDir)) {
            stream.map(p -> p.getFileName().toString())
                  .map(ModInstaller::extractModSlug)
                  .filter(s -> s != null)
                  .forEach(slugs::add);
        } catch (IOException ignored) {}
        return slugs;
    }

    /**
     * Extracts the mod slug from a filename, stripping the version and MC version suffix.
     * Examples:
     *   fabric-api-0.92.3+1.21.4.jar  → fabric-api
     *   sodium-mc1.21.1-0.6.0-fabric.jar → sodium
     *   iris-1.8.0+mc1.21.1.jar       → iris
     *   ferritecore-7.0.1-fabric.jar   → ferritecore
     */
    static String extractModSlug(String filename) {
        if (filename == null) return null;
        int dotIdx = filename.lastIndexOf('.');
        String name = dotIdx >= 0 ? filename.substring(0, dotIdx) : filename;

        // Strip MC/game version after '+' first
        int plusIdx = name.indexOf('+');
        if (plusIdx > 0) name = name.substring(0, plusIdx);

        // Split on '-' and collect parts until we hit a version-like token
        String[] parts = name.split("-");
        StringBuilder slug = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            // Version segment: starts with a digit or "mc" followed by a digit
            if (Character.isDigit(part.charAt(0))) break;
            if (part.length() >= 3 && part.startsWith("mc") && Character.isDigit(part.charAt(2))) break;
            if (slug.length() > 0) slug.append('-');
            slug.append(part.toLowerCase());
        }
        return slug.length() > 0 ? slug.toString() : null;
    }
}
