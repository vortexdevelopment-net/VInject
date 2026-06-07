package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentInterceptorTest {

    @Test
    void interceptorIsCalledForComponents() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(InterceptorTestRoot.class)
                .build()) {
            
            TestInterceptor interceptor = context.getComponent(TestInterceptor.class);
            assertThat(interceptor.getRegisteredClasses()).contains(InterceptedComponent.class);
        }
    }

    @Test
    void interceptorCanFilterByInterface() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(InterceptorTestRoot.class)
                .build()) {
            
            InterfaceFilterInterceptor interceptor = context.getComponent(InterfaceFilterInterceptor.class);
            assertThat(interceptor.getFilteredInstances()).hasSize(1);
            assertThat(interceptor.getFilteredInstances().get(0)).isInstanceOf(MyInterface.class);
        }
    }

    @Root(packageName = "net.vortexdevelopment.vinject.di", createInstance = false)
    static class InterceptorTestRoot {}

    @Component
    public static class TestInterceptor implements ComponentInterceptor {
        private final List<Class<?>> registeredClasses = new ArrayList<>();

        @Override
        public void onComponentRegistered(Class<?> clazz, Object instance, DependencyContainer container) {
            registeredClasses.add(clazz);
        }

        public List<Class<?>> getRegisteredClasses() {
            return registeredClasses;
        }
    }

    @Component
    public static class InterceptedComponent {}

    public interface MyInterface {}

    @Component
    public static class MyInterfaceImpl implements MyInterface {}

    @Component
    public static class InterfaceFilterInterceptor implements ComponentInterceptor {
        private final List<Object> filteredInstances = new ArrayList<>();

        @Override
        public void onComponentRegistered(Class<?> clazz, Object instance, DependencyContainer container) {
            if (instance instanceof MyInterface) {
                filteredInstances.add(instance);
            }
        }

        public List<Object> getFilteredInstances() {
            return filteredInstances;
        }
    }
}
