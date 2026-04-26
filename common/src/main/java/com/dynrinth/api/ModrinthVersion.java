package com.dynrinth.api;

import java.util.List;
import java.util.Map;

public class ModrinthVersion {

    public String id;
    public String name;
    public String projectId;
    public List<String> gameVersions;
    public List<String> loaders;
    public List<ModrinthFile> files;

    public static class ModrinthFile {
        public String url;
        public String filename;
        public boolean primary;
        public Map<String, String> hashes; // "sha1", "sha512"
    }

    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) return null;
        return files.stream().filter(f -> f.primary).findFirst().orElse(files.get(0));
    }
}
