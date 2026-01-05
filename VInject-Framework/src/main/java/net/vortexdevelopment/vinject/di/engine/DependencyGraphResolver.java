package net.vortexdevelopment.vinject.di.engine;

import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.di.utils.DependencyUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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

            // Check constructor parameters
            if (!DependencyUtils.hasDefaultConstructor(component)) {
                // Only one constructor is supported in this check (usually the first one)
                Class<?>[] parameterTypes = component.getDeclaredConstructors()[0].getParameterTypes();
                for (Class<?> parameter : parameterTypes) {
                    
                    // Skip if it is already in the container (e.g. pre-registered beans)
                    if (container.getDependencies().containsKey(parameter)) {
                        continue;
                    }

                    // Check if parameter is a component, service, or root class
                    if (parameter.isAnnotationPresent(Component.class)
                            || parameter.isAnnotationPresent(Service.class)
                            || parameter.equals(container.getRootClass())
                            || parameter.isAnnotationPresent(Repository.class)) {
                        
                        if (components.contains(parameter)) {
                            dependencies.add(parameter);
                        } else {
                            Class<?> providingClass = getProvidingClass(components, parameter);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                            }
                        }
                    } else {
                        // If it doesn't have annotations, maybe something else provides it
                        Class<?> providingClass = getProvidingClass(components, parameter);
                        if (providingClass != null) {
                            dependencies.add(providingClass);
                        }
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
                            Class<?> providingClass = getProvidingClass(components, fieldType);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                                continue;
                            }
                        }
                        
                        if (components.contains(fieldType)) {
                            dependencies.add(fieldType);
                        } else {
                            Class<?> providingClass = getProvidingClass(components, fieldType);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                            }
                        }
                    }
                }
            }

            dependencyGraph.put(component, dependencies);
        }

        // Perform topological sort
        return performTopologicalSort(dependencyGraph);
    }

    private Class<?> getProvidingClass(Set<Class<?>> components, Class<?> searchedClass) {
        for (Class<?> clazz : components) {
            Component component = clazz.getAnnotation(Component.class);
            if (component != null) {
                for (Class<?> providingClass : component.registerSubclasses()) {
                    if (providingClass.equals(searchedClass)) {
                        return clazz;
                    }
                }
            }
            Bean bean = clazz.getAnnotation(Bean.class);
            if (bean != null) {
                for (Class<?> providingClass : bean.registerSubclasses()) {
                    if (providingClass.equals(searchedClass)) {
                        return clazz;
                    }
                }
            }
        }
        return null;
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
