package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.MultipleClasses;
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
import org.reflections.Configuration;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import sun.misc.Unsafe;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DependencyContainer {

    private final Map<Class<?>, Object> dependencies;
    private final Unsafe unsafe;
    private final Class<?> rootClass;
    private final Object rootInstance;
    private final Set<Class<?>> entities;
    private AnnotationHandlerRegistry annotationHandlerRegistry;

    public DependencyContainer(Root rootAnnotation, Class<?> rootClass, Object rootInstance, Database database, RepositoryContainer repositoryContainer) {
        dependencies = new ConcurrentHashMap<>();
        entities = ConcurrentHashMap.newKeySet();
        unsafe = getUnsafe();
        annotationHandlerRegistry = new AnnotationHandlerRegistry();

        //Add plugin as bean so components can inject it
        dependencies.put(rootClass, rootInstance);
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

        //Collect all Registry annotations
        reflections.getTypesAnnotatedWith(Registry.class).forEach(aClass -> {
            //Check if extends AnnotationHandler class
            if (aClass.getSuperclass().equals(AnnotationHandler.class)) {
                AnnotationHandler instance = (AnnotationHandler) newInstance(aClass);
                Class<? extends Annotation> annotation = instance.getAnnotation();
                if (annotation == null) {
                    throw new RuntimeException("Annotation not found for class: " + aClass + ". Make sure to return a valid annotation in getAnnotation method");
                }
                annotationHandlerRegistry.registerHandler(annotation, instance);
            } else {
                throw new RuntimeException("Class: " + aClass.getName() + " annotated with @Registry does not extend AnnotationHandler");
            }
        });

        annotationHandlerRegistry.getHandlers(RegistryOrder.FIRST).forEach(annotationHandler -> {
            Class<? extends Annotation> find = annotationHandler.getAnnotation();
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                annotationHandler.handle(aClass, this);
            });
        });

        //Register Beans and Services
        reflections.getTypesAnnotatedWith(Service.class).forEach(this::registerBeans);

        annotationHandlerRegistry.getHandlers(RegistryOrder.SERVICES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = annotationHandler.getAnnotation();
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                annotationHandler.handle(aClass, this);
            });
        });

        //Register Components
        //Need to create a loading order to avoid circular dependencies and inject issues
        createLoadingOrder(reflections.getTypesAnnotatedWith(Component.class)).forEach(this::registerComponent);

        annotationHandlerRegistry.getHandlers(RegistryOrder.COMPONENTS).forEach(annotationHandler -> {
            Class<? extends Annotation> find = annotationHandler.getAnnotation();
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                annotationHandler.handle(aClass, this);
            });
        });

        //Get all entities
        entities.addAll(reflections.getTypesAnnotatedWith(Entity.class));

        annotationHandlerRegistry.getHandlers(RegistryOrder.ENTITIES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = annotationHandler.getAnnotation();
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                annotationHandler.handle(aClass, this);
            });
        });

        reflections.getTypesAnnotatedWith(Repository.class).forEach(repositoryClass -> {
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

            // Register the repository and its entity type
            RepositoryInvocationHandler<?, ?> proxy = repositoryContainer.registerRepository(repositoryClass, entityClass, this);
            this.dependencies.put(repositoryClass, proxy.create());
        });
        database.initializeEntityMetadata(this);

        annotationHandlerRegistry.getHandlers(RegistryOrder.REPOSITORIES).forEach(annotationHandler -> {
            Class<? extends Annotation> find = annotationHandler.getAnnotation();
            reflections.getTypesAnnotatedWith(find).forEach(aClass -> {
                annotationHandler.handle(aClass, this);
            });
        });
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
        for (Class<?> component : components) {
            Set<Class<?>> dependencies = new HashSet<>();

            // Check constructor parameters
            Constructor<?>[] constructors = component.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(Component.class)) {
                    dependencies.addAll(Arrays.asList(constructor.getParameterTypes()));
                }
            }

            // Check @Inject annotated fields
            for (Field field : component.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    Class<?> fieldType = field.getType();
                    // Skip fields annotated as Services since they are preloaded
                    // Skip plugin main class, it will always present in the container when component injection is performed
                    if (!fieldType.isAnnotationPresent(Service.class) && !fieldType.equals(this.rootClass)) {
                        dependencies.add(fieldType);
                    }
                }
            }

            dependencyGraph.put(component, dependencies);
        }

        // Perform topological sort
        return performTopologicalSort(dependencyGraph);
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
        try {
            //Check if the Component annotation has any parameters
            Class<?>[] parameters = clazz.getDeclaredConstructor().getParameterTypes();

            if (parameters.length > 0) {
                //Get the classes of the parameters
                Constructor<T> constructor;
                try {
                    constructor = clazz.getDeclaredConstructor(parameters);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("Could not create new instance of class: " + clazz.getName() + "with parameters: " + Arrays.toString(parameters) + ". Constructor not found");
                }

                Object[] parameterInstances = Arrays.stream(parameters).map(dependencies::get).toArray();
                constructor.setAccessible(true);
                T instance = constructor.newInstance(parameterInstances);
                inject(instance);
                return instance;
            }

            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();
            inject(instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Unable to create new instance of class: " + clazz.getName(), e);
        }
    }

    public void inject(Object object) {
        //Inject object where @Inject annotation is present
        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                field.setAccessible(true);
                Object dependency = dependencies.get(field.getType());
                if (dependency == null) {
                    //Check if the object is a @Service class
                    if (object.getClass().isAnnotationPresent(Service.class)) {
                        throw new RuntimeException("Only @Root class can be injected to @Service classes! Class: " + object.getClass().getName());
                    } else {
                        throw new RuntimeException("Dependency not found for field: " + field.getType() +" " + field.getName() + " in class: " + object.getClass().getName() + ". Forget to add Bean?");
                    }

                }
                unsafe.putObject(object, unsafe.objectFieldOffset(field), dependency);
            }
        }
    }

    public void replaceBean(Class<?> clazz, Object instance) {
        dependencies.put(clazz, instance);
    }

    public void registerComponent(Class<?> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        MultipleClasses multipleClasses = clazz.getAnnotation(MultipleClasses.class);
        Object instance = newInstance(clazz);
        dependencies.put(clazz, instance);
        for (Class<?> subclass : multipleClasses.registerSubclasses()) {
            dependencies.put(subclass, instance);
        }
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
                dependencies.put(bean.getReturnType(), beanInstance); //Add the instance to the dependencies map

                MultipleClasses annotation = bean.getAnnotation(MultipleClasses.class);
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
}
