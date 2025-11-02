package net.vortexdevelopment.vinject.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Environment class for reading application properties with support for:
 * - Environment variables (highest priority)
 * - System properties
 * - application.properties file (lowest priority)
 * 
 * Supports Spring Boot-style property resolution with default values.
 */
public class Environment {
    private static Environment instance;
    private final Properties applicationProperties;

    private Environment() {
        applicationProperties = new Properties();
        loadApplicationProperties();
    }

    /**
     * Get the singleton Environment instance.
     * 
     * @return The Environment instance
     */
    public static Environment getInstance() {
        if (instance == null) {
            synchronized (Environment.class) {
                if (instance == null) {
                    instance = new Environment();
                }
            }
        }
        return instance;
    }

    /**
     * Load properties from application.properties file and set them as system properties.
     * System properties are only set if they don't already exist (to preserve existing values).
     * This allows non-component classes to access properties via System.getProperty().
     * 
     * <p>Looks for application.properties in the following order:
     * <ol>
     *   <li>Current working directory (where the application is run from)</li>
     *   <li>Classpath resource (src/main/resources/application.properties)</li>
     * </ol>
     */
    private void loadApplicationProperties() {
        boolean loaded = false;
        
        // First, try to load from current working directory
        String workingDir = System.getProperty("user.dir");
        File propertiesFile = new File(workingDir, "application.properties");
        if (propertiesFile.exists() && propertiesFile.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(propertiesFile)) {
                applicationProperties.load(fileInputStream);
                loaded = true;
            } catch (Exception e) {
                // Working directory file exists but error reading - try classpath next
            }
        }
        
        // If not loaded from working directory, try classpath resource
        if (!loaded) {
            try (InputStream inputStream = Environment.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                if (inputStream != null) {
                    applicationProperties.load(inputStream);
                }
            } catch (Exception e) {
                // application.properties not found or error reading - that's okay
                // Properties can come from environment variables or system properties
            }
        }
        
        // Set system properties from application.properties (only if not already set)
        // This allows non-component classes to use System.getProperty()
        for (String key : applicationProperties.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                // Only set if not already present, preserving existing system properties
                System.setProperty(key, applicationProperties.getProperty(key));
            }
        }
    }
    
    /**
     * Initialize the Environment early, before dependency injection.
     * This ensures application.properties are loaded and set as system properties
     * so they're available for @Value injection and System.getProperty() calls.
     */
    public static void initialize() {
        getInstance(); // Force initialization which loads properties and sets system properties
    }

    /**
     * Get a property value with resolution priority:
     * 1. Environment variables (converted from dot notation to UPPER_SNAKE_CASE)
     * 2. System properties
     * 3. application.properties file
     * 
     * @param key The property key (supports dot notation, e.g., "app.name")
     * @return The property value, or null if not found
     */
    public String getProperty(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        // 1. Check environment variables (convert app.name to APP_NAME)
        String envKey = convertToEnvKey(key);
        String value = System.getenv(envKey);
        if (value != null) {
            return value;
        }

        // 2. Check system properties
        value = System.getProperty(key);
        if (value != null) {
            return value;
        }

        // 3. Check application.properties
        return applicationProperties.getProperty(key);
    }

    /**
     * Get a property value with a default value if not found.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if property is not found
     * @return The property value or default value
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a property value as an integer.
     * 
     * @param key The property key
     * @param defaultValue The default value if property is not found or invalid
     * @return The integer value
     */
    public int getPropertyAsInt(String key, int defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a property value as a long.
     * 
     * @param key The property key
     * @param defaultValue The default value if property is not found or invalid
     * @return The long value
     */
    public long getPropertyAsLong(String key, long defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a property value as a boolean.
     * 
     * @param key The property key
     * @param defaultValue The default value if property is not found or invalid
     * @return The boolean value
     */
    public boolean getPropertyAsBoolean(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Resolve a property expression that may contain a default value.
     * Supports Spring Boot-style syntax: ${property.key:defaultValue}
     * 
     * @param expression The property expression (e.g., "${app.name:MyApp}")
     * @return The resolved value
     */
    public String resolveProperty(String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        // Check if it's a property expression: ${key:default}
        if (expression.startsWith("${") && expression.endsWith("}")) {
            String content = expression.substring(2, expression.length() - 1);
            
            // Check for default value separator (use first colon to allow colons in default values)
            int colonIndex = content.indexOf(':');
            if (colonIndex >= 0) {
                String key = content.substring(0, colonIndex).trim();
                String defaultValue = content.substring(colonIndex + 1).trim();
                return getProperty(key, defaultValue);
            } else {
                // No default value
                String key = content.trim();
                String value = getProperty(key);
                if (value == null) {
                    throw new RuntimeException("Property '" + key + "' not found and no default value provided");
                }
                return value;
            }
        }

        // Not a property expression, return as-is
        return expression;
    }

    /**
     * Convert a property key from dot notation to environment variable format.
     * Example: "app.name" -> "APP_NAME", "database.host" -> "DATABASE_HOST"
     * 
     * @param key The property key in dot notation
     * @return The environment variable key in UPPER_SNAKE_CASE
     */
    private String convertToEnvKey(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }
}

