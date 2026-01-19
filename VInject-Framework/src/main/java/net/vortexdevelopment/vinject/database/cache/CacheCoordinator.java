package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central coordinator for proactive cache management.
 * Manages cache contributors and triggers data loading/unloading.
 */
@Service
public class CacheCoordinator {

    @Inject
    private RepositoryContainer repositoryContainer;

    private final Map<String, List<CacheContributor<?>>> contributors = new HashMap<>();

    /**
     * Register a contributor for a specific namespace.
     * @param contributor the contributor to register
     */
    public void registerContributor(CacheContributor<?> contributor) {
        contributors.computeIfAbsent(contributor.getNamespace(), k -> new ArrayList<>()).add(contributor);
        DebugLogger.log("Registered cache contributor for namespace: %s", contributor.getNamespace());
    }

    /**
     * Load data for a cache namespace, triggering automatic and manual loading.
     * 
     * @param namespace the namespace (e.g. DefaultCacheKeys.PLAYER_UUID)
     * @param value the value (e.g. a UUID)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void load(String namespace, Object value) {
        DebugLogger.log("Triggering cache loading for %s = %s", namespace, value);
        
        // 1. Automatic loading via @AutoLoad in repositories
        repositoryContainer.loadByNamespace(namespace, value);

        // 2. Manual contributors
        List<CacheContributor<?>> list = contributors.get(namespace);
        if (list != null) {
            for (CacheContributor contributor : list) {
                contributor.contribute(value, (entity) -> {
                    if (entity == null) return;
                    repositoryContainer.injectIntoCache(entity);
                });
            }
        }
    }

    /**
     * Unload data for a cache namespace, triggering automatic and manual unloading.
     * 
     * @param namespace the namespace
     * @param value the value
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void unload(String namespace, Object value) {
        DebugLogger.log("Triggering cache unloading for %s = %s", namespace, value);

        // 1. Automatic invalidation in repositories
        repositoryContainer.invalidateByNamespace(namespace, value);

        // 2. Manual contributors
        List<CacheContributor<?>> list = contributors.get(namespace);
        if (list != null) {
            for (CacheContributor contributor : list) {
                contributor.invalidate(value, (entity) -> {
                    if (entity == null) return;
                    repositoryContainer.removeFromCache(entity);
                });
            }
        }
    }
}
