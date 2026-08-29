package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConfigurationValueConverterTest {

    @Test
    void convertsInlineAndBlockSequencesToTypedValues() throws Exception {
        YamlConfig config = YamlConfig.load("""
                Upgrade Costs:
                  tier: [1000, 2500, 5000]
                Names: [STONE, DIAMOND]
                Block:
                  - 4
                  - 8
                Decimals:
                  - 0.5
                  - 1.25
                """);

        ConfigurationValueConverter converter = new ConfigurationValueConverter(clazz -> {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        });
        UpgradeCostsHolder holder = new UpgradeCostsHolder();

        converter.mapToInstance(config, holder, UpgradeCostsHolder.class, "");

        assertEquals(List.of(1000D, 2500D, 5000D), holder.costs.get("tier"));
        assertArrayEquals(new String[]{"STONE", "DIAMOND"}, holder.names);
        assertArrayEquals(new int[]{4, 8}, holder.block);
        assertArrayEquals(new double[]{0.5D, 1.25D}, holder.decimals);
        assertInstanceOf(List.class, config.get("Upgrade Costs.tier"));
        org.junit.jupiter.api.Assertions.assertTrue(config.isList("Upgrade Costs.tier"));
    }

    @Test
    void convertsInlineSequenceStringForListAndArrayTargets() throws Exception {
        ConfigurationValueConverter converter = new ConfigurationValueConverter(clazz -> null);

        var listField = UpgradeCostsHolder.class.getDeclaredField("costs");
        var namesField = UpgradeCostsHolder.class.getDeclaredField("names");

        @SuppressWarnings("unchecked")
        Map<String, List<Double>> costs = (Map<String, List<Double>>) converter.convertValue(
                Map.of("tier", "[1000, 2500]"), listField.getGenericType(), listField);
        String[] names = (String[]) converter.convertValue("[STONE, DIAMOND]", namesField.getGenericType(), namesField);

        assertEquals(List.of(1000D, 2500D), costs.get("tier"));
        assertArrayEquals(new String[]{"STONE", "DIAMOND"}, names);
    }

    static class UpgradeCostsHolder {
        @Key("Upgrade Costs")
        private Map<String, List<Double>> costs;

        @Key("Names")
        private String[] names;

        @Key("Block")
        private int[] block;

        @Key("Decimals")
        private double[] decimals;
    }
}
