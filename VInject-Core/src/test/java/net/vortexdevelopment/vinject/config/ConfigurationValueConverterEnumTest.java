package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationValueConverterEnumTest {

    @Test
    void enumValuesAreMatchedCaseInsensitively() throws Exception {
        String yaml = """
                persistence:
                  mode: "ttl"
                """;

        YamlConfig config = YamlConfig.load(yaml);
        TestPersistence loaded = new TestPersistence();
        ConfigurationValueConverter converter = new ConfigurationValueConverter(clazz -> {
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        });
        converter.mapToInstance(config, loaded, TestPersistence.class, "persistence");

        assertEquals(Mode.TTL, loaded.mode);
    }

    enum Mode {
        PERMANENT,
        TTL
    }

    static class TestPersistence {
        @Key("mode")
        Mode mode;
    }
}
