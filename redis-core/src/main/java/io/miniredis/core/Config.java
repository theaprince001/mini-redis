package io.miniredis.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Config {

    private final Map<String, String> values = new LinkedHashMap<>();

    public Config() {
        values.put("maxmemory", "0");
        values.put("maxmemory-policy", "noeviction");
        values.put("save", "");
        values.put("appendonly", "no");
        values.put("appendfsync", "everysec");
        values.put("databases", "1");
        values.put("timeout", "0");
    }

    public String get(String key) { return values.get(key); }
    public boolean contains(String key) { return values.containsKey(key); }
    public Map<String, String> all() { return values; }
}