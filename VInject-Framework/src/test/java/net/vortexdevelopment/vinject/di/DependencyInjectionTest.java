package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.ComponentTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for dependency injection functionality.
 */
class DependencyInjectionTest {

    @Test
    void fieldInjectionWorksCorrectly() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ComponentWithFieldInjection comp = context.getComponent(ComponentWithFieldInjection.class);

            // Assert
            assertThat(comp.dependency).isNotNull();
            assertThat(ComponentTestUtils.verifyAllDependenciesInjected(comp)).isTrue();
        }
    }

    @Test
    void multipleDependenciesAreInjected() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ComponentWithMultipleDependencies comp = context.getComponent(ComponentWithMultipleDependencies.class);

            // Assert
            assertThat(comp.dependencyA).isNotNull();
            assertThat(comp.dependencyB).isNotNull();
            assertThat(comp.dependencyC).isNotNull();
        }
    }

    @Test
    void transitiveDependenciesAreResolved() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            // ComponentA depends on ComponentB, which depends on ComponentC
            ComponentA compA = context.getComponent(ComponentA.class);

            // Assert
            assertThat(compA.componentB).isNotNull();
            assertThat(compA.componentB.componentC).isNotNull();
        }
    }

    @Test
    void singletonScopeIsMaintained() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            // Get the same component twice
            SimpleDependency dep1 = context.getComponent(SimpleDependency.class);
            SimpleDependency dep2 = context.getComponent(SimpleDependency.class);

            // Assert: Should be the same instance (singleton)
            assertThat(dep1).isSameAs(dep2);
        }
    }

    @Test
    void componentWithoutDependenciesIsCreated() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            SimpleDependency comp = context.getComponent(SimpleDependency.class);

            // Assert
            assertThat(comp).isNotNull();
            assertThat(ComponentTestUtils.hasDependencies(SimpleDependency.class)).isFalse();
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.di", createInstance = false)
    static class TestRoot {
    }

    @Component
    public static class ComponentWithFieldInjection {
        @Inject
        public SimpleDependency dependency;
    }

    @Component
    public static class ComponentWithMultipleDependencies {
        @Inject
        public DependencyA dependencyA;

        @Inject
        public DependencyB dependencyB;

        @Inject
        public DependencyC dependencyC;
    }

    @Component
    public static class ComponentA {
        @Inject
        public ComponentB componentB;
    }

    @Component
    public static class ComponentB {
        @Inject
        public ComponentC componentC;
    }

    @Component
    public static class ComponentC {
    }

    @Component
    public static class SimpleDependency {
    }

    @Component
    public static class DependencyA {
    }

    @Component
    public static class DependencyB {
    }

    @Component
    public static class DependencyC {
    }
}
