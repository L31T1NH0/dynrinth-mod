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
    public List<Dependency> dependencies;

    public static class ModrinthFile {
        public String url;
        public String filename;
        public boolean primary;
        public Map<String, String> hashes; // "sha1", "sha512"
    }

    public static class Dependency {
        public String project_id;
        public String dependency_type; // "required", "optional", "incompatible", "embedded"
    }

    public ModrinthFile getPrimaryFile() {
        if (files == null || files.isEmpty()) return null;
        for (ModrinthFile file : files) {
            if (file.primary) return file;
        }
        return files.get(0);
    }
}
