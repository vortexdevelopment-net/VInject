package net.vortexdevelopment.vinject.di.config;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigObjectFactory;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.config.ConfigurationValueConverter;
import net.vortexdevelopment.vinject.config.YamlReflectiveMapping;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles mapping between YAML configuration sections and Java objects.
 * Separated from ConfigurationContainer to reduce complexity.
 */
public class ConfigurationMapper {

    private final ConfigurationValueConverter converter;
    private final Consumer<Object> onLoadInvoker;

    public ConfigurationMapper(DependencyContainer container) {
        this(c -> container.newInstance(c, false), instance -> container.getLifecycleManager().invokeOnLoad(instance));
    }

    ConfigurationMapper(ConfigObjectFactory factory, Consumer<Object> onLoadInvoker) {
        this.converter = new ConfigurationValueConverter(factory);
        this.onLoadInvoker = onLoadInvoker;
    }

    public void registerSerializer(YamlSerializerBase<?> serializer) {
        YamlSerializerRegistry.registerSerializer(serializer);
    }

    public boolean mapToInstance(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        return converter.mapToInstance(root, instance, clazz, basePath);
    }

    /**
     * Invokes {@code @OnLoad} after a mapped YAML object graph is fully hydrated.
     * Nested {@link YamlItem} values are processed before their parent so parent
     * callbacks can safely consume derived child state.
     *
     * @param instance mapped root object
     */
    public void invokeOnLoadRecursively(Object instance) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        invokeOnLoadRecursively(instance, true, visited);
    }

    private void invokeOnLoadRecursively(Object value, boolean mappedRoot, Set<Object> visited) {
        if (value == null || !visited.add(value)) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> invokeOnLoadRecursively(item, false, visited));
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> invokeOnLoadRecursively(item, false, visited));
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                invokeOnLoadRecursively(Array.get(value, i), false, visited);
            }
            return;
        }
        if (!mappedRoot && !value.getClass().isAnnotationPresent(YamlItem.class)) {
            return;
        }

        for (Class<?> current = value.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (shouldSkipField(field)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    invokeOnLoadRecursively(field.get(value), false, visited);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Unable to inspect mapped YAML field "
                            + current.getName() + "." + field.getName(), e);
                }
            }
        }

        this.onLoadInvoker.accept(value);
    }

    public void applyToConfig(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        if (clazz.isAnnotationPresent(YamlItem.class)
                || YamlSerializerRegistry.hasSerializer(clazz)
                || YamlReflectiveMapping.supportsFieldReflection(clazz)) {
            root.set(basePath, instance);
        } else {
            for (Field field : clazz.getDeclaredFields()) {
                if (shouldSkipField(field)) continue;
                if (field.isAnnotationPresent(YamlId.class)
                        || field.isAnnotationPresent(ItemRoot.class)
                        || field.getName().startsWith("__vinject_yaml")) continue;
                field.setAccessible(true);

                String keyPath = converter.getKeyPath(field, basePath);
                Object value = field.get(instance);

                if (field.isAnnotationPresent(Comment.class)) {
                    Comment comment = field.getAnnotation(Comment.class);
                    String commentText = String.join("\n", comment.value());
                    root.set(keyPath, value, commentText);
                } else {
                    root.set(keyPath, value);
                }
            }
        }
    }

    public Field findIdFieldForClass(Class<?> clazz) {
        return converter.findIdFieldForClass(clazz);
    }

    public Object convertValue(Object value, java.lang.reflect.Type targetType, Field field) throws Exception {
        return converter.convertValue(value, targetType, field);
    }

    private static boolean shouldSkipField(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic();
    }
}
