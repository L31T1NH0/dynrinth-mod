package com.dynrinth.api;

import com.dynrinth.net.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ModrinthApi {

    private static final String API = "https://api.modrinth.com/v2";
    private static final Gson GSON = new Gson();

    public static final class ResolveResult {
        private final List<ModrinthVersion> versions;
        private final List<String> notFound;
        private final int depsAdded;

        public ResolveResult(List<ModrinthVersion> versions, List<String> notFound, int depsAdded) {
            this.versions  = versions;
            this.notFound  = notFound;
            this.depsAdded = depsAdded;
        }

        public List<ModrinthVersion> versions()  { return versions;  }
        public List<String>          notFound()  { return notFound;  }
        public int                   depsAdded() { return depsAdded; }
    }

    /**
     * Resolves versions for the given project IDs and recursively resolves
     * required dependencies via BFS. The loader filter is omitted when null
     * (e.g. for resourcepacks).
     */
    public static ResolveResult resolveVersions(
            List<String> projectIds, String mcVersion, String loader, String userAgent)
            throws Exception {

        List<ModrinthVersion> resolved = new ArrayList<>();
        List<String>          notFound = new ArrayList<>();

        // Pre-encode query params once — mcVersion and loader never change across iterations
        String gvParam     = "game_versions=" + URLEncoder.encode("[\"" + mcVersion + "\"]", "UTF-8");
        String loaderParam = (loader != null && !loader.isEmpty())
            ? "&loaders=" + URLEncoder.encode("[\"" + loader + "\"]", "UTF-8")
            : "";

        // BFS: track all enqueued IDs to avoid cycles and duplicate resolution
        Set<String>  explicitSet   = new LinkedHashSet<>(projectIds);
        Set<String>  queued        = new LinkedHashSet<>(projectIds);
        List<String> queue         = new ArrayList<>(projectIds);
        int          explicitCount = projectIds.size();

        while (!queue.isEmpty()) {
            List<String> current = new ArrayList<>(queue);
            queue.clear();

            for (String id : current) {
                String url = API + "/project/" + URLEncoder.encode(id, "UTF-8")
                    + "/version?" + gvParam + loaderParam;

                HttpUtil.Response res = HttpUtil.get(url, userAgent, "application/json", 10_000, 15_000);

                if (res.statusCode() == 404) {
                    notFound.add(id);
                    continue;
                }

                if (res.statusCode() != 200) {
                    throw new RuntimeException(
                        "Modrinth API error resolving '" + id + "': HTTP " + res.statusCode());
                }

                List<ModrinthVersion> versions =
                    GSON.fromJson(res.body(), new TypeToken<List<ModrinthVersion>>() {}.getType());

                if (versions == null || versions.isEmpty()) {
                    notFound.add(id);
                    continue;
                }

                ModrinthVersion version = versions.get(0);
                resolved.add(version);

                // Enqueue required dependencies not yet seen
                if (version.dependencies != null) {
                    for (ModrinthVersion.Dependency dep : version.dependencies) {
                        if ("required".equals(dep.dependency_type)
                                && dep.project_id != null
                                && !queued.contains(dep.project_id)) {
                            queued.add(dep.project_id);
                            queue.add(dep.project_id);
                        }
                    }
                }
            }
        }

        // Count only explicit failures so dep failures don't corrupt the depsAdded count
        int explicitFailed = 0;
        for (String id : notFound) {
            if (explicitSet.contains(id)) explicitFailed++;
        }
        int depsAdded = resolved.size() - (explicitCount - explicitFailed);
        return new ResolveResult(resolved, notFound, Math.max(0, depsAdded));
    }
}
