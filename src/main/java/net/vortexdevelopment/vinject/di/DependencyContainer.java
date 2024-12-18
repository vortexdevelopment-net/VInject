package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.RegisterListener;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Service;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationHandler;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import sun.misc.Unsafe;

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
    private final Class<?> pluginClass;
    private final Object pluginInstance;
    private final Set<Class<?>> entities;

    public DependencyContainer(String pluginRoot, Class<?> pluginClass, Object pluginInstance) {
        dependencies = new ConcurrentHashMap<>();
        entities = ConcurrentHashMap.newKeySet();
        unsafe = getUnsafe();

        //Add plugin as bean so components can inject it
        dependencies.put(pluginClass, pluginInstance);
        this.pluginClass = pluginClass;
        this.pluginInstance = pluginInstance;

        //Find all classes which annotated as Service
        Reflections reflections = new Reflections(pluginRoot);

        //Register Beans and Services
        reflections.getTypesAnnotatedWith(Service.class).forEach(this::registerBeans);

        //Register Components
        //Need to create a loading order to avoid circular dependencies and inject issues
        createLoadingOrder(reflections.getTypesAnnotatedWith(Component.class)).forEach(this::registerComponent);

        //Get all entities
        entities.addAll(reflections.getTypesAnnotatedWith(Entity.class));
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

    public void registerRepositories(RepositoryContainer repositoryContainer) {
        Reflections reflections = new Reflections(pluginClass.getPackageName());

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

            System.out.println("Registered repository: " + repositoryClass.getName() + " with entity class: " + entityClass.getName());
            // Register the repository and its entity type
            RepositoryInvocationHandler<?, ?> proxy = repositoryContainer.registerRepository(repositoryClass, entityClass, this);
            this.dependencies.put(repositoryClass, proxy.create());
        });
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
                    if (!fieldType.isAnnotationPresent(Service.class) && !fieldType.equals(this.pluginClass)) {
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

        System.err.println("Load order: " + sortedComponents);

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
                    throw new RuntimeException("Dependency not found for field: " + field.getType() +" " + field.getName() + " in class: " + object.getClass().getName() + ". Forget to add Bean?");
                }
                unsafe.putObject(object, unsafe.objectFieldOffset(field), dependency);
            }
        }
    }

    public void registerComponent(Class<?> clazz) {
        Object instance = newInstance(clazz);
        inject(instance);
        dependencies.put(clazz, instance);
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

            Object instance = constructor.newInstance(); //Create an instance of the class

            //Register service as a Bean itself
            dependencies.put(clazz, instance);

            for (Method bean : beans) {
                bean.setAccessible(true);
                Object beanInstance = bean.invoke(instance); //Invoke the method and get an instance of the class
                dependencies.put(bean.getReturnType(), beanInstance); //Add the instance to the dependencies map
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
