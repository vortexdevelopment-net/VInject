package net.vortexdevelopment.vinject.database.repository;

import lombok.Getter;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.formatter.SchemaFormatter;
import net.vortexdevelopment.vinject.di.DependencyContainer;

/**
 * Context for repository method handlers, containing all necessary dependencies.
 */
@Getter
public class RepositoryInvocationContext<T, ID> {

    private final Class<?> repositoryClass;
    private final Class<T> entityClass;
    private final EntityMetadata entityMetadata;
    private final Database database;
    private final DependencyContainer dependencyContainer;
    private final SchemaFormatter schemaFormatter;

    public RepositoryInvocationContext(Class<?> repositoryClass,
                                     Class<T> entityClass,
                                     EntityMetadata entityMetadata,
                                     Database database,
                                     DependencyContainer dependencyContainer) {
        this.repositoryClass = repositoryClass;
        this.entityClass = entityClass;
        this.entityMetadata = entityMetadata;
        this.database = database;
        this.dependencyContainer = dependencyContainer;
        this.schemaFormatter = database.getSchemaFormatter();
    }
}
