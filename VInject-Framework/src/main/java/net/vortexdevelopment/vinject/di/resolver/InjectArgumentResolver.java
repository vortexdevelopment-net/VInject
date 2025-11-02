package net.vortexdevelopment.vinject.di.resolver;

import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Service;

/**
 * Built-in resolver for @Inject annotation and default dependency injection.
 * Handles dependency lookup from the container.
 */
@ArgumentResolver(value = Inject.class, priority = 50)
public class InjectArgumentResolver implements ArgumentResolverProcessor {
    
    @Override
    public boolean canResolve(ArgumentResolverContext context) {
        // Check if @Inject annotation is present, or if it's a known component type
        if (context.hasAnnotation(Inject.class)) {
            return true;
        }
        
        // Also handle default dependency injection for component types
        Class<?> targetType = context.getTargetType();
        return targetType.isAnnotationPresent(Component.class) 
                || targetType.isAnnotationPresent(Service.class)
                || targetType.isAnnotationPresent(Repository.class)
                || context.getContainer().getDependencyOrNull(targetType) != null;
    }
    
    @Override
    public Object resolve(ArgumentResolverContext context) {
        Class<?> targetType = context.getTargetType();
        
        // Check if dependency exists in container
        Object dependency = context.getContainer().getDependencyOrNull(targetType);
        
        if (dependency == null) {
            // Check if it's optional
            if (context.hasAnnotation(OptionalDependency.class)) {
                return null;
            }
            
            // Don't throw here - let the calling code handle the error
            // This allows fallback to existing error handling logic
            return null;
        }
        
        return dependency;
    }
    
}
