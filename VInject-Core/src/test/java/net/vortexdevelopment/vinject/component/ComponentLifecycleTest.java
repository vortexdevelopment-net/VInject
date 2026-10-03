package net.vortexdevelopment.vinject.component;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnDestroy;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.testing.ComponentTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for component lifecycle methods (@PostConstruct and @OnDestroy).
 */
class ComponentLifecycleTest {

    @Test
    void postConstructCalledAfterInjection() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(LifecycleTestRoot.class)
                .build()) {
            
            // Get the component
            LifecycleComponent comp = context.getComponent(LifecycleComponent.class);
            
            // Assert: @PostConstruct should have been called
            assertThat(comp.postConstructCalled).isTrue();
            assertThat(comp.dependency).isNotNull();
        }
    }

    @Test
    void onDestroyMethodExists() {
        // Arrange
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(LifecycleTestRoot.class)
                .build()) {
            
            LifecycleComponent comp = context.getComponent(LifecycleComponent.class);
            
            // Assert: Component should have @OnDestroy method
            assertThat(ComponentTestUtils.hasOnDestroyMethod(comp)).isTrue();
            
            // Act: Destroy context
            context.destroy();
            
            // Assert: Field should be set to indicate destruction
            assertThat(comp.onDestroyCalled).isTrue();
        }
    }

    @Test
    void allDependenciesInjectedBeforePostConstruct() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(LifecycleTestRoot.class)
                .build()) {
            
            LifecycleComponent comp = context.getComponent(LifecycleComponent.class);
            
            // Assert: All dependencies should be injected
            assertThat(ComponentTestUtils.verifyAllDependenciesInjected(comp)).isTrue();
            assertThat(comp.dependencyInjectedBeforePostConstruct).isTrue();
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.component", createInstance = false)
    static class LifecycleTestRoot {
    }

    @Component
    public static class LifecycleComponent {
        @Inject
        public SimpleDependency dependency;

        public boolean postConstructCalled = false;
        public boolean onDestroyCalled = false;
        public boolean dependencyInjectedBeforePostConstruct = false;

        @PostConstruct
        public void init() {
            postConstructCalled = true;
            dependencyInjectedBeforePostConstruct = (dependency != null);
        }

        @OnDestroy
        public void cleanup() {
            onDestroyCalled = true;
        }
    }

    @Component
    public static class SimpleDependency {
    }
}
