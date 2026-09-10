package net.vortexdevelopment.vinject.database.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Cache operations bound to one repository.
 *
 * @param <T> entity type
 * @param <ID> primary-key type
 */
public interface RepositoryCache<T, ID> {

    /**
     * Reads only from memory and never loads from the database.
     *
     * @param id entity primary key
     * @return cached entity when present
     */
    @NotNull
    Optional<T> getIfPresent(@NotNull ID id);

    /**
     * Adds one reference-counted pin. An uncached entity is inserted first.
     *
     * @param entity entity to pin
     * @param reason optional diagnostic reason
     */
    void pin(@NotNull T entity, @Nullable String reason);

    /**
     * Releases one reference-counted pin.
     *
     * @param entity entity to unpin
     * @param reason optional diagnostic reason
     */
    void unpin(@NotNull T entity, @Nullable String reason);

    /**
     * Returns the current total pin count for an entity.
     *
     * @param entity entity whose count should be inspected
     * @return total pin count, or zero when absent
     */
    int pinCount(@NotNull T entity);

    /**
     * Flushes one dirty entity when write-back caching is enabled.
     *
     * @param id entity primary key
     * @return true when the entry was flushed or was already clean
     */
    boolean flush(@NotNull ID id);
}
