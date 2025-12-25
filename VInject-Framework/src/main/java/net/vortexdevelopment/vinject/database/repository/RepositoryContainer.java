package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container for managing repository proxies.
 */
public class RepositoryContainer {

    private final Map<Class<?>, RepositoryInvocationHandler<?, ?>> repositoryProxies;
    private final Database database;

    public RepositoryContainer(Database database) {
        this.repositoryProxies = new ConcurrentHashMap<>();
        this.database = database;
    }

    @SuppressWarnings("rawtypes")
    public RepositoryInvocationHandler registerRepository(Class<?> repositoryClass, Class<?> entityClass, DependencyContainer container) {
        RepositoryInvocationHandler proxy = new RepositoryInvocationHandler<>(repositoryClass, entityClass, database, container);
        repositoryProxies.put(entityClass, proxy);
        return proxy;
    }
}
