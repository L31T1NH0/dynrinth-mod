package com.dynrinth.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ModrinthApi {

    private static final String API = "https://api.modrinth.com/v2";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Gson GSON = new Gson();

    public record ResolveResult(List<ModrinthVersion> versions, List<String> notFound) {}

    public static ResolveResult resolveVersions(
            List<String> projectIds, String mcVersion, String loader, String userAgent)
            throws Exception {

        List<ModrinthVersion> resolved = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        String effectiveLoader = loader != null ? loader : "fabric";

        for (String id : projectIds) {
            String gv = URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8);
            String ld = URLEncoder.encode("[\"" + effectiveLoader + "\"]", StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + "/project/" + id + "/version?game_versions=" + gv + "&loaders=" + ld))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            List<ModrinthVersion> versions = null;
            if (res.statusCode() == 200) {
                versions = GSON.fromJson(res.body(), new TypeToken<List<ModrinthVersion>>() {}.getType());
            }

            if (versions != null && !versions.isEmpty()) {
                resolved.add(versions.get(0));
            } else {
                notFound.add(id);
            }
        }

        return new ResolveResult(resolved, notFound);
    }
}
