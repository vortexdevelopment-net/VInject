package net.vortexdevelopment.vinject.di.resolver;

import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Service;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Built-in resolver for @Inject annotation and default dependency injection.
 * Handles dependency lookup from the container with comprehensive error handling.
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
            
            // Handle error cases with proper error messages
            if (context.isField()) {
                // Field injection error handling
                Field field = context.getField();
                Class<?> declaringClass = context.getDeclaringClass();
                
                // Check if the declaring class is a @Service with special restrictions
                if (declaringClass.isAnnotationPresent(Service.class)) {
                    throw new RuntimeException("Only @Root class can be injected to @Service classes! Class: " + declaringClass.getName());
                }
                
                // Check if dependency was skipped due to DependsOn
                if (context.getContainer().isSkippedDueToDependsOn(targetType)) {
                    List<String> missing = context.getContainer().getMissingDependencies(targetType);
                    throw new RuntimeException("Dependency not loaded for field: " + targetType.getName() + " " + field.getName() + 
                            " in class: " + declaringClass.getName() + ". Missing runtime dependencies: " + 
                            String.join(", ", missing) + ". Consider annotating the field with @OptionalDependency to inject null.");
                }
                
                // Determine error message based on context
                String errorContext = field.getModifiers() != 0 && java.lang.reflect.Modifier.isStatic(field.getModifiers()) 
                        ? "static dependencies" 
                        : "dependencies";
                throw new RuntimeException("Dependency not found for field: " + targetType + " " + field.getName() + 
                        " in class: " + declaringClass.getName() + " while injecting " + errorContext + ". Forget to add Bean?");
            } else if (context.isParameter()) {
                // Parameter injection error handling
                Class<?> declaringClass = context.getDeclaringClass();
                String parameterContext;
                
                if (context.getConstructor() != null) {
                    parameterContext = "constructor parameter";
                } else if (context.getMethod() != null) {
                    String methodName = context.getMethod().getName();
                    parameterContext = "@PostConstruct method parameter in method: " + methodName;
                } else {
                    parameterContext = "method parameter";
                }
                
                // Check if dependency was skipped due to DependsOn
                if (context.getContainer().isSkippedDueToDependsOn(targetType)) {
                    List<String> missing = context.getContainer().getMissingDependencies(targetType);
                    throw new RuntimeException("Dependency not loaded for " + parameterContext + ": " + targetType.getName() + 
                            " in class: " + declaringClass.getName() + ". Missing runtime dependencies: " + 
                            String.join(", ", missing) + ". Consider annotating the parameter with @OptionalDependency to inject null.");
                }
                
                // Standard error message
                if (context.getMethod() != null && context.getMethod().isAnnotationPresent(net.vortexdevelopment.vinject.annotation.PostConstruct.class)) {
                    throw new RuntimeException("Dependency not found for @PostConstruct method parameter: " + 
                            targetType.getName() + " in method: " + context.getMethod().getName() + 
                            " of class: " + declaringClass.getName());
                } else if (context.getConstructor() != null) {
                    throw new RuntimeException("Dependency not found for constructor parameter: " + targetType.getName() + 
                            " in class: " + declaringClass.getName() + ". Forget to add Bean?");
                } else {
                    throw new RuntimeException("Dependency not found for @Bean method parameter: " + targetType.getName() + 
                            " in method: " + (context.getMethod() != null ? context.getMethod().getName() : "unknown") + 
                            " of class: " + declaringClass.getName());
                }
            } else {
                // Fallback error
                throw new RuntimeException("Dependency not found: " + targetType.getName());
            }
        }
        
        return dependency;
    }
    
}
