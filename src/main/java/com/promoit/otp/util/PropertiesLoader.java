package com.promoit.otp.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads a {@code .properties} resource, preferring an external file in the working
 * directory over the copy bundled on the classpath. This lets channel/DB settings be
 * edited next to the jar and applied with a simple restart (no rebuild required).
 */
public final class PropertiesLoader {

    private PropertiesLoader() {
    }

    public static Properties load(String name) {
        Properties props = new Properties();
        Path external = Path.of(name);
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
                return props;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read external config " + external, e);
            }
        }
        try (InputStream in = PropertiesLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Config not found (filesystem or classpath): " + name);
            }
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath config " + name, e);
        }
    }
}
