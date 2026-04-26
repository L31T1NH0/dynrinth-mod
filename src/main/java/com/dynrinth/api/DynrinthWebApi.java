package com.dynrinth.api;

import com.dynrinth.DynrinthMod;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DynrinthWebApi {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    /** Fetches the ModListState for a given code. Returns null if not found (404). */
    public static ModListState fetchState(String code) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DynrinthMod.DYNRINTH_BASE_URL + "/api/codes/" + code))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404 || response.statusCode() == 400) return null;
        if (response.statusCode() != 200) {
            throw new RuntimeException("Dynrinth API error: HTTP " + response.statusCode());
        }

        return GSON.fromJson(response.body(), ModListState.class);
    }
}
