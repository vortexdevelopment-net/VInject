package net.vortexdevelopment.vinject.di.registry;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for custom @Registry annotations and their handlers.
 * Verifies that registry annotations are detected and processed correctly.
 */
class RegistryAnnotationTest {

    @Test
    void customRegistryAnnotationCanBeUsed() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(RegistryTestRoot.class)
                .build()) {
            
            // Assert: Component with custom registry annotation should be available
            CustomRegistryComponent comp = context.getComponent(CustomRegistryComponent.class);
            assertThat(comp).isNotNull();
        }
    }

    @Test
    void componentWithMultipleAnnotationsLoads() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(RegistryTestRoot.class)
                .build()) {
            
            // Assert: Component should be available
            MultiAnnotatedComponent comp = context.getComponent(MultiAnnotatedComponent.class);
            assertThat(comp).isNotNull();
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.di.registry", createInstance = false)
    static class RegistryTestRoot {
    }

    @CustomRegistryAnnotation
    public static class CustomRegistryComponent {
    }

    @CustomRegistryAnnotation
    public static class MultiAnnotatedComponent {
    }


    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomRegistryAnnotation {
    }

    @Registry(annotation = CustomRegistryAnnotation.class)
    static class CustomRegistryHandler extends AnnotationHandler {
        @Override
        public void handle(Class<?> clazz, Object instance, DependencyContainer dependencyContainer) {
            if (instance == null) {
                Object newInstance = dependencyContainer.newInstance(clazz);
                dependencyContainer.addBean(clazz, newInstance);
                System.out.println("Registered component: " + clazz.getName());
            }
        }
    }
}
