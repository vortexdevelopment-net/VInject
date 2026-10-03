package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryMethodHandler;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Handles basic Object methods like equals, hashCode, and toString.
 */
public class ObjectMethodHandler implements RepositoryMethodHandler {

    private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

    @Override
    public boolean canHandle(Method method) {
        return OBJECT_METHODS.contains(method.getName());
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        return switch (methodName) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            default -> throw new UnsupportedOperationException("Method not implemented: " + methodName);
        };
    }
}
