package net.vortexdevelopment.vinject.config.serializer;

import java.util.Map;

/**
 * Serializer interface for mapping between POJO and YAML-representable Map.
 * Implementations must return the target type using getTargetType().
 *
 * @param <T> target class
 */
public interface YamlSerializerBase<T> {

    /**
     * The target class this serializer handles
     */
    Class<T> getTargetType();

    /**
     * Serialize instance into a Map suitable for SnakeYAML dumping.
     */
    Map<String, Object> serialize(T instance);

    /**
     * Deserialize a Map (from YAML) into an instance of T.
     */
    T deserialize(Map<String, Object> map);
}
