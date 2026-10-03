package net.vortexdevelopment.vinject.di.registry;

/**
 * The order in which the registry should be registered.
 * <p>
 * Build in register order:
 * RootClass - Nothing can be registered before this
 * Services - Services should be registered before components
 * Components - Components should be registered before plugins
 * Entities - Entities should be registered last
 * Repositories - Repositories should be registered last (After database connection established)
 */
public enum RegistryOrder {

    /**
     * Registers Before Services
     */
    FIRST,
    /**
     * Registers after Services
     */
    SERVICES,

    /**
     * Registers after Entities
     */
    ENTITIES,

    /**
     * Registers after Repositories
     */
    REPOSITORIES,

    /**
     * Registers before Loading Components (But after Services and Repositories)
     */
    COMPONENTS;


}
