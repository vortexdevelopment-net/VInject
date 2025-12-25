package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.handler.CrudMethodHandler;
import net.vortexdevelopment.vinject.database.repository.handler.CustomQueryMethodHandler;
import net.vortexdevelopment.vinject.database.repository.handler.DefaultMethodHandler;
import net.vortexdevelopment.vinject.database.repository.handler.DynamicQueryMethodHandler;
import net.vortexdevelopment.vinject.database.repository.handler.ObjectMethodHandler;
import net.vortexdevelopment.vinject.database.repository.handler.TopQueryMethodHandler;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced InvocationHandler to implement CRUD and dynamic operations for CrudRepository interface.
 * Uses a modular handler system for method processing.
 *
 * @param <T>  Entity type
 * @param <ID> ID type
 */
public class RepositoryInvocationHandler<T, ID> implements InvocationHandler {

    private final RepositoryInvocationContext<T, ID> context;
    private final Map<String, RepositoryMethodHandler> exactHandlers = new HashMap<>();
    private final List<RepositoryMethodHandler> patternHandlers = new ArrayList<>();

    public RepositoryInvocationHandler(Class<?> repositoryClass,
                                       Class<T> entityClass,
                                       Database database,
                                       DependencyContainer dependencyContainer) {

        EntityMetadata entityMetadata = new EntityMetadata(entityClass, database.getSerializerRegistry());
        this.context = new RepositoryInvocationContext<>(repositoryClass, entityClass, entityMetadata, database, dependencyContainer);
        
        initializeHandlers();
    }

    private void initializeHandlers() {
        // CRUD Methods
        CrudMethodHandler crudHandler = new CrudMethodHandler();
        for (String methodName : CrudMethodHandler.SUPPORTED_METHODS) {
            exactHandlers.put(methodName, crudHandler);
        }

        // Custom Query Method
        exactHandlers.put("query", new CustomQueryMethodHandler());

        // Object Methods
        ObjectMethodHandler objectHandler = new ObjectMethodHandler();
        exactHandlers.put("equals", objectHandler);
        exactHandlers.put("hashCode", objectHandler);
        exactHandlers.put("toString", objectHandler);

        // Pattern-based Handlers (Dynamic queries, default methods, etc.)
        patternHandlers.add(new DynamicQueryMethodHandler());
        patternHandlers.add(new TopQueryMethodHandler());
        patternHandlers.add(new DefaultMethodHandler());
    }

    @SuppressWarnings("unchecked")
    public CrudRepository<T, ID> create() {
        return (CrudRepository<T, ID>) Proxy.newProxyInstance(
                context.getRepositoryClass().getClassLoader(),
                new Class[]{context.getRepositoryClass()},
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. Try exact match lookup (O(1))
        RepositoryMethodHandler handler = exactHandlers.get(method.getName());
        if (handler != null) {
            return handler.handle(context, proxy, method, args);
        }

        // 2. Try pattern-based matching
        for (RepositoryMethodHandler patternHandler : patternHandlers) {
            if (patternHandler.canHandle(method)) {
                return patternHandler.handle(context, proxy, method, args);
            }
        }

        throw new UnsupportedOperationException("Method not supported: " + method.getName());
    }
}