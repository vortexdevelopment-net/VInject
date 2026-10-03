package net.vortexdevelopment.vinject.di.config;

import net.vortexdevelopment.vinject.annotation.lifecycle.OnLoad;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationMapperLifecycleTest {

    @Test
    void invokesNestedOnLoadAfterYamlHydrationInPostOrder() throws Exception {
        ConfigurationMapper mapper = new ConfigurationMapper(type -> {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }, ConfigurationMapperLifecycleTest::invokeOnLoad);
        LifecycleConfig config = new LifecycleConfig();
        YamlConfig yaml = YamlConfig.load("""
                Trees:
                  oak:
                    Name: Oak
                    Loot:
                      Material: OAK_SAPLING
                """);

        boolean missingKeys = mapper.mapToInstance(yaml, config, LifecycleConfig.class, "");
        mapper.invokeOnLoadRecursively(config);

        LifecycleTree tree = config.trees.get("oak");
        assertFalse(missingKeys);
        assertTrue(tree.loot.loaded);
        assertTrue(tree.loaded);
        assertEquals("oak", tree.loadedId);
        assertTrue(tree.childWasLoaded);
        assertTrue(config.loaded);
        assertTrue(config.childWasLoaded);
    }

    private static void invokeOnLoad(Object instance) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(OnLoad.class)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(instance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class LifecycleConfig {
        @Key("Trees")
        private Map<String, LifecycleTree> trees = new LinkedHashMap<>();

        private transient boolean loaded;
        private transient boolean childWasLoaded;

        @OnLoad
        public void onLoad() {
            this.loaded = true;
            this.childWasLoaded = this.trees.values().stream().allMatch(tree -> tree.loaded);
        }
    }

    @YamlItem
    static class LifecycleTree {
        @YamlId
        private String id;

        @Key("Name")
        private String name;

        @Key("Loot")
        private LifecycleLoot loot;

        private transient boolean loaded;
        private transient String loadedId;
        private transient boolean childWasLoaded;

        @OnLoad
        public void onLoad() {
            this.loaded = true;
            this.loadedId = this.id;
            this.childWasLoaded = this.loot.loaded;
        }
    }

    @YamlItem
    static class LifecycleLoot {
        @Key("Material")
        private String material;

        private transient boolean loaded;

        @OnLoad
        public void onLoad() {
            this.loaded = this.material != null;
        }
    }
}
