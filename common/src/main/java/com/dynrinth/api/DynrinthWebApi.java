package com.dynrinth.api;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DynrinthWebApi {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final Gson GSON = new Gson();

    public static ModListState fetchState(String baseUrl, String code) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/codes/" + code))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404 || response.statusCode() == 400) return null;
        if (response.statusCode() != 200)
            throw new RuntimeException("Dynrinth API error: HTTP " + response.statusCode());

        return GSON.fromJson(response.body(), ModListState.class);
    }
}
