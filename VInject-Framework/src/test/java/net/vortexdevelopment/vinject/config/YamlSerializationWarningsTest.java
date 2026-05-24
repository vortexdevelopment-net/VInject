package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.config.yaml.DocumentNode;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSerializationWarningsTest {

    /** No instance fields - cannot use reflective YAML mapping. */
    static final class EmptyType {
    }

    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
        YamlSerializationWarnings.clearWarningsForTests();
    }

    @Test
    void detectsDefaultObjectToString() {
        Object value = new Object();
        assertTrue(YamlSerializationWarnings.isDefaultObjectToString(value));
        assertTrue(value.toString().contains("@"));
    }

    @Test
    void doesNotFlagCustomToString() {
        assertFalse(YamlSerializationWarnings.isDefaultObjectToString("hello"));
        assertFalse(YamlSerializationWarnings.isDefaultObjectToString(new StringBuilder("built")));
    }

    @Test
    void warnsWhenRenderingUnmappableType() {
        System.setErr(new PrintStream(capturedErr));

        YamlConfig config = new YamlConfig(new DocumentNode());
        config.set("bad", new EmptyType());
        config.render();

        String err = capturedErr.toString();
        assertTrue(err.contains("[VInject YAML]"));
        assertTrue(err.contains("Object.toString()") || err.contains("toString()"));
        assertTrue(err.contains("EmptyType"));
    }
}
