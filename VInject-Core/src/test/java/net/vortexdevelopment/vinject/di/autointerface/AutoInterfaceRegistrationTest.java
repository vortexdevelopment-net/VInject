package net.vortexdevelopment.vinject.di.autointerface;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoInterfaceRegistrationTest {

    @Test
    void injectsInterfaceWithoutRegisterSubclasses() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            PortConsumer consumer = context.getComponent(PortConsumer.class);
            assertThat(consumer.port).isNotNull();
            assertThat(consumer.port).isInstanceOf(PortImpl.class);
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
}
