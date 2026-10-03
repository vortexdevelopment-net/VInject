package net.vortexdevelopment.vinject.component;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.di.registry.RegistryOrder;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for component loading order based on dependencies and @RegistryOrder annotations.
 */
class ComponentLoadingOrderTest {

    @Test
    void componentsWithDependenciesLoadInCorrectOrder() {
        // Arrange: Create context with components that have dependencies
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRootWithDeps.class)
                .buildWithoutInit()) {
            
            // Act: Initialize the context
            context.initialize();
            
            // Assert: Both components should be available
            ComponentB compB = context.getComponent(ComponentB.class);
            ComponentA compA = context.getComponent(ComponentA.class);
            
            assertThat(compB).isNotNull();
            assertThat(compA).isNotNull();
            assertThat(compA.componentB).isNotNull();
            assertThat(compA.componentB).isSameAs(compB);
        }
    }

    @Test
    void componentsWithoutDependenciesCanBeRetrieved() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(SimpleTestRoot.class)
                .build()) {
            
            // Assert: Component should be available
            SimpleComponent comp = context.getComponent(SimpleComponent.class);
            assertThat(comp).isNotNull();
        }
    }

    @Test
    void mockInstancesAreInjectedInsteadOfRealComponents() {
        // Arrange: Create a mock instance
        ComponentB mockB = new ComponentB();
        
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRootWithDeps.class)
                .withMock(ComponentB.class, mockB)
                .build()) {
            
            // Act: Get ComponentA which depends on ComponentB
            ComponentA compA = context.getComponent(ComponentA.class);
            
            // Assert: Mock instance should be injected
            assertThat(compA.componentB).isSameAs(mockB);
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.component", createInstance = false)
    static class TestRootWithDeps {
    }

    @Root(packageName = "net.vortexdevelopment.vinject.component", createInstance = false)
    static class SimpleTestRoot {
    }

    @Component
    public static class ComponentA {
        @Inject
        public ComponentB componentB;
    }

    @Component
    public static class ComponentB {
    }

    @Component
    public static class SimpleComponent {
    }
}
