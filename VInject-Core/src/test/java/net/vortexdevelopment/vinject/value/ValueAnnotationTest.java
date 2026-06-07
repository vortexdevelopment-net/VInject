package net.vortexdevelopment.vinject.value;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for @Value annotation and property resolution.
 */
class ValueAnnotationTest {

    @Test
    void valueWithDefaultIsInjected() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ComponentWithValue comp = context.getComponent(ComponentWithValue.class);

            // Assert: Should use default value
            assertThat(comp.greetingMessage).isNotNull();
            assertThat(comp.greetingMessage).contains("Welcome");
        }
    }

    @Test
    void valueFromPropertiesIsResolved() {
        // Arrange: Set system property
        System.setProperty("app.name", "Test Application");

        try {
            // Act
            try (TestApplicationContext context = TestApplicationContext.builder()
                    .withRootClass(TestRoot.class)
                    .build()) {
                
                ComponentWithValue comp = context.getComponent(ComponentWithValue.class);

                // Assert
                assertThat(comp.appName).isEqualTo("Test Application");
            }
        } finally {
            // Cleanup
            System.clearProperty("app.name");
        }
    }

    @Test
    void valueWithDifferentTypesWorks() {
        // Arrange
        System.setProperty("app.port", "8080");
        System.setProperty("app.debug", "true");

        try {
            // Act
            try (TestApplicationContext context = TestApplicationContext.builder()
                    .withRootClass(TestRoot.class)
                    .build()) {
                
                ComponentWithDifferentTypes comp = context.getComponent(ComponentWithDifferentTypes.class);

                // Assert
                assertThat(comp.port).isEqualTo(8080);
                assertThat(comp.debugEnabled).isTrue();
            }
        } finally {
            System.clearProperty("app.port");
            System.clearProperty("app.debug");
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.value", createInstance = false)
    static class TestRoot {
    }

    @Component
    public static class ComponentWithValue {
        @Value("${app.greeting:Welcome to VInject!}")
        public String greetingMessage;

        @Value("${app.name:Default App}")
        public String appName;
    }

    @Component
    public static class ComponentWithDifferentTypes {
        @Value("${app.port:3000}")
        public int port;

        @Value("${app.debug:false}")
        public boolean debugEnabled;

        @Value("${app.timeout:30}")
        public long timeout;
    }
}
