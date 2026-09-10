package net.vortexdevelopment.vinject.di.autointerface;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoInterfaceRegistrationTest {

    @Test
    void injectsInterfaceAutomatically() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            PortConsumer consumer = context.getComponent(PortConsumer.class);
            assertThat(consumer.port).isNotNull();
            assertThat(consumer.port).isInstanceOf(PortImpl.class);
        }
    }

    @Test
    void prefersExactClassOverInheritedSubclassRegistration() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            ExactClassConsumer consumer = context.getComponent(ExactClassConsumer.class);
            assertThat(consumer.base).isInstanceOf(BaseComponent.class);
        }
    }

    @Root(packageName = "net.vortexdevelopment.vinject.di.autointerface", createInstance = false)
    static class TestRoot {
    }

    interface Port {
    }

    @Component
    static class PortImpl implements Port {
    }

    @Component
    static class PortConsumer {
        @Inject Port port;
    }

    @Component
    static class BaseComponent {
    }

    @Component
    static class DerivedComponent extends BaseComponent {
    }

    @Component
    static class ExactClassConsumer {
        @Inject BaseComponent base;
    }
}
