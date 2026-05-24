package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionFallbackYamlTest {

    public static class MyOtherClass {
        private String first;
        private String second;

        public MyOtherClass() {
        }

        public MyOtherClass(String first, String second) {
            this.first = first;
            this.second = second;
        }

        public String getFirst() {
            return first;
        }

        public String getSecond() {
            return second;
        }

        @Override
        public String toString() {
            return "MustNotUseToString";
        }
    }

    public static class MyClass {
        private int test1 = 2;
        private String test2 = "Test2";
        private MyOtherClass test3 = new MyOtherClass("firstValue", "secondValue");

        public MyClass() {
        }

        public int getTest1() {
            return test1;
        }

        public String getTest2() {
            return test2;
        }

        public MyOtherClass getTest3() {
            return test3;
        }
    }

    @Test
    void serializesUnannotatedClassByFields() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        config.set("root", new MyClass());

        String rendered = config.render();
        System.out.println(rendered);

        assertFalse(rendered.contains("MustNotUseToString"));
        assertTrue(rendered.contains("root:"));
        assertTrue(rendered.contains("test1: 2"));
        assertTrue(rendered.contains("test2: \"Test2\""));
        assertTrue(rendered.contains("test3:"));
        assertTrue(rendered.contains("first: \"firstValue\""));
        assertTrue(rendered.contains("second: \"secondValue\""));
    }

    @Test
    void roundTripsUnannotatedClassByFields() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        config.set("root", new MyClass());

        YamlConfig loaded = YamlConfig.load(config.render());
        MyClass result = loaded.get("root", MyClass.class);

        assertNotNull(result);
        assertEquals(2, result.getTest1());
        assertEquals("Test2", result.getTest2());
        assertNotNull(result.getTest3());
        assertEquals("firstValue", result.getTest3().getFirst());
        assertEquals("secondValue", result.getTest3().getSecond());
    }
}
