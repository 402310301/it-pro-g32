package za.org.foodredistribution;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings from application.properties (inside src/main/resources).
 * An application.properties file in the folder you run from, or an environment
 * variable such as DB_PASSWORD, overrides the built-in value.
 */
public final class AppConfig {

    private final Properties properties = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            Path local = Path.of("application.properties");
            if (Files.exists(local)) {
                try (Reader reader = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read application.properties", e);
        }
    }

    public String get(String key, String defaultValue) {
        String envName = key.toUpperCase().replace('.', '_');
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return properties.getProperty(key, defaultValue).trim();
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }
}
