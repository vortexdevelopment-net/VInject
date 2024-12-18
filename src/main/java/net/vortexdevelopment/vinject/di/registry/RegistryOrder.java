package net.vortexdevelopment.vinject.di.registry;

/**
 * The order in which the registry should be registered.
 *
 * Build in register order:
 * RootClass - Nothing can be registered before this
 * Services - Services should be registered before components
 * Components - Components should be registered before plugins
 *
 */
public enum RegistryOrder {

    /**
     * Registers Be
     */
    FIRST,
}
