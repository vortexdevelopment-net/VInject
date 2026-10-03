package net.vortexdevelopment.vinject.http.dispatcher;

import java.util.Map;

/**
 * Holder class that stores path variables extracted from request URIs.
 * This class is registered within the InjectionContext to make path variables
 * injectable into controller method parameters.
 */
public class PathVariablesHolder {
    private final Map<String, String> variables;

    public PathVariablesHolder(Map<String, String> variables) {
        this.variables = variables;
    }

    /**
     * Get a path variable value by its name.
     *
     * @param name The name of the path variable
     * @return The value of the path variable, or null if not found
     */
    public String get(String name) {
        return variables.get(name);
    }
    
    /**
     * Get all path variables.
     *
     * @return Map of all path variables
     */
    public Map<String, String> getVariables() {
        return variables;
    }
}
