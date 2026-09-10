package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.database.cache.Cache;
import net.vortexdevelopment.vinject.database.repository.handler.CrudMethodHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Default repository-bound cache view used by repository proxies.
 */
public final class RepositoryCacheImpl<T, ID> implements RepositoryCache<T, ID> {

    private final RepositoryInvocationContext<T, ID> context;
    private final CrudMethodHandler crudHandler;

    public RepositoryCacheImpl(RepositoryInvocationContext<T, ID> context,
                               CrudMethodHandler crudHandler) {
        this.context = context;
        this.crudHandler = crudHandler;
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public Optional<T> getIfPresent(@NotNull ID id) {
        Cache<Object, Object> cache = requireCache();
        return Optional.ofNullable((T) cache.get(id));
    }

    @Override
    public void pin(@NotNull T entity, String reason) {
        Object id = primaryKey(entity);
        Cache<Object, Object> cache = requireCache();

        if (!cache.getAllEntries().containsKey(id)) {
            cache.put(id, entity);
        }
        if (!cache.pin(id, reason)) {
            throw new IllegalStateException("Unable to pin cache entry: " + id);
        }
    }

    @Override
    public void unpin(@NotNull T entity, String reason) {
        Object id = primaryKey(entity);
        if (!requireCache().unpin(id, reason)) {
            throw new IllegalStateException("Unable to unpin missing cache entry: " + id);
        }
    }

    @Override
    public int pinCount(@NotNull T entity) {
        return requireCache().getPinCount(primaryKey(entity));
    }

    @Override
    public boolean flush(@NotNull ID id) {
        return crudHandler.flush(context, id);
    }

    private Object primaryKey(T entity) {
        Object id = context.getEntityMetadata().getPrimaryKeyFieldContent(entity);
        if (id == null) {
            throw new IllegalArgumentException("Cannot pin an entity without a primary key");
        }
        return id;
    }

    private Cache<Object, Object> requireCache() {
        Cache<Object, Object> cache = crudHandler.getCache(context);
        if (cache == null) {
            throw new IllegalStateException(
                    "Caching is not enabled for " + context.getRepositoryClass().getName());
        }
        return cache;
    }
}
