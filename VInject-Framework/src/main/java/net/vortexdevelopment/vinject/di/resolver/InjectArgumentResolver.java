package net.vortexdevelopment.vinject.di.resolver;

import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.Conditional;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Service;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConditional;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;

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
        
        // Also handle default dependency injection for component types and YAML configs
        Class<?> targetType = context.getTargetType();
        return targetType.isAnnotationPresent(Component.class) 
                || targetType.isAnnotationPresent(Service.class)
                || targetType.isAnnotationPresent(Repository.class)
                || targetType.isAnnotationPresent(YamlConfiguration.class)
                || targetType.isAnnotationPresent(net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory.class)
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

            // Inject null for conditional dependencies if they are not met
            if (targetType.isAnnotationPresent(Conditional.class) || targetType.isAnnotationPresent(YamlConditional.class)) {
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
                
                // Try to create the dependency on-the-spot if it's a Component with default constructor
                if (targetType.isAnnotationPresent(Component.class)) {
                    try {
                        // Check if it has a default constructor
                        targetType.getDeclaredConstructor();
                        // Try to create it - this will handle circular dependencies via setter injection
                        dependency = context.getContainer().newInstance(targetType);
                        if (dependency != null) {
                            return dependency;
                        }
                    } catch (NoSuchMethodException e) {
                        // No default constructor - throw circular dependency error
                        throw new RuntimeException(
                            "Circular dependency detected for field: " + field.getName() + " of type: " + targetType.getName() + 
                            " in class: " + declaringClass.getName() +
                            "\n\nCircular dependencies require a default constructor and field/setter injection." +
                            "\n\nTo resolve this issue:" +
                            "\n1. Add a default (no-arg) constructor to " + targetType.getSimpleName() +
                            "\n2. Use @Inject on fields instead of constructor parameters" +
                            "\n3. Use @PostConstruct for initialization logic that needs injected dependencies" +
                            "\n\nExample:" +
                            "\n  @Component" +
                            "\n  public class " + targetType.getSimpleName() + " {" +
                            "\n      @Inject" +
                            "\n      private " + declaringClass.getSimpleName() + " dependency;" +
                            "\n      " +
                            "\n      @PostConstruct" +
                            "\n      public void init() {" +
                            "\n          // Use dependency here - it's guaranteed to be injected" +
                            "\n      }"+
                            "\n  }"
                        );
                    } catch (RuntimeException e) {
                        // If we get a circular dependency error from newInstance, re-throw it
                        if (e.getMessage() != null && e.getMessage().contains("Circular dependency detected")) {
                            throw e;
                        }
                        // Otherwise, it's a different error
                        throw new RuntimeException("Unable to create dependency: " + targetType.getName() + " for field: " + field.getName(), e);
                    }
                }
                
                // Not a Component or couldn't create it - throw error
                throw new RuntimeException("Dependency not found for field: " + targetType + " " + field.getName() + 
                        " in class: " + declaringClass.getName() + ". Forget to add @Component?");
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
