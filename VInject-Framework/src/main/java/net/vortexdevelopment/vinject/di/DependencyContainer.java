package net.vortexdevelopment.vinject.di;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.DependsOn;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.OnEvent;
import net.vortexdevelopment.vinject.annotation.Registry;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Root;
import net.vortexdevelopment.vinject.annotation.Service;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationHandler;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandlerRegistry;
import net.vortexdevelopment.vinject.di.registry.RegistryOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Configuration;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import sun.misc.Unsafe;

import java.beans.EventHandler;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyContainer implements DependencyRepository {

    private final Map<Class<?>, Object> dependencies;
    private final Unsafe unsafe;
    private final Class<?> rootClass;
    private final Object rootInstance;
    private final Set<Class<?>> entities;
    private final Set<Class<?>> skippedDueToDependsOn;
    private final Map<Class<?>, List<String>> missingDependenciesByClass;
    private AnnotationHandlerRegistry annotationHandlerRegistry;
    private Consumer<Void> onPreComponentLoad;
    private Map<String, List<Method>> eventListeners;

    @Getter
    private static DependencyContainer instance;
    public DependencyContainer(Root rootAnnotation, Class<?> rootClass, @Nullable Object rootInstance, Database database, RepositoryContainer repositoryContainer, @Nullable Consumer<Void> onPreComponentLoad) {
        instance = this;
        eventListeners = new ConcurrentHashMap<>();
        dependencies = new ConcurrentHashMap<>();
        entities = ConcurrentHashMap.newKeySet();
        skippedDueToDependsOn = ConcurrentHashMap.newKeySet();
        missingDependenciesByClass = new ConcurrentHashMap<>();
        unsafe = getUnsafe();
        annotationHandlerRegistry = new AnnotationHandlerRegistry();

        if (rootInstance == null) {
            //Create the root instance if it is null
            try {
                Constructor<?> constructor = rootClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                rootInstance = constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create root instance", e);
            }
        }

        //Add plugin as bean so components can inject it
        dependencies.put(rootClass, rootInstance);
        injectRoot(rootInstance);

        this.rootClass = rootClass;
        this.rootInstance = rootInstance;

        String[] ignoredPackages = rootAnnotation.ignoredPackages();

        //included packages override ignored packages
        String[] includedPackages = rootAnnotation.includedPackages();

        Configuration configuration = new ConfigurationBuilder()
                .forPackage(rootAnnotation.packageName())
                .filterInputsBy(s -> {
                    if (s == null) return false;
                    if (s.startsWith("META-INF")) return false;

                    boolean include = true;

                    for (String ignoredPackage : ignoredPackages) {
                        if (s.startsWith(ignoredPackage)) {
                            include = false;
                            break;
                        }
                    }

                    for (String includedPackage : includedPackages) {
                        if (s.startsWith(includedPackage)) {
                            include = true;
                            break;
                        }
                    }

                    return include && s.endsWith(".class");
                });

        //Find all classes which annotated as Service
        Reflections reflections = new Reflections(configuration);

        //Get all entities first so we can initialize the database before components
        entities.addAll(reflections.getTypesAnnotatedWith(Entity.class));

        annotationHandlerRegistry.getHandlers(RegistryOrder.ENTITIES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = getAnnotationFromHandler(annotationHandler);
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                if (!canLoadClass(aClass)) {
                    return;
                }
                annotationHandler.handle(aClass,  dependencies.get(aClass), this);
            });
        });

        reflections.getTypesAnnotatedWith(Repository.class).forEach(repositoryClass -> {
            if (!canLoadClass(repositoryClass)) {
                return;
            }
            // Check if the class implements CrudRepository
            if (!ReflectionUtils.getAllSuperTypes(repositoryClass).contains(CrudRepository.class)) {
                throw new RuntimeException("Class: " + repositoryClass.getName()
                        + " annotated with @Repository does not implement CrudRepository");
            }

            // Find the generic type argument of CrudRepository
            Class<?> entityClass = getGenericTypeFromCrudRepository(repositoryClass);
            if (entityClass == null) {
                throw new RuntimeException("Unable to determine generic type for CrudRepository in class: " + repositoryClass.getName());
            }

            registerEventListeners(repositoryClass);

            // Register the repository and its entity type
            RepositoryInvocationHandler<?, ?> proxy = repositoryContainer.registerRepository(repositoryClass, entityClass, this);
            this.dependencies.put(repositoryClass, proxy.create());
        });
        //Run database initialization (Create, Update tables)
        database.initializeEntityMetadata(this);

        if (onPreComponentLoad != null) {
            onPreComponentLoad.accept(null);
        }

        //Collect all Registry annotations
        reflections.getTypesAnnotatedWith(Registry.class).forEach(aClass -> {
            //Check if extends AnnotationHandler class
            if (aClass.getSuperclass().equals(AnnotationHandler.class)) {
                AnnotationHandler instance = (AnnotationHandler) newInstance(aClass);
                Class<? extends Annotation> annotation = getAnnotationFromHandler(instance);
                if (annotation == null) {
                    throw new RuntimeException("Annotation not found for class: " + aClass + ". Make sure to return a valid annotation in getAnnotation method");
                }

                registerEventListeners(aClass);

                annotationHandlerRegistry.registerHandler(annotation, instance);
            } else {
                throw new RuntimeException("Class: " + aClass.getName() + " annotated with @Registry does not extend AnnotationHandler");
            }
        });

        annotationHandlerRegistry.getHandlers(RegistryOrder.FIRST).forEach(annotationHandler -> {
            Class<? extends Annotation> find = getAnnotationFromHandler(annotationHandler);
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                if (!canLoadClass(aClass)) {
                    return;
                }
                annotationHandler.handle(aClass,  dependencies.get(aClass), this);
            });
        });

        //Register Beans and Services
        reflections.getTypesAnnotatedWith(Service.class).forEach(serviceClass -> {
            if (!canLoadClass(serviceClass)) {
                return;
            }
            registerEventListeners(serviceClass);
            registerBeans(serviceClass);
        });

        annotationHandlerRegistry.getHandlers(RegistryOrder.SERVICES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = getAnnotationFromHandler(annotationHandler);
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                if (!canLoadClass(aClass)) {
                    return;
                }
                annotationHandler.handle(aClass,  dependencies.get(aClass), this);
            });
        });

        annotationHandlerRegistry.getHandlers(RegistryOrder.REPOSITORIES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = getAnnotationFromHandler(annotationHandler);
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                if (!canLoadClass(aClass)) {
                    return;
                }
                annotationHandler.handle(aClass,  dependencies.get(aClass), this);
            });
        });

        //Register Components
        //Need to create a loading order to avoid circular dependencies and inject issues
        Set<Class<?>> allComponents = reflections.getTypesAnnotatedWith(Component.class);
        Set<Class<?>> loadableComponents = allComponents.stream().filter(this::canLoadClass).collect(Collectors.toSet());
        createLoadingOrder(loadableComponents).stream().sorted(Comparator.comparingInt(value -> {
            Component component = value.getAnnotation(Component.class);
            if (component != null) {
                return component.priority();
            }
            return 10;
        })).forEach(componentClass -> {
            registerEventListeners(componentClass);
            registerComponent(componentClass);
        });

        annotationHandlerRegistry.getHandlers(RegistryOrder.COMPONENTS).forEach(annotationHandler -> {
            Class<? extends Annotation> find = getAnnotationFromHandler(annotationHandler);
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                if (!canLoadClass(aClass)) {
                    return;
                }
                annotationHandler.handle(aClass,  dependencies.get(aClass), this);
            });
        });
    }

    @Override
    public void emitEvent(@NotNull String eventName) {
        List<Method> listeners = eventListeners.get(eventName);
        if (listeners == null || listeners.isEmpty()) {
            return; // No listeners for this event
        }
        for (Method listenerMethod : listeners) {
            try {
                Object instance = dependencies.get(listenerMethod.getDeclaringClass());
                listenerMethod.setAccessible(true);

                //If it has no parameters, invoke it directly
                if (listenerMethod.getParameterCount() == 0) {
                    listenerMethod.invoke(instance);
                    continue;
                }

                //else check if we have all the types in the dependency container to invoke it, event listener methods only should have dependencies that are already registered
                Class<?>[] parameterTypes = listenerMethod.getParameterTypes();
                Object[] parameters = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Object dependency = dependencies.get(parameterTypes[i]);
                    if (dependency == null) {
                        System.err.println("Dependency not found for event listener method: " + listenerMethod.getName() + " with parameter type: " + parameterTypes[i].getName());
                        continue;
                    }
                    parameters[i] = dependency;
                }
                listenerMethod.invoke(instance, parameters);
            } catch (Exception e) {
                System.err.println("Error invoking event listener: " + listenerMethod.getName() + " for event: " + eventName);
                e.printStackTrace();
            }
        }
    }

    private void registerEventListeners(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnEvent.class)) {
                //We either has a String value or a string array value with the event names
                OnEvent onEvent = method.getAnnotation(OnEvent.class);
                String[] values = onEvent.value();

                if (values != null) {
                    //Register the method for each event in the array
                    for (String event : values) {
                        if (event.isEmpty()) {
                            System.err.println("Event name is empty in method: " + method.getName() + " in class: " + clazz.getName() + ". Skipping registration.");
                            continue;
                        }
                        eventListeners.computeIfAbsent(event, k -> new ArrayList<>()).add(method);
                    }
                }
            }
        }
    }

    //Clear and set to null everything
    public void release() {
        dependencies.clear();
        entities.clear();
        annotationHandlerRegistry = null;
    }

    private Class<? extends Annotation> getAnnotationFromHandler(AnnotationHandler handler) {
        //get the @Registry annotation from the handler and get the annotation
        Registry annotation = handler.getClass().getAnnotation(Registry.class);
        return annotation.annotation();
    }

    private void injectRoot(Object rootInstance) {
        //Find field with the same class as the root class and inject it
        for (Field field : rootInstance.getClass().getDeclaredFields()) {
            if (field.getType().equals(rootClass) && field.isAnnotationPresent(Inject.class)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    unsafe.putObject(rootInstance, unsafe.staticFieldOffset(field), rootInstance);
                } else {
                    unsafe.putObject(rootInstance, unsafe.objectFieldOffset(field), rootInstance);
                }
            }
        }
    }

    public Object mapEntity(Object entityInstance, Map<String, Object> resultSet) {
        try {
            for (Field field : entityInstance.getClass().getDeclaredFields()) {
                unsafe.putObject(entityInstance, unsafe.objectFieldOffset(field), resultSet.get(field.getName()));
            }
            return entityInstance;
        } catch (Exception e) {
            throw new RuntimeException("Unable to map entity", e);
        }
    }

    private Class<?> getGenericTypeFromCrudRepository(Class<?> repositoryClass) {
        // Iterate over all implemented interfaces
        for (Type genericInterface : repositoryClass.getGenericInterfaces()) {
            // Check if the interface is parameterized and extends CrudRepository
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass && CrudRepository.class.isAssignableFrom(rawClass)) {
                    // Extract the first generic type argument (e.g., User)
                    Type entityType = parameterizedType.getActualTypeArguments()[0];
                    if (entityType instanceof Class<?> entityClass) {
                        return entityClass; // Return the entity class (e.g., User)
                    }
                }
            }
        }
        // Recurse up the class hierarchy if no match is found
        if (repositoryClass.getSuperclass() != null) {
            return getGenericTypeFromCrudRepository(repositoryClass.getSuperclass());
        }
        return null; // Return null if no generic type is found
    }

    private LinkedList<Class<?>> createLoadingOrder(Set<Class<?>> components) {
        // Build the dependency graph
        Map<Class<?>, Set<Class<?>>> dependencyGraph = new HashMap<>();
        List<Class<?>> provided = new ArrayList<>();
        for (Class<?> component : components) {
            Set<Class<?>> dependencies = new HashSet<>();

            // Check constructor parameters
            if (!hasDefaultConstructor(component)) {
                //Need to add parameter types to the dependencies, only one constructor is supported
                Class<?>[] parameterTypes = component.getDeclaredConstructors()[0].getParameterTypes();
                for (Class<?> parameter : parameterTypes) {

                    //Skip if it is provided by a Bean
                    if (this.dependencies.containsKey(parameter)) {
                        continue;
                    }

                    //Check if a parameter is a component, service or root class
                    if (parameter.isAnnotationPresent(Component.class)
                            || parameter.isAnnotationPresent(Service.class)
                            || parameter.equals(this.rootClass)
                            || parameter.isAnnotationPresent(Repository.class)
                    ) {
                        if (components.contains(parameter)) {
                            dependencies.add(parameter);
                        } else {
                            Class<?> providingClass = getProvidingClass(components, parameter);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                                provided.add(providingClass);
                            }
                        }
                    } else {
                        //if the parameter type does not have a component annotation in its class declaration, something is providing it, skip it
                        Class<?> providingClass = getProvidingClass(components, parameter);
                        if (providingClass != null) {
                            dependencies.add(providingClass);
                            provided.add(providingClass);
                            continue;
                        } else {
                            // leave unresolved to be handled at injection time
                        }
                    }
                }
            }

            // Check @Inject annotated fields
            for (Field field : component.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    Class<?> fieldType = field.getType();

                    //Skip Bean provided classes
                    if (this.dependencies.containsKey(fieldType)) {
                        continue;
                    }

                    // Skip fields annotated as Services since they are preloaded
                    // Skip plugin main class, it will always present in the container when component injection is performed
                    if (!fieldType.isAnnotationPresent(Service.class) && !fieldType.equals(this.rootClass) && !fieldType.isAnnotationPresent(Repository.class)) {
                        //if the field type does not have a component annotation in its class declaration, something is providing it, skip it
                        if (!fieldType.isAnnotationPresent(Component.class) || fieldType.isInterface()) {
                            Class<?> providingClass = getProvidingClass(components, fieldType);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                                provided.add(providingClass);
                                continue;
                            } else {
                                // leave unresolved to be handled at injection time
                            }
                        }
                        if (components.contains(fieldType)) {
                            dependencies.add(fieldType);
                        } else {
                            Class<?> providingClass = getProvidingClass(components, fieldType);
                            if (providingClass != null) {
                                dependencies.add(providingClass);
                                provided.add(providingClass);
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
        Set<Class<?>> visiting = new HashSet<>();

        for (Class<?> component : dependencyGraph.keySet()) {
            if (!visited.contains(component)) {
                performTopologicalSort(component, dependencyGraph, visited, visiting, sortedComponents);
            }
        }

        return sortedComponents;
    }

    private void performTopologicalSort(Class<?> component,
                                        Map<Class<?>, Set<Class<?>>> graph,
                                        Set<Class<?>> visited,
                                        Set<Class<?>> visiting,
                                        LinkedList<Class<?>> sortedComponents) {
        if (visiting.contains(component)) {
            throw new RuntimeException("Circular dependency detected involving: " + component.getName());
        }

        if (!visited.contains(component)) {
            visiting.add(component);

            for (Class<?> dependency : graph.getOrDefault(component, Collections.emptySet())) {
                performTopologicalSort(dependency, graph, visited, visiting, sortedComponents);
            }

            visiting.remove(component);
            visited.add(component);
            // Change made here: Add to the end instead of the front
            sortedComponents.addLast(component);
        }
    }

    public <T> T newInstance(Class<T> clazz) {
        Object component = dependencies.get(clazz);
        if (component != null && !clazz.isAnnotationPresent(Entity.class)) {
            return (T) component;
        }
        try {
            //Check if we need to inject dependencies into the constructor
            if (!hasDefaultConstructor(clazz)) {
                Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
                Object[] parameters = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Object dependency = dependencies.get(parameterTypes[i]);
                    if (dependency == null) {
                        boolean isOptional = Arrays.stream(parameterAnnotations[i])
                                .anyMatch(a -> a.annotationType().equals(OptionalDependency.class));
                        if (isOptional) {
                            parameters[i] = null;
                        } else {
                            if (skippedDueToDependsOn != null && skippedDueToDependsOn.contains(parameterTypes[i])) {
                                List<String> missing = missingDependenciesByClass != null ? missingDependenciesByClass.getOrDefault(parameterTypes[i], Collections.emptyList()) : Collections.emptyList();
                                throw new RuntimeException("Dependency not loaded for constructor parameter: " + parameterTypes[i].getName() + " in class: " + clazz.getName() + ". Missing runtime dependencies: " + String.join(", ", missing) + ". Consider annotating the parameter with @OptionalDependency to inject null.");
                            }
                            throw new RuntimeException("Dependency not found for constructor parameter: " + parameterTypes[i].getName() + " in class: " + clazz.getName() + ". Forget to add Bean?");
                        }
                    } else {
                        parameters[i] = dependency;
                    }
                }
                injectStatic(clazz);
                T instance = (T) clazz.getDeclaredConstructors()[0].newInstance(parameters);
                inject(instance);

                if (clazz.isAnnotationPresent(Component.class)) {
                    Component componentAnnotation = clazz.getAnnotation(Component.class);
                    for (Class<?> subclass : componentAnnotation.registerSubclasses()) {
                        dependencies.put(subclass, instance);
                    }
                }

                //Register the instance in the dependency container
                dependencies.put(clazz, instance);
                return instance;
            }
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            injectStatic(clazz);
            T instance = constructor.newInstance();
            inject(instance);
            //Register the instance in the dependency container
            dependencies.put(clazz, instance);
            if (clazz.isAnnotationPresent(Component.class)) {
                Component componentAnnotation = clazz.getAnnotation(Component.class);
                for (Class<?> subclass : componentAnnotation.registerSubclasses()) {
                    dependencies.put(subclass, instance);
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create new instance of class: " + clazz.getName(), e);
        }
    }

    private boolean hasDefaultConstructor(Class<?> clazz) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public <T> @NotNull T getDependency(Class<T> dependency) {
        Object result = dependencies.get(dependency);
        if (result == null) {
            throw new RuntimeException("Dependency not found for class: " + dependency.getName());
        }
        return (T) result;
    }

    @Override
    public <T> @Nullable T getDependencyOrNull(Class<T> dependency) {
        return (T) dependencies.get(dependency);
    }

    //TODO: Show error when a non static field is injected and used in the constructor
    @Override
    public void inject(@NotNull Object object) {
        //Inject object where @Inject annotation is present
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class) && !Modifier.isStatic(field.getModifiers())) {
                Object dependency = dependencies.get(field.getType());
                if (dependency == null) {
                    boolean isOptional = field.isAnnotationPresent(OptionalDependency.class);
                    if (isOptional) {
                        try {
                            unsafe.putObject(object, unsafe.objectFieldOffset(field), null);
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to inject optional dependency: " + field.getType() + " " + field.getName() + " in class: " + object.getClass().getName(), e);
                        }
                        continue;
                    }
                    //Check if the object is a @Service class
                    if (object.getClass().isAnnotationPresent(Service.class)) {
                        throw new RuntimeException("Only @Root class can be injected to @Service classes! Class: " + object.getClass().getName());
                    } else {
                        if (skippedDueToDependsOn != null && skippedDueToDependsOn.contains(field.getType())) {
                            List<String> missing = missingDependenciesByClass != null ? missingDependenciesByClass.getOrDefault(field.getType(), Collections.emptyList()) : Collections.emptyList();
                            throw new RuntimeException("Dependency not loaded for field: " + field.getType().getName() + " " + field.getName() + " in class: " + object.getClass().getName() + ". Missing runtime dependencies: " + String.join(", ", missing) + ". Consider annotating the field with @OptionalDependency to inject null.");
                        }
                        throw new RuntimeException("Dependency not found for field: " + field.getType() + " " + field.getName() + " in class: " + object.getClass().getName() + " while injecting dependencies. Forget to add Bean?");
                    }
                }
                try {
                    unsafe.putObject(object, unsafe.objectFieldOffset(field), dependency);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject dependency: " + field.getType() + " " + field.getName() + " in class: " + object.getClass().getName(), e);
                }
            }
        }
    }

    @Override
    public void addBean(@NotNull Class<?> dependency, @NotNull Object instance) {
        dependencies.put(dependency, instance);
    }

    @Override
    public void injectStatic(@NotNull Class<?> target) {
        //inject static fields before the class is loaded so injected fields are available in static blocks
        for (Field field : target.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class) && Modifier.isStatic(field.getModifiers())) {
                Object dependency = dependencies.get(field.getType());
                if (dependency == null) {
                    boolean isOptional = field.isAnnotationPresent(OptionalDependency.class);
                    if (isOptional) {
                        try {
                            unsafe.putObject(target, unsafe.staticFieldOffset(field), null);
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to inject optional dependency: " + field.getType() + " " + field.getName() + " in class: " + target.getName(), e);
                        }
                        continue;
                    }
                    if (skippedDueToDependsOn != null && skippedDueToDependsOn.contains(field.getType())) {
                        List<String> missing = missingDependenciesByClass != null ? missingDependenciesByClass.getOrDefault(field.getType(), Collections.emptyList()) : Collections.emptyList();
                        throw new RuntimeException("Dependency not loaded for field: " + field.getType().getName() + " " + field.getName() + " in class: " + target.getName() + ". Missing runtime dependencies: " + String.join(", ", missing) + ". Consider annotating the field with @OptionalDependency to inject null.");
                    }
                    throw new RuntimeException("Dependency not found for field: " + field.getType() +" " + field.getName() + " in class: " + target.getName() + " while injecting static dependencies. Forget to add Bean?");
                }
                try {
                    unsafe.putObject(target, unsafe.staticFieldOffset(field), dependency);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject dependency: " + field.getType() + " " + field.getName() + " in class: " + target.getName(), e);
                }
            }
        }
    }

    public void replaceBean(Class<?> clazz, Object instance) {
        dependencies.put(clazz, instance);
    }

    public void registerComponent(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Component.class)) {
            //Do not register non component classes
            return;
        }
        newInstance(clazz); //This will auto register the component and subclasses if needed
    }

    public void registerBeans(Class<?> clazz) {
        //Register beans where @Bean annotation is present
        //Invoke the method and get an instance of the class
        try {
            //Get all annotated methods from class
            Set<Method> beans = Arrays.stream(clazz.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Bean.class)).collect(Collectors.toSet());
            if (beans.isEmpty()) return; //No beans to register, return

            //Check if it has a default constructor, if not throw an exception
            Constructor<?> constructor;
            try {
                constructor = clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Could not register beans, class: " + clazz.getName() + " does not have a default constructor");
            }

            Object instance = newInstance(clazz); //Create new instance of the service and inject dependencies (Root class)

            //Register service as a Bean itself
            dependencies.put(clazz, instance);

            for (Method bean : beans) {
                bean.setAccessible(true);
                Object beanInstance = bean.invoke(instance); //Invoke the method and get an instance of the class
                if (beanInstance == null) {
                    System.err.println("Unable to register bean for " + bean.getReturnType().getName() + ". Value most not be null!");
                    continue;
                }
                dependencies.put(bean.getReturnType(), beanInstance); //Add the instance to the dependencies map

                Bean annotation = bean.getAnnotation(Bean.class);
                //Check if it has more classes to register as
                for (Class<?> register : annotation.registerSubclasses()) {
                    dependencies.put(register, beanInstance);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to register beans", e);
        }
    }

    private Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true); // Make the field accessible
            return (Unsafe) field.get(null); // Get the Unsafe instance
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Unsafe", e);
        }
    }

    public Class[] getAllEntities() {
        return entities.toArray(new Class[0]);
    }

    private boolean canLoadClass(Class<?> clazz) {
        DependsOn dependsOn = clazz.getAnnotation(DependsOn.class);
        if (dependsOn == null) {
            return true;
        }
        List<String> missing = new ArrayList<>();
        ClassLoader loader = clazz.getClassLoader();
        try {
            Class<?> val = dependsOn.value();
            if (val != null && val != Void.class) {
                if (!isClassPresent(val.getName(), loader)) {
                    missing.add(val.getName());
                }
            }
        } catch (Throwable ignored) {
            missing.add("<unavailable:value>");
        }
        try {
            for (Class<?> c : dependsOn.values()) {
                if (c != null && !isClassPresent(c.getName(), loader)) {
                    missing.add(c.getName());
                }
            }
        } catch (Throwable ignored) {
            missing.add("<unavailable:values>");
        }
        String className = dependsOn.className();
        if (className != null && !className.isEmpty() && !isClassPresent(className, loader)) {
            missing.add(className);
        }
        for (String name : dependsOn.classNames()) {
            if (name != null && !name.isEmpty() && !isClassPresent(name, loader)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            if (!dependsOn.soft()) {
                // Hard dependency: throw immediately with details
                throw new RuntimeException("Hard DependsOn failure for class: " + clazz.getName() + ". Missing classes: " + String.join(", ", missing));
            }
            skippedDueToDependsOn.add(clazz);
            missingDependenciesByClass.put(clazz, missing);
            return false;
        }
        return true;
    }

    private boolean isClassPresent(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
