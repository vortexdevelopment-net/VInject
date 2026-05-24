package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Detects types that should be serialized/deserialized via declared instance fields
 * when no {@link YamlItem} annotation or custom {@link net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase} is present.
 */
public final class YamlReflectiveMapping {

    private YamlReflectiveMapping() {
    }

    /**
     * Whether values of this type should be written as a YAML mapping built from instance fields.
     */
    public static boolean supportsFieldReflection(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (clazz.isPrimitive() || clazz.isEnum() || clazz.isArray()) {
            return false;
        }
        if (clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class) {
            return false;
        }
        if (Map.class.isAssignableFrom(clazz)
                || List.class.isAssignableFrom(clazz)
                || Collection.class.isAssignableFrom(clazz)
                || ConfigurationSection.class.isAssignableFrom(clazz)) {
            return false;
        }
        if (clazz.isAnnotationPresent(YamlItem.class)
                || clazz.isAnnotationPresent(YamlConfiguration.class)) {
            return false;
        }
        if (YamlSerializerRegistry.hasSerializer(clazz)) {
            return false;
        }
        String name = clazz.getName();
        if (name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("kotlin.")) {
            return false;
        }
        return hasMappableFields(clazz);
    }

    private static boolean hasMappableFields(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(YamlId.class)
                        || field.isAnnotationPresent(ItemRoot.class)
                        || field.getName().startsWith("__vinject_yaml")) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
}
