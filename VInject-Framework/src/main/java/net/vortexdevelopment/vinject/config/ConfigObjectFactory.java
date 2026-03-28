package net.vortexdevelopment.vinject.config;

/**
 * Creates instances when converting nested YAML structures (map or section) into Java types.
 */
@FunctionalInterface
public interface ConfigObjectFactory {

    Object newInstance(Class<?> clazz) throws Exception;
}
