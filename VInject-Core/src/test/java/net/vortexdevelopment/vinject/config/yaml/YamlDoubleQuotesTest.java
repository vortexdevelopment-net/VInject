package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlDoubleQuotesTest {

    @Test
    public void testStringQuoting() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        config.set("simple", "hello");
        config.set("withSpaces", "hello world");
        config.set("alreadyQuoted", "\"quoted\"");
        config.set("number", 42);
        
        String rendered = config.render();
        System.out.println("Rendered:\n" + rendered);
        
        assertTrue(rendered.contains("simple: \"hello\""));
        assertTrue(rendered.contains("withSpaces: \"hello world\""));
        assertTrue(rendered.contains("alreadyQuoted: \"\\\"quoted\\\"\""));
        assertTrue(rendered.contains("number: 42"));
        
        YamlConfig config2 = YamlConfig.load(rendered);
        assertEquals("hello", config2.get("simple"));
        assertEquals("hello world", config2.get("withSpaces"));
        assertEquals("\"quoted\"", config2.get("alreadyQuoted"));
        assertEquals(Integer.valueOf(42), config2.get("number"));
        
        String rendered2 = config2.render();
        assertEquals(rendered.trim(), rendered2.trim());
    }
}
