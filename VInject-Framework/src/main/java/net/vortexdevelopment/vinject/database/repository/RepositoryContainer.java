package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.annotation.util.Injectable;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Container for managing repository proxies.
 */
@Injectable
public class RepositoryContainer {

    private final Map<Class<?>, RepositoryInvocationHandler<?, ?>> repositoryProxies;
    private final Map<String, List<RepositoryInvocationHandler<?, ?>>> namespaceToHandlers;
    private final Database database;

    public RepositoryContainer(Database database) {
        this.repositoryProxies = new ConcurrentHashMap<>();
        this.namespaceToHandlers = new ConcurrentHashMap<>();
        this.database = database;
    }

    @SuppressWarnings("rawtypes")
    public RepositoryInvocationHandler registerRepository(Class<?> repositoryClass, Class<?> entityClass, DependencyContainer container) {
        RepositoryInvocationHandler proxy = new RepositoryInvocationHandler<>(repositoryClass, entityClass, database, container);
        repositoryProxies.put(entityClass, proxy);

        // Index namespaces
        EntityMetadata metadata = proxy.getContext().getEntityMetadata();
        for (String namespace : metadata.getAutoLoadFields().keySet()) {
            namespaceToHandlers.computeIfAbsent(namespace, k -> new CopyOnWriteArrayList<>()).add(proxy);
        }

        return proxy;
    }

    /**
     * Injects an entity into the appropriate repository's cache.
     * @param entity the entity to cache
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void injectIntoCache(Object entity) {
        RepositoryInvocationHandler handler = repositoryProxies.get(entity.getClass());
        if (handler != null) {
            handler.injectIntoCache(entity);
        }
    }

    /**
     * Removes an entity from the appropriate repository's cache.
     * @param entity the entity to remove
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void removeFromCache(Object entity) {
        RepositoryInvocationHandler handler = repositoryProxies.get(entity.getClass());
        if (handler != null) {
            handler.removeFromCache(entity);
        }
    }

    /**
     * Loads entities from all repositories that care about the given namespace.
     * @param namespace the namespace
     * @param value the value
     */
    public void loadByNamespace(String namespace, Object value) {
        List<RepositoryInvocationHandler<?, ?>> handlers = namespaceToHandlers.get(namespace);
        if (handlers != null) {
            for (RepositoryInvocationHandler<?, ?> handler : handlers) {
                handler.loadByNamespace(namespace, value);
            }
        }
    }

    /**
     * Invalidates entities from all repositories that care about the given namespace.
     * @param namespace the namespace
     * @param value the value
     */
    public void invalidateByNamespace(String namespace, Object value) {
        List<RepositoryInvocationHandler<?, ?>> handlers = namespaceToHandlers.get(namespace);
        if (handlers != null) {
            for (RepositoryInvocationHandler<?, ?> handler : handlers) {
                handler.invalidateByNamespace(namespace, value);
            }
        }
    }
}
