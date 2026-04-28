package com.dynrinth.tracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class PackTracker {

    private static final Gson   GSON = new Gson();
    private static final String FILE = "dynrinth-packs.json";

    private final Path file;

    public PackTracker(Path gameDir) {
        this.file = gameDir.resolve(FILE);
    }

    public void record(String code, List<String> filenames) {
        Map<String, List<String>> packs = load();
        String key = code.toUpperCase();
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(filenames));

        if (unique.isEmpty()) packs.remove(key);
        else packs.put(key, unique);

        save(packs);
    }

    public List<String> getFilenames(String code) {
        List<String> filenames = load().get(code.toUpperCase());
        return filenames != null ? filenames : Collections.<String>emptyList();
    }

    public boolean hasCode(String code) {
        return load().containsKey(code.toUpperCase());
    }

    private Map<String, List<String>> load() {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Map<String, List<String>> map = GSON.fromJson(
                json, new TypeToken<Map<String, List<String>>>() {}.getType());
            return map != null ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
        } catch (Exception e) {
            System.err.println("[Dynrinth] Failed to read pack tracker (will reset): " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void save(Map<String, List<String>> packs) {
        try { Files.write(file, GSON.toJson(packs).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) {
            System.err.println("[Dynrinth] Failed to save pack tracker: " + e.getMessage());
        }
    }
}
