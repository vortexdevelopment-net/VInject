package net.vortexdevelopment.vinject.database.repository;

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
