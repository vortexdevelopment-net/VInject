package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryMethodHandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Handles default methods defined in repository interfaces.
 */
public class DefaultMethodHandler implements RepositoryMethodHandler {

    @Override
    public boolean canHandle(Method method) {
        return method.isDefault();
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        MethodHandle handle = lookup.findSpecial(
                declaringClass,
                method.getName(),
                MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                declaringClass);
        return handle.bindTo(proxy).invokeWithArguments(args == null ? new Object[0] : args);
    }
}
