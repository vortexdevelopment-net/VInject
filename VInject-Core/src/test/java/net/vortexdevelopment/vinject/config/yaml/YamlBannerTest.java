package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class YamlBannerTest {

    @YamlConfiguration(file = "test.yml")
    @Comment({
        "+---------------------------------+",
        "|  Test File Banner               |",
        "+---------------------------------+"
    })
    public static class BannerConfig {
        public String value = "test";
    }

    @Test
    public void testFileBanner() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        BannerConfig bannerConfig = new BannerConfig();
        
        config.set("", bannerConfig);
        
        String rendered = config.render();
        System.out.println("Rendered:\n" + rendered);
        
        assertTrue(rendered.startsWith("# +-----"), "Should start with banner");
        assertTrue(rendered.contains("|  Test File Banner"), "Should contain banner content");
        
        // Test load and ensure no duplication
        YamlConfig config2 = YamlConfig.load(rendered);
        config2.set("", bannerConfig);
        
        String rendered2 = config2.render();
        System.out.println("Rendered 2:\n" + rendered2);
        
        // Strictly speaking, firstIndex is inside # +---
        // Let's count occurrences of the first line
        int count = countOccurrences(rendered2, "+---------------------------------+");
        assertEquals(2, count, "Banner should not be duplicated (should appear twice in one banner)");
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
