package net.vortexdevelopment.vinject.di.resolver;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing argument resolvers.
 * Resolvers are registered by their supported annotation type and can be retrieved
 * with priority ordering (higher priority first).
 */
public class ArgumentResolverRegistry {
    
    private final Map<Class<? extends Annotation>, ArgumentResolverProcessor> resolvers;
    private final Map<Class<? extends Annotation>, Integer> priorities;
    private final Map<ArgumentResolverProcessor, Integer> resolverPriorities;
    
    public ArgumentResolverRegistry() {
        this.resolvers = new ConcurrentHashMap<>();
        this.priorities = new ConcurrentHashMap<>();
        this.resolverPriorities = new ConcurrentHashMap<>();
    }
    
    /**
     * Register an argument resolver for a specific annotation type.
     * 
     * @param annotation The annotation type this resolver handles
     * @param resolver The resolver instance
     * @param priority The priority (higher values checked first)
     */
    public void registerResolver(Class<? extends Annotation> annotation, ArgumentResolverProcessor resolver, int priority) {
        resolvers.put(annotation, resolver);
        priorities.put(annotation, priority);
        resolverPriorities.put(resolver, priority);
    }
    
    /**
     * Register an argument resolver for a specific annotation type with default priority.
     * 
     * @param annotation The annotation type this resolver handles
     * @param resolver The resolver instance
     */
    public void registerResolver(Class<? extends Annotation> annotation, ArgumentResolverProcessor resolver) {
        registerResolver(annotation, resolver, 10);
    }
    
    /**
     * Get a resolver for a specific annotation type.
     * 
     * @param annotation The annotation type
     * @return The resolver, or null if not registered
     */
    public ArgumentResolverProcessor getResolver(Class<? extends Annotation> annotation) {
        return resolvers.get(annotation);
    }
    
    /**
     * Get all registered resolvers ordered by priority (highest first).
     * 
     * @return List of resolvers sorted by priority
     */
    public List<ArgumentResolverProcessor> getAllResolvers() {
        // Get unique resolvers
        List<ArgumentResolverProcessor> uniqueResolvers = new ArrayList<>();
        for (ArgumentResolverProcessor resolver : resolvers.values()) {
            if (!uniqueResolvers.contains(resolver)) {
                uniqueResolvers.add(resolver);
            }
        }
        
        // Sort by priority (highest first)
        uniqueResolvers.sort(Comparator.comparingInt((ArgumentResolverProcessor resolver) -> 
            resolverPriorities.getOrDefault(resolver, 10)
        ).reversed());
        
        return uniqueResolvers;
    }
    
    /**
     * Check if a resolver is registered for the given annotation type.
     * 
     * @param annotation The annotation type
     * @return true if a resolver is registered
     */
    public boolean hasResolver(Class<? extends Annotation> annotation) {
        return resolvers.containsKey(annotation);
    }
}

