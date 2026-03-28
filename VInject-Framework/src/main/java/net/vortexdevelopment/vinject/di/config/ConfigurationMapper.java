package net.vortexdevelopment.vinject.di.config;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.config.ConfigurationValueConverter;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Handles mapping between YAML configuration sections and Java objects.
 * Separated from ConfigurationContainer to reduce complexity.
 */
public class ConfigurationMapper {

    private final ConfigurationValueConverter converter;

    public ConfigurationMapper(DependencyContainer container) {
        this.converter = new ConfigurationValueConverter(c -> container.newInstance(c, false));
    }

    public void registerSerializer(YamlSerializerBase<?> serializer) {
        YamlSerializerRegistry.registerSerializer(serializer);
    }

    public void mapToInstance(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        converter.mapToInstance(root, instance, clazz, basePath);
    }

    public void applyToConfig(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        if (clazz.isAnnotationPresent(YamlItem.class)
                || YamlSerializerRegistry.hasSerializer(clazz)) {
            root.set(basePath, instance);
        } else {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
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
}
