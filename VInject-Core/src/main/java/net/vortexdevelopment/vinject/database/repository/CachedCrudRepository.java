package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.annotation.database.EnableCaching;
import org.jetbrains.annotations.NotNull;

/**
 * CRUD repository with a VInject-managed entity cache.
 *
 * <p>The cache is keyed by the entity primary key. Calling {@link #pin(Object,
 * String)} adds one reference to the entry; the entry becomes eligible for the
 * configured eviction policy only after all matching {@link #unpin(Object,
 * String)} calls have been made.</p>
 *
 * @param <T> entity type
 * @param <ID> primary-key type
 */
@EnableCaching
public interface CachedCrudRepository<T, ID> extends CrudRepository<T, ID> {

    /**
     * Returns the repository-bound cache view.
     *
     * @return cache view managed by VInject
     */
    @NotNull
    RepositoryCache<T, ID> cache();

    /**
     * Adds one reference-counted pin to an entity.
     *
     * @param entity cached entity
     * @param reason optional diagnostic reason
     */
    default void pin(@NotNull T entity, String reason) {
        cache().pin(entity, reason);
    }

    /**
     * Adds one reference-counted pin without a diagnostic reason.
     */
    default void pin(@NotNull T entity) {
        pin(entity, null);
    }

    /**
     * Releases one reference-counted pin from an entity.
     *
     * @param entity cached entity
     * @param reason optional diagnostic reason
     */
    default void unpin(@NotNull T entity, String reason) {
        cache().unpin(entity, reason);
    }

    /**
     * Releases one reference-counted pin without a diagnostic reason.
     */
    default void unpin(@NotNull T entity) {
        unpin(entity, null);
    }
}
