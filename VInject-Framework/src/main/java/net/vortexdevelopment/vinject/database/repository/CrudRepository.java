package net.vortexdevelopment.vinject.database.repository;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;


/**
 * Generic CRUD repository interface for basic CRUD operations.
 *
 * @param <T> the entity type
 * @param <ID> the type of the entity's identifier
 */
public interface CrudRepository<T, ID> {

    /**
     * Can be null if no result found or unable to parse the return type.
     * Supported types are:
     * - Single value (e.g. String, Integer, Long, etc. one value per row)
     * - List of values (e.g. List<String>, List<Integer>, etc. one value per row)
     * - Map of values for multiple columns in a single row (e.g. Map<String, Object>)
     *
     * @param query The query to execute
     * @param returnType The type of the result
     * @param args The arguments to bind to the query
     * @return The result of the query, or null if no result found or unable to parse the return type
     */
    @Nullable <R> R query(String query, Class<R> returnType, Object... args);

    /**
     * Saves a given entity and returns the saved entity with an updated primary key if applicable.
     *
     * @param entity the entity to save
     * @return the saved entity
     */
    <S extends T> S save(S entity);

    /**
     * Saves all given entities and returns the saved entities with updated primary keys if applicable.
     *
     * @param entities the entities to save
     * @return the saved entities
     */
    @NotNull
    <S extends T> Iterable<S> saveAll(Iterable<S> entities);

    /**
     * Finds an entity by its ID.
     *
     * @param id the ID of the entity
     * @return the found entity or null if not found
     */
    @Nullable
    T findById(ID id);

    /**
     * Checks if an entity exists by its ID.
     *
     * @param id the ID of the entity
     * @return true if the entity exists, false otherwise
     */
    boolean existsById(ID id);

    /**
     * Finds all entities.
     *
     * @return all entities
     */
    @NotNull
    Iterable<T> findAll();

    /**
     * Finds all entities by their IDs.
     *
     * @param ids the IDs of the entities
     * @return the found entities
     */
    @NotNull
    Iterable<T> findAllById(Iterable<ID> ids);

    /**
     * Counts the number of entities.
     *
     * @return the number of entities
     */
    long count();

    /**
     * Deletes an entity by its ID.
     *
     * @param id the ID of the entity
     * @return number of rows affected
     */
    int deleteById(ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity the entity to delete
     * @return number of rows affected
     */
    int delete(T entity);

    /**
     * Deletes all entities by their IDs.
     *
     * @param ids the IDs of the entities
     * @return number of rows affected
     */
    int deleteAllById(Iterable<? extends ID> ids);

    /**
     * Deletes all given entities.
     *
     * @param entities the entities to delete
     * @return number of rows affected
     */
    int deleteAll(Iterable<? extends T> entities);

    /**
     * Deletes all entities.
     *
     * @return number of rows affected
     */
    int deleteAll();
}
