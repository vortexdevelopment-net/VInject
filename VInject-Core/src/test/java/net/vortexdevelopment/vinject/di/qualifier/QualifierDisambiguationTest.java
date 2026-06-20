package net.vortexdevelopment.vinject.di.qualifier;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Qualifier;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualifierDisambiguationTest {

    @Test
    void injectsNamedBeanWhenMultipleImplementationsExist() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            NamedConsumer consumer = context.getComponent(NamedConsumer.class);
            assertThat(consumer.port).isNotNull();
            assertThat(consumer.port).isInstanceOf(NamedPortB.class);
        }
    }

    @Root(packageName = "net.vortexdevelopment.vinject.di.qualifier", createInstance = false)
    static class TestRoot {
    }

    interface Port {
    }

    @Component(name = "portA")
    static class NamedPortA implements Port {
    }

    @Qualifier("portB")
    @Component
    static class NamedPortB implements Port {
    }

    @Component
    static class NamedConsumer {
        @Inject
        @Qualifier("portB")
        Port port;
    }
}
