package com.dynrinth.api;

import java.util.List;

public class ModListState {
    public static class ContentGroup {
        public String contentType;
        public String loader;
        public String shaderLoader;
        public String pluginLoader;
        public List<String> mods;
    }

    public int formatVersion;
    public String version;
    public String source;
    public String contentType;
    public String loader;
    public String shaderLoader;
    public String pluginLoader;
    public List<String> mods;
    public List<ContentGroup> groups;
}
