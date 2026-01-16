package net.vortexdevelopment.vinject.lifecycle;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnDestroy;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for component lifecycle methods (@PostConstruct and @OnDestroy).
 */
class ComponentLifecycleTest {

    private static final List<String> executionOrder = new ArrayList<>();

    @Test
    void postConstructCalledAfterDependencyInjection() {
        // Arrange
        executionOrder.clear();

        // Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            LifecycleComponent comp = context.getComponent(LifecycleComponent.class);

            // Assert
            assertThat(comp.isInitialized()).isTrue();
            assertThat(comp.isDependencyInjectedBeforeInit()).isTrue();
        }
    }

    @Test
    void onDestroyCalledDuringShutdown() {
        // Arrange
        executionOrder.clear();
        LifecycleComponent comp;

        // Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            comp = context.getComponent(LifecycleComponent.class);
            assertThat(comp.isDestroyed()).isFalse();
        } // Context closes here, should call @OnDestroy

        // Assert
        assertThat(comp.isDestroyed()).isTrue();
    }

    @Test
    void lifecycleMethodsCalledInCorrectOrder() {
        // Arrange
        executionOrder.clear();

        // Act
        OrderedComponent comp;
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            comp = context.getComponent(OrderedComponent.class);
            assertThat(comp.dependency).isNotNull();
            // At this point, only PostConstruct should have been called
            assertThat(executionOrder).containsExactly("PostConstruct");
        } // Context closes here, calling @OnDestroy

        // Assert: PostConstruct should be called before OnDestroy
        // Note: There may be multiple OnDestroy calls if multiple components have the annotation
        assertThat(executionOrder).startsWith("PostConstruct");
        assertThat(executionOrder).contains("OnDestroy");
        assertThat(comp.wasDestroyedAfterInit()).isTrue();
    }

    @Test
    void postConstructWithParametersIsSupported() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ComponentWithParamInit comp = context.getComponent(ComponentWithParamInit.class);

            // Assert
            assertThat(comp.getInjectedValue()).isNotNull();
        }
    }

    @Test
    void multipleComponentsLifecycleMethodsCalled() {
        // Arrange
        executionOrder.clear();

        // Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ComponentOne compOne = context.getComponent(ComponentOne.class);
            ComponentTwo compTwo = context.getComponent(ComponentTwo.class);

            // Assert
            assertThat(compOne.isInitialized()).isTrue();
            assertThat(compTwo.isInitialized()).isTrue();
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.lifecycle", createInstance = false)
    static class TestRoot {
    }

    @Component
    public static class LifecycleComponent {
        @Inject
        public SimpleDependency dependency;

        private boolean initialized = false;
        private boolean destroyed = false;
        private boolean dependencyInjectedBeforeInit = false;

        @PostConstruct
        public void init() {
            initialized = true;
            dependencyInjectedBeforeInit = (dependency != null);
        }

        @OnDestroy
        public void cleanup() {
            destroyed = true;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        public boolean isDependencyInjectedBeforeInit() {
            return dependencyInjectedBeforeInit;
        }
    }

    @Component
    public static class OrderedComponent {
        @Inject
        public SimpleDependency dependency;

        private boolean initialized = false;
        private boolean destroyed = false;

        @PostConstruct
        public void init() {
            initialized = true;
            executionOrder.add("PostConstruct");
        }

        @OnDestroy
        public void cleanup() {
            destroyed = true;
            executionOrder.add("OnDestroy");
        }

        public boolean wasDestroyedAfterInit() {
            return initialized && destroyed;
        }
    }

    @Component
    public static class ComponentWithParamInit {
        private Object injectedValue;

        @PostConstruct
        public void init(SimpleDependency dep) {
            this.injectedValue = dep;
        }

        public Object getInjectedValue() {
            return injectedValue;
        }
    }

    @Component
    public static class ComponentOne {
        private boolean initialized = false;

        @PostConstruct
        public void init() {
            initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    @Component
    public static class ComponentTwo {
        private boolean initialized = false;

        @PostConstruct
        public void init() {
            initialized = true;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    @Component
    public static class SimpleDependency {
    }
}
