package com.dynrinth.tracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        packs.put(code.toUpperCase(), new ArrayList<>(filenames));
        save(packs);
    }

    public List<String> getFilenames(String code) {
        return load().getOrDefault(code.toUpperCase(), List.of());
    }

    public boolean hasCode(String code) {
        return load().containsKey(code.toUpperCase());
    }

    private Map<String, List<String>> load() {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            String json = Files.readString(file);
            Map<String, List<String>> map = GSON.fromJson(
                json, new TypeToken<Map<String, List<String>>>() {}.getType());
            return map != null ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private void save(Map<String, List<String>> packs) {
        try { Files.writeString(file, GSON.toJson(packs)); }
        catch (IOException ignored) {}
    }
}
