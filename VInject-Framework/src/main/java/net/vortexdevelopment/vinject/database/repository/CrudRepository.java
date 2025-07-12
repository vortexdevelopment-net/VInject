package net.vortexdevelopment.vinject.database.repository;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Generic CRUD repository interface for basic CRUD operations.
 *
 * @param <T> the entity type
 * @param <ID> the type of the entity's identifier
 */
public interface CrudRepository<T, ID> {

    <S extends T> S save(S entity);

    <S extends T> Iterable<S> saveAll(Iterable<S> entities);

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

    T findById(ID id);

    boolean existsById(ID id);

    Iterable<T> findAll();

    Iterable<T> findAllById(Iterable<ID> ids);

    long count();

    void deleteById(ID id);

    void delete(T entity);

    void deleteAllById(Iterable<? extends ID> ids);

    void deleteAll(Iterable<? extends T> entities);

    void deleteAll();
}
