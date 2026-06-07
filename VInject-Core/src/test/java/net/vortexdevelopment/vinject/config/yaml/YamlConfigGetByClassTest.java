package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.ConfigurationSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigGetByClassTest {

    enum Sample {
        ALPHA,
        BETA
    }

    @Test
    void getByClass_resolvesEnumFromString() {
        ConfigurationSection config = YamlConfig.load("mode: ALPHA\nother: BETA");

        assertThat(config.get("mode", Sample.class)).isEqualTo(Sample.ALPHA);
        assertThat(config.get("other", Sample.class)).isEqualTo(Sample.BETA);
    }

    @Test
    void getByClass_coercesIntegerFromYamlNumber() {
        ConfigurationSection config = YamlConfig.load("port: 8080");

        assertThat(config.get("port", Integer.class)).isEqualTo(8080);
    }

    @Test
    void getByClass_returnsNullForInvalidEnum() {
        ConfigurationSection config = YamlConfig.load("mode: UNKNOWN");

        assertThat(config.get("mode", Sample.class)).isNull();
    }
}
