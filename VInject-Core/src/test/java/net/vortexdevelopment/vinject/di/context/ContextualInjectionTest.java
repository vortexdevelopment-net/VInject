package net.vortexdevelopment.vinject.di.context;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ContextualInjectionTest {

    @Root
    public static class TestApp {}

    public static class RequestData {
        private final String id;

        public RequestData(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @Component
    public static class RouteHandler {
        public String handleRequest(@Inject RequestData req) {
            return "Handled: " + req.getId();
        }
    }

    @Test
    public void testMethodParameterContextualInjection() throws Exception {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestApp.class)
                .build()) {

            DependencyContainer container = context.getContainer();
            RouteHandler handler = container.getDependencyOrNull(RouteHandler.class);
            assertNotNull(handler);

            Method handleMethod = RouteHandler.class.getDeclaredMethod("handleRequest", RequestData.class);

            // Context 1
            Map<Class<?>, Object> contextBeans1 = new HashMap<>();
            contextBeans1.put(RequestData.class, new RequestData("req-001"));

            String result1 = InjectionContext.runWithContext(contextBeans1, () -> {
                Object[] args = container.resolveMethodArgumentValues(RouteHandler.class, handleMethod, handler);
                return (String) handleMethod.invoke(handler, args);
            });

            assertEquals("Handled: req-001", result1);

            // Context 2 (Thread isolated, but done sequentially here to test re-binding)
            Map<Class<?>, Object> contextBeans2 = new HashMap<>();
            contextBeans2.put(RequestData.class, new RequestData("req-002"));

            String result2 = InjectionContext.runWithContext(contextBeans2, () -> {
                Object[] args = container.resolveMethodArgumentValues(RouteHandler.class, handleMethod, handler);
                return (String) handleMethod.invoke(handler, args);
            });

            assertEquals("Handled: req-002", result2);
            
            // Verifying isolation outside context
            assertNull(InjectionContext.get(RequestData.class));
        }
    }
}
