package net.vortexdevelopment.vinject.config.serializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for YamlSerializerBase implementations.
 * Used by YamlConfig and ConfigurationMapper to handle custom type conversion.
 */
public class YamlSerializerRegistry {
    private static final Map<Class<?>, YamlSerializerBase<?>> SERIALIZERS = new ConcurrentHashMap<>();

    public static void registerSerializer(YamlSerializerBase<?> serializer) {
        if (serializer == null) return;
        Class<?> target = serializer.getTargetType();
        if (target != null) {
            SERIALIZERS.put(target, serializer);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> YamlSerializerBase<T> getSerializer(Class<T> clazz) {
        YamlSerializerBase<?> ser = SERIALIZERS.get(clazz);
        if (ser != null) return (YamlSerializerBase<T>) ser;

        for (Map.Entry<Class<?>, YamlSerializerBase<?>> entry : SERIALIZERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(clazz)) {
                return (YamlSerializerBase<T>) entry.getValue();
            }
        }
        return null;
    }

    public static boolean hasSerializer(Class<?> clazz) {
        if (SERIALIZERS.containsKey(clazz)) return true;
        for (Class<?> registered : SERIALIZERS.keySet()) {
            if (registered.isAssignableFrom(clazz)) return true;
        }
        return false;
    }
    
    public static Map<Class<?>, YamlSerializerBase<?>> getSerializers() {
        return SERIALIZERS;
    }
}
