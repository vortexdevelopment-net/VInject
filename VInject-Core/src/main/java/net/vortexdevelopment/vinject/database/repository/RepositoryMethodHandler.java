package net.vortexdevelopment.vinject.database.repository;

import java.lang.reflect.Method;

/**
 * Interface for handling repository method calls.
 */
public interface RepositoryMethodHandler {

    /**
     * Checks if this handler can manage the given method.
     */
    boolean canHandle(Method method);

    /**
     * Handles the method invocation.
     */
    Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable;
}
