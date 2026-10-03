package net.vortexdevelopment.vinject.di.resolver;

/**
 * Interface for resolving argument values during dependency injection.
 * Resolvers can handle custom annotations for both field and parameter injection.
 * 
 * <p>The supported annotations are specified via the {@link net.vortexdevelopment.vinject.annotation.ArgumentResolver}
 * annotation on the resolver class.
 */
public interface ArgumentResolverProcessor {
    
    /**
     * Check if this resolver can handle the given context.
     * 
     * @param context The injection context containing all relevant information
     * @return true if this resolver can handle the context, false otherwise
     */
    boolean canResolve(ArgumentResolverContext context);
    
    /**
     * Resolve the argument value for the given context.
     * 
     * @param context The injection context containing all relevant information
     * @return The resolved value, or null if cannot resolve (will fall back to other resolvers)
     */
    Object resolve(ArgumentResolverContext context);
}

