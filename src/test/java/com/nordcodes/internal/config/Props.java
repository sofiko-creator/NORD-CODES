package com.nordcodes.internal.config;

import java.io.InputStream;
import java.util.Properties;

public final class Props {

    private static final String FILE = "application.properties";
    private static final Properties PROPS = load();

    private Props() {}

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Props.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in == null) {
                throw new IllegalStateException(FILE + " not found in classpath");
            }
            p.load(in);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + FILE, e);
        }
    }

    public static String get(String key, String defaultValue) {
        String v = PROPS.getProperty(key);
        return v != null ? v.trim() : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String v = PROPS.getProperty(key);
        return v != null ? Integer.parseInt(v.trim()) : defaultValue;
    }
}