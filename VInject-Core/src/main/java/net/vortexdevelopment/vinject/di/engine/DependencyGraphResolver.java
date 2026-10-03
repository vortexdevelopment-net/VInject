package net.vortexdevelopment.vinject.di.engine;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.di.utils.DependencyUtils;
import net.vortexdevelopment.vinject.di.utils.BeanNamingUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles dependency resolution and topological sorting for component loading order.
 */
public class DependencyGraphResolver {

    private final DependencyContainer container;

    public DependencyGraphResolver(DependencyContainer container) {
        this.container = container;
    }

    /**
     * Creates a loading order for components based on their dependencies.
     */
    public LinkedList<Class<?>> createLoadingOrder(Set<Class<?>> components) {
        // Build the dependency graph
        Map<Class<?>, Set<Class<?>>> dependencyGraph = new HashMap<>();
        for (Class<?> component : components) {
            Set<Class<?>> dependencies = new HashSet<>();

            // Skip interfaces, enums, etc. that cannot be instantiated
            if (component.isInterface() || component.isEnum() || component.isAnnotation()) {
                dependencyGraph.put(component, Collections.emptySet());
                continue;
            }

            // Check constructor parameters
            if (!DependencyUtils.hasDefaultConstructor(component)) {
                // Only one constructor is supported in this check (usually the first one)
                var constructors = component.getDeclaredConstructors();
                if (constructors.length > 0) {
                    Class<?>[] parameterTypes = constructors[0].getParameterTypes();
                    java.lang.annotation.Annotation[][] parameterAnnotations =
                            constructors[0].getParameterAnnotations();
                    for (int i = 0; i < parameterTypes.length; i++) {
                        // Same dependency logic as constructor parameters
                        resolveParameters(components, dependencies, parameterTypes[i],
                                BeanNamingUtils.extractQualifier(parameterAnnotations[i]));
                    }
                }
            }



            // Check @Inject annotated fields
            for (Field field : component.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    Class<?> fieldType = field.getType();

                    // Skip already registered
                    if (container.getDependencies().containsKey(fieldType)) {
                        continue;
                    }

                    // Skip services and root (preloaded)
                    if (!fieldType.isAnnotationPresent(Service.class) && !fieldType.equals(container.getRootClass()) && !fieldType.isAnnotationPresent(Repository.class)) {
                        if (!fieldType.isAnnotationPresent(Component.class) || fieldType.isInterface()) {
                            String qualifier = BeanNamingUtils.extractQualifier(field.getAnnotations());
                            Class<?> providingClass = getProvidingClass(components, fieldType, qualifier);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                                continue;
                            }
                        }
                        
                        if (components.contains(fieldType)) {
                            dependencies.add(fieldType);
                        } else {
                            String qualifier = BeanNamingUtils.extractQualifier(field.getAnnotations());
                            Class<?> providingClass = getProvidingClass(components, fieldType, qualifier);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                            }
                        }
                    }
                }
            }

            // Check @PostConstruct annotated methods with parameters
            for (Method method : component.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    java.lang.annotation.Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                    for (int i = 0; i < parameterTypes.length; i++) {
                        // Same dependency logic as constructor parameters
                        resolveParameters(components, dependencies, parameterTypes[i],
                                BeanNamingUtils.extractQualifier(parameterAnnotations[i]));
                    }
                }
            }

            dependencyGraph.put(component, dependencies);
        }

        // Perform topological sort
        return performTopologicalSort(dependencyGraph);
    }

    private void resolveParameters(Set<Class<?>> components, Set<Class<?>> dependencies,
                                   Class<?> parameter, String qualifier) {
        if (container.getDependencies().containsKey(parameter)) {
            return;
        }

        if (parameter.isAnnotationPresent(Component.class)
                || parameter.isAnnotationPresent(Service.class)
                || parameter.equals(container.getRootClass())
                || parameter.isAnnotationPresent(Repository.class)) {

            if (components.contains(parameter)) {
                dependencies.add(parameter);
            } else {
                Class<?> providingClass = getProvidingClass(components, parameter, qualifier);
                if (providingClass != null) {
                    dependencies.add(providingClass);
                }
            }
        } else {
            if (components.contains(parameter)) {
                dependencies.add(parameter);
                return;
            }

            Class<?> providingClass = getProvidingClass(components, parameter, qualifier);
            if (providingClass != null) {
                dependencies.add(providingClass);
            }
        }
    }

    private Class<?> getProvidingClass(Set<Class<?>> components, Class<?> searchedClass, String qualifier) {
        List<Class<?>> matches = new ArrayList<>();
        for (Class<?> clazz : components) {
            if (!searchedClass.isAssignableFrom(clazz)) {
                continue;
            }
            if (!qualifier.isEmpty()
                    && !qualifier.equals(BeanNamingUtils.resolveComponentName(clazz))) {
                continue;
            }
            matches.add(clazz);
        }

        if (matches.isEmpty()) {
            return null;
        }

        // An exact concrete type is always preferred over broader assignable matches.
        if (matches.contains(searchedClass)) {
            return searchedClass;
        }
        if (matches.size() > 1) {
            throw new RuntimeException("Ambiguous dependency for type " + searchedClass.getName()
                    + ". Multiple components found: "
                    + matches.stream().map(Class::getName).sorted().collect(java.util.stream.Collectors.joining(", "))
                    + ". Inject the concrete type or add @Qualifier(\"name\") to select a named component.");
        }
        return matches.get(0);
    }

    private LinkedList<Class<?>> performTopologicalSort(Map<Class<?>, Set<Class<?>>> dependencyGraph) {
        LinkedList<Class<?>> sortedComponents = new LinkedList<>();
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new java.util.LinkedHashSet<>();

        for (Class<?> component : dependencyGraph.keySet()) {
            if (!visited.contains(component)) {
                visit(component, dependencyGraph, visited, visiting, sortedComponents);
            }
        }

        return sortedComponents;
    }

    private void visit(Class<?> component,
                       Map<Class<?>, Set<Class<?>>> graph,
                       Set<Class<?>> visited,
                       Set<Class<?>> visiting,
                       LinkedList<Class<?>> sortedComponents) {
        if (visiting.contains(component)) {
            // Circular dependency detected - log it and skip to resolve with deferred injection
            StringBuilder cycle = new StringBuilder();
            boolean inCycle = false;
            for (Class<?> c : visiting) {
                if (c.equals(component)) {
                    inCycle = true;
                }
                if (inCycle) {
                    cycle.append(c.getSimpleName()).append(" -> ");
                }
            }
            cycle.append(component.getSimpleName());
            System.out.println("Circular dependency detected (will be resolved with deferred injection): " + cycle.toString());
            return;
        }

        if (!visited.contains(component)) {
            visiting.add(component);
            for (Class<?> dependency : graph.getOrDefault(component, Collections.emptySet())) {
                visit(dependency, graph, visited, visiting, sortedComponents);
            }
            visiting.remove(component);
            visited.add(component);
            sortedComponents.addLast(component);
        }
    }
}
