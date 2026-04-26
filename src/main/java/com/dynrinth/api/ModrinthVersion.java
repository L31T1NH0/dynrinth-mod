package com.dynrinth.api;

import java.util.List;

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
    }

    /** Returns the primary file, falling back to the first file. */
    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) return null;
        return files.stream()
            .filter(f -> f.primary)
            .findFirst()
            .orElse(files.get(0));
    }
}
