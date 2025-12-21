package net.vortexdevelopment.vinject.database.serializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for database serializers.
 * Maps types to their corresponding serializers.
 */
public class SerializerRegistry {
    private final Map<Class<?>, DatabaseSerializer<?>> serializers = new HashMap<>();

    /**
     * Register a serializer for a type.
     * 
     * @param type The type being serialized
     * @param serializer The serializer instance
     * @param <T> The type parameter
     */
    public <T> void registerSerializer(Class<T> type, DatabaseSerializer<T> serializer) {
        serializers.put(type, serializer);
    }

    /**
     * Get a serializer for a type.
     * 
     * @param type The type to get a serializer for
     * @return The serializer, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> DatabaseSerializer<T> getSerializer(Class<T> type) {
        return (DatabaseSerializer<T>) serializers.get(type);
    }

    /**
     * Check if a serializer is registered for a type.
     * 
     * @param type The type to check
     * @return true if a serializer is registered
     */
    public boolean hasSerializer(Class<?> type) {
        return serializers.containsKey(type);
    }
}
