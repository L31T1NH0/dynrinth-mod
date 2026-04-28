package com.dynrinth.api;

import com.dynrinth.net.HttpUtil;
import com.google.gson.Gson;

import java.net.URLEncoder;

public class DynrinthWebApi {

    private static final Gson GSON = new Gson();

    public static ModListState fetchState(String baseUrl, String code) throws Exception {
        HttpUtil.Response response = HttpUtil.get(
            baseUrl + "/api/codes/" + URLEncoder.encode(code, "UTF-8"),
            null,
            "application/json",
            10_000,
            15_000);

        if (response.statusCode() == 404 || response.statusCode() == 400) return null;
        if (response.statusCode() != 200)
            throw new RuntimeException("Dynrinth API error: HTTP " + response.statusCode());

        return GSON.fromJson(response.body(), ModListState.class);
    }
}
