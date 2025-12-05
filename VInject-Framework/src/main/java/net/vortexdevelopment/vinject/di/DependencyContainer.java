package net.vortexdevelopment.vinject.di;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.DependsOn;
import net.vortexdevelopment.vinject.annotation.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.OnDestroy;
import net.vortexdevelopment.vinject.annotation.OnEvent;
import net.vortexdevelopment.vinject.annotation.PostConstruct;
import net.vortexdevelopment.vinject.annotation.Registry;
import net.vortexdevelopment.vinject.annotation.Repository;
import net.vortexdevelopment.vinject.annotation.Root;
import net.vortexdevelopment.vinject.annotation.Service;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationHandler;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandlerRegistry;
import net.vortexdevelopment.vinject.di.registry.RegistryOrder;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverProcessor;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverContext;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverRegistry;
import net.vortexdevelopment.vinject.di.resolver.ValueArgumentResolver;
import net.vortexdevelopment.vinject.di.resolver.InjectArgumentResolver;
import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Configuration;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import sun.misc.Unsafe;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
    private ArgumentResolverRegistry argumentResolverRegistry;
    private Consumer<Void> onPreComponentLoad;
    private Map<String, List<Method>> eventListeners;
    private List<Method> destroyMethods;

    @Getter
    private static DependencyContainer instance;
    public DependencyContainer(Root rootAnnotation, Class<?> rootClass, @Nullable Object rootInstance, Database database, RepositoryContainer repositoryContainer, @Nullable Consumer<Void> onPreComponentLoad) {
        instance = this;
        eventListeners = new ConcurrentHashMap<>();
        dependencies = new ConcurrentHashMap<>();
        entities = ConcurrentHashMap.newKeySet();
        skippedDueToDependsOn = ConcurrentHashMap.newKeySet();
        missingDependenciesByClass = new ConcurrentHashMap<>();
        destroyMethods = new ArrayList<>();
        unsafe = getUnsafe();
        annotationHandlerRegistry = new AnnotationHandlerRegistry();
        argumentResolverRegistry = new ArgumentResolverRegistry();
        
        // Register built-in resolvers
        registerBuiltInResolvers();

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
        String rootPackage = getEffectivePackageName(rootAnnotation, rootClass);
        // Convert package name to path format (e.g., "net.vortexdevelopment.vinject.app" -> "net/vortexdevelopment/vinject/app")
        String rootPackagePath = rootPackage.replace('.', '/');

        Configuration configuration = new ConfigurationBuilder()
                .forPackage(rootPackage)
                .filterInputsBy(s -> {
                    if (s == null) return false;
                    if (s.startsWith("META-INF")) return false;
                    if (!s.endsWith(".class")) return false;

                    // First check: Only include classes that are under the root package
                    if (!s.startsWith(rootPackagePath + "/") && !s.equals(rootPackagePath + ".class")) {
                        return false;
                    }

                    // Check ignored packages (must match the path format)
                    for (String ignoredPackage : ignoredPackages) {
                        String ignoredPath = ignoredPackage.replace('.', '/');
                        if (s.startsWith(ignoredPath)) {
                            return false;
                        }
                    }

                    // Check included packages (override ignored packages)
                    for (String includedPackage : includedPackages) {
                        String includedPath = includedPackage.replace('.', '/');
                        if (s.startsWith(includedPath)) {
                            return true;
                        }
                    }

                    // Default: include if under root package (already checked above)
                    return true;
                });

        //Find all classes which annotated as Service
        Reflections reflections = new Reflections(configuration);

        //Get all entities first so we can initialize the database before components
        if (database != null) {
            entities.addAll(reflections.getTypesAnnotatedWith(Entity.class));
        }

        //Load YAML configuration classes so components/services can depend on them
        //We collect only classes that can be loaded (respect DependsOn)
        Set<Class<?>> yamlConfigClasses = reflections.getTypesAnnotatedWith(YamlConfiguration.class)
                .stream().filter(this::canLoadClass).collect(Collectors.toSet());
        final ConfigurationContainer configurationContainer;
        if (!yamlConfigClasses.isEmpty()) {
            // Create the configuration container and register configs into this dependency container
            configurationContainer = new ConfigurationContainer(this, yamlConfigClasses);
        } else {
            configurationContainer = null;
        }
        // Register the ConfigurationContainer itself so it can be injected
        if (configurationContainer != null) {
            this.dependencies.put(ConfigurationContainer.class, configurationContainer);
        }

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
        if (database != null) {
            database.initializeEntityMetadata(this);
            database.verifyTables();
        }

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
        
        //Collect all ArgumentResolver annotations
        reflections.getTypesAnnotatedWith(ArgumentResolver.class).forEach(aClass -> {
            //Check if implements ArgumentResolverProcessor interface
            if (ArgumentResolverProcessor.class.isAssignableFrom(aClass)) {
                try {
                    ArgumentResolverProcessor instance = (ArgumentResolverProcessor) newInstance(aClass);
                    ArgumentResolver annotation = aClass.getAnnotation(ArgumentResolver.class);
                    if (annotation == null) {
                        throw new RuntimeException("Annotation not found for class: " + aClass.getName());
                    }
                    
                    int priority = annotation.priority();
                    registerEventListeners(aClass);
                    
                    // Handle multiple annotations via values(), or single annotation via value()
                    Class<? extends Annotation>[] values = annotation.values();
                    if (values != null && values.length > 0) {
                        // Register for each annotation in values()
                        for (Class<? extends Annotation> supportedAnnotation : values) {
                            argumentResolverRegistry.registerResolver(supportedAnnotation, instance, priority);
                        }
                    } else {
                        // Use single value() (required field)
                        Class<? extends Annotation> supportedAnnotation = annotation.value();
                        argumentResolverRegistry.registerResolver(supportedAnnotation, instance, priority);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Unable to register ArgumentResolverProcessor: " + aClass.getName(), e);
                }
            } else {
                throw new RuntimeException("Class: " + aClass.getName() + " annotated with @ArgumentResolver does not implement ArgumentResolverProcessor");
            }
        });

        // Auto-register YamlSerializerBase implementations into the configuration container
        if (configurationContainer != null) {
            reflections.getTypesAnnotatedWith(net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer.class).forEach(serializerClass -> {
                if (!canLoadClass(serializerClass)) return;
                try {
                    Object instance = newInstance(serializerClass);
                    if (instance instanceof YamlSerializerBase<?> ys) {
                        configurationContainer.registerSerializer(ys);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Unable to register YamlSerializerBase: " + serializerClass.getName(), e);
                }
            });
            // After registering serializers, load any annotated batch directories
            try {
                configurationContainer.loadBatches(reflections, this);
            } catch (Exception e) {
                throw new RuntimeException("Unable to load YAML batch directories", e);
            }
        }

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

        // Scan for @OnDestroy methods (including root instance)
        scanDestroyMethods();
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

    /**
     * Scan all components in the dependency container for @OnDestroy annotated methods.
     * This includes all components, services, beans, and the root instance.
     */
    private void scanDestroyMethods() {
        destroyMethods.clear();

        // FIRST: Always scan root class immediately, before scanning other components
        // This ensures the root class is scanned even if something goes wrong later
        if (rootClass != null) {
            // Get the root instance from dependencies map (it should be there)
            Object rootInstanceToScan = dependencies.get(rootClass);
            if (rootInstanceToScan == null && rootInstance != null) {
                rootInstanceToScan = rootInstance;
            }
            
            // Scan the root class - use instance if available, otherwise scan class methods only
            if (rootInstanceToScan != null) {
                scanDestroyMethodsForClass(rootClass, rootInstanceToScan);
            } else {
                // Scan class methods even without instance (instance will be found during invocation)
                scanDestroyMethodsForClassOnly(rootClass);
            }
        }

        // Then scan all other registered components for @OnDestroy methods
        for (Map.Entry<Class<?>, Object> entry : dependencies.entrySet()) {
            Class<?> componentClass = entry.getKey();
            Object componentInstance = entry.getValue();

            // Skip if instance is null
            if (componentInstance == null) {
                continue;
            }

            // Check if this is the root class (skip if already scanned)
            if (rootClass != null && componentClass.equals(rootClass)) {
                // Skip scanning again (already done above)
                continue;
            }

            // Find all methods annotated with @OnDestroy
            scanDestroyMethodsForClass(componentClass, componentInstance);
        }
    }

    /**
     * Scan a specific class/instance for @OnDestroy methods and add them to the destroyMethods list.
     *
     * @param clazz The class to scan
     * @param instance The instance (used for validation)
     */
    private void scanDestroyMethodsForClass(Class<?> clazz, Object instance) {
        if (clazz == null) {
            return;
        }
        // Note: instance can be null - we'll scan the class methods anyway

        // Find all methods annotated with @OnDestroy
        // Check both declared methods and all methods (to catch inherited ones)
        Method[] allMethods = clazz.getMethods(); // Gets public methods including inherited
        Method[] declaredMethods = clazz.getDeclaredMethods(); // Gets all methods declared in this class
        
        // First check declared methods
        for (Method method : declaredMethods) {
            if (method.isAnnotationPresent(OnDestroy.class)) {
                // Check if this method is already in the list (avoid duplicates)
                boolean alreadyAdded = destroyMethods.stream().anyMatch(m -> 
                    m.getDeclaringClass().equals(method.getDeclaringClass()) && 
                    m.getName().equals(method.getName())
                );
                if (alreadyAdded) {
                    continue;
                }

                // Validate method signature: should have no parameters and return void
                if (method.getParameterCount() != 0) {
                    System.err.println("Warning: @OnDestroy method " + method.getName() + 
                            " in class " + clazz.getName() + 
                            " has parameters. It should have no parameters.");
                    continue;
                }
                if (!method.getReturnType().equals(void.class) && !method.getReturnType().equals(Void.class)) {
                    System.err.println("Warning: @OnDestroy method " + method.getName() + 
                            " in class " + clazz.getName() + 
                            " does not return void. It should return void.");
                    continue;
                }

                method.setAccessible(true);
                destroyMethods.add(method);
            }
        }
        
        // Also check public methods (to catch inherited @OnDestroy methods)
        for (Method method : allMethods) {
            // Skip if already processed as declared method
            boolean isDeclared = false;
            for (Method declared : declaredMethods) {
                if (declared.equals(method)) {
                    isDeclared = true;
                    break;
                }
            }
            if (isDeclared) {
                continue;
            }
            
            if (method.isAnnotationPresent(OnDestroy.class)) {
                // Check if this method is already in the list (avoid duplicates)
                boolean alreadyAdded = destroyMethods.stream().anyMatch(m -> 
                    m.getDeclaringClass().equals(method.getDeclaringClass()) && 
                    m.getName().equals(method.getName())
                );
                if (alreadyAdded) {
                    continue;
                }

                // Validate method signature: should have no parameters and return void
                if (method.getParameterCount() != 0) {
                    System.err.println("Warning: @OnDestroy method " + method.getName() + 
                            " in class " + clazz.getName() + 
                            " has parameters. It should have no parameters.");
                    continue;
                }
                if (!method.getReturnType().equals(void.class) && !method.getReturnType().equals(Void.class)) {
                    System.err.println("Warning: @OnDestroy method " + method.getName() + 
                            " in class " + clazz.getName() + 
                            " does not return void. It should return void.");
                    continue;
                }

                method.setAccessible(true);
                destroyMethods.add(method);
            }
        }
    }

    /**
     * Scan a class for @OnDestroy methods without requiring an instance.
     * Used when we need to scan the root class but instance might not exist yet.
     *
     * @param clazz The class to scan
     */
    private void scanDestroyMethodsForClassOnly(Class<?> clazz) {
        scanDestroyMethodsForClass(clazz, null);
    }

    /**
     * Invoke all methods annotated with @OnDestroy on their respective component instances.
     */
    public void invokeDestroyMethods() {
        // If no destroy methods were found during startup scanning, try scanning again
        // This is a fallback in case scanning didn't happen or was incomplete
        if (destroyMethods.isEmpty()) {
            // Re-scan, especially the root class
            if (rootClass != null) {
                scanDestroyMethodsForClassOnly(rootClass);
            }
            
            // Also try to get root instance from dependencies and scan it
            if (rootClass != null) {
                Object rootInst = dependencies.get(rootClass);
                if (rootInst != null) {
                    scanDestroyMethodsForClass(rootClass, rootInst);
                }
            }
        }
        
        if (destroyMethods.isEmpty()) {
            return;
        }

        // Invoke destroy methods in reverse order (LIFO - last created, first destroyed)
        for (int i = destroyMethods.size() - 1; i >= 0; i--) {
            Method method = destroyMethods.get(i);
            Class<?> componentClass = method.getDeclaringClass();
            Object componentInstance = dependencies.get(componentClass);

            if (componentInstance == null) {
                // Try to find the instance by checking registered subclasses
                componentInstance = findComponentInstance(componentClass);
            }

            // If still not found, check if it's the root instance
            if (componentInstance == null && rootInstance != null && rootClass != null) {
                if (componentClass.equals(rootClass) || rootClass.isAssignableFrom(componentClass)) {
                    componentInstance = rootInstance;
                }
            }

            if (componentInstance != null) {
                try {
                    method.invoke(componentInstance);
                } catch (Exception e) {
                    System.err.println("Error invoking @OnDestroy method " + method.getName() + 
                            " on " + componentClass.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("Warning: Could not find instance for component " + 
                        componentClass.getName() + " to invoke @OnDestroy method " + method.getName());
            }
        }
    }

    /**
     * Find component instance by checking if any registered component implements or extends the given class.
     */
    private Object findComponentInstance(Class<?> targetClass) {
        for (Map.Entry<Class<?>, Object> entry : dependencies.entrySet()) {
            Class<?> componentClass = entry.getKey();
            if (targetClass.isAssignableFrom(componentClass)) {
                return entry.getValue();
            }
        }
        return null;
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
    
    /**
     * Register built-in argument resolvers (@Value and @Inject).
     */
    private void registerBuiltInResolvers() {
        // Register @Value resolver with priority 100 (highest)
        ValueArgumentResolver valueResolver = new ValueArgumentResolver();
        argumentResolverRegistry.registerResolver(Value.class, valueResolver, 100);
        
        // Register @Inject resolver with priority 50
        InjectArgumentResolver injectResolver = new InjectArgumentResolver();
        argumentResolverRegistry.registerResolver(Inject.class, injectResolver, 50);
    }
    
    /**
     * Resolve an argument using the argument resolver system.
     * Falls back to existing logic if no resolver handles it.
     * 
     * @param context The resolver context
     * @return The resolved value, or null if no resolver handled it
     */
    @Nullable
    private Object resolveArgument(ArgumentResolverContext context) {
        // Try all resolvers in priority order
        for (ArgumentResolverProcessor resolver : argumentResolverRegistry.getAllResolvers()) {
            if (resolver.canResolve(context)) {
                Object value = resolver.resolve(context);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
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
                    // Try resolver system first
                    Parameter parameter = constructor.getParameters()[i];
                    ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                            .targetType(parameterTypes[i])
                            .annotations(parameterAnnotations[i])
                            .parameter(parameter)
                            .declaringClass(clazz)
                            .constructor(constructor)
                            .container(this)
                            .instance(null) // Constructor doesn't have instance yet
                            .build();
                    
                    Object resolvedValue = resolveArgument(context);
                    if (resolvedValue != null) {
                        parameters[i] = resolvedValue;
                        continue;
                    }
                    
                    // Fallback to existing logic for backward compatibility (only for @Value if resolver didn't handle it)
                    // Note: @Inject is now fully handled by InjectArgumentResolver
                    Value valueAnnotation = findAnnotation(parameterAnnotations[i], Value.class);
                    if (valueAnnotation != null) {
                        parameters[i] = resolveValue(valueAnnotation.value(), parameterTypes[i]);
                        continue;
                    }
                    
                    // If no resolver handled it and it's not @Value, throw an error
                    throw new RuntimeException("Unable to resolve constructor parameter: " + parameterTypes[i].getName() + " in class: " + clazz.getName() + ". Use @Inject, @Value, or @OptionalDependency annotation.");
                }
                injectStatic(clazz);
                T instance = (T) clazz.getDeclaredConstructors()[0].newInstance(parameters);
                inject(instance);
                invokePostConstruct(instance);

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
            invokePostConstruct(instance);
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

    /**
     * Invoke all methods annotated with @PostConstruct on the given instance.
     * PostConstruct methods are called after constructor execution and dependency injection.
     * Supports dependency injection for method parameters via @Inject or @Value annotations.
     *
     * @param instance The instance to invoke PostConstruct methods on
     */
    public void invokePostConstruct(Object instance) {
        if (instance == null) {
            return;
        }

        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                // Validate return type: should return void
                if (!method.getReturnType().equals(void.class) && !method.getReturnType().equals(Void.class)) {
                    System.err.println("Warning: @PostConstruct method " + method.getName() +
                            " in class " + clazz.getName() +
                            " does not return void. It should return void.");
                    continue;
                }

                try {
                    method.setAccessible(true);
                    
                    // Resolve method parameters with dependency injection support
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                    Object[] parameters = new Object[parameterTypes.length];
                    
                    for (int i = 0; i < parameterTypes.length; i++) {
                        // Try resolver system first
                        Parameter parameter = method.getParameters()[i];
                        ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                                .targetType(parameterTypes[i])
                                .annotations(parameterAnnotations[i])
                                .parameter(parameter)
                                .declaringClass(clazz)
                                .method(method)
                                .container(this)
                                .instance(instance)
                                .build();
                        
                        Object resolvedValue = resolveArgument(context);
                        if (resolvedValue != null) {
                            parameters[i] = resolvedValue;
                            continue;
                        }
                        
                        // Fallback to existing logic for backward compatibility (only for @Value if resolver didn't handle it)
                        // Note: @Inject is now fully handled by InjectArgumentResolver
                        Value valueAnnotation = findAnnotation(parameterAnnotations[i], Value.class);
                        if (valueAnnotation != null) {
                            parameters[i] = resolveValue(valueAnnotation.value(), parameterTypes[i]);
                            continue;
                        }
                        
                        // If no resolver handled it and it's not @Value, throw an error
                        throw new RuntimeException("@PostConstruct method " + method.getName() + 
                                " in class " + clazz.getName() + 
                                " has parameter " + parameterTypes[i].getName() + 
                                " that cannot be injected. Use @Inject or @Value annotation, or mark with @OptionalDependency.");
                    }
                    
                    // Invoke the method with resolved parameters
                    method.invoke(instance, parameters);
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking @PostConstruct method " + method.getName() +
                            " on " + clazz.getName(), e);
                }
            }
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

    /**
     * Check if a class was skipped due to missing DependsOn dependencies.
     * Used by argument resolvers for error reporting.
     * 
     * @param clazz The class to check
     * @return true if the class was skipped
     */
    public boolean isSkippedDueToDependsOn(Class<?> clazz) {
        return skippedDueToDependsOn != null && skippedDueToDependsOn.contains(clazz);
    }

    /**
     * Get missing dependencies for a class that was skipped due to DependsOn.
     * Used by argument resolvers for error reporting.
     * 
     * @param clazz The class to check
     * @return List of missing dependency class names, or empty list if none
     */
    public List<String> getMissingDependencies(Class<?> clazz) {
        return missingDependenciesByClass != null ? missingDependenciesByClass.getOrDefault(clazz, Collections.emptyList()) : Collections.emptyList();
    }

    /**
     * Get the root class.
     * Used by argument resolvers for validation.
     * 
     * @return The root class
     */
    public Class<?> getRootClass() {
        return rootClass;
    }

    //TODO: Show error when a non static field is injected and used in the constructor
    @Override
    public void inject(@NotNull Object object) {
        //Inject object where @Inject or @Value annotation is present
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue; // Skip static fields (handled by injectStatic)
            }
            
            // Try resolver system first
            ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                    .targetType(field.getType())
                    .annotations(field.getAnnotations())
                    .field(field)
                    .declaringClass(field.getDeclaringClass())
                    .container(this)
                    .instance(object)
                    .build();
            
            Object resolvedValue = resolveArgument(context);
            if (resolvedValue != null) {
                try {
                    unsafe.putObject(object, unsafe.objectFieldOffset(field), resolvedValue);
                    continue;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject resolved value for field: " + field.getName() + " in class: " + object.getClass().getName(), e);
                }
            }
            
            // Fallback to existing logic for backward compatibility (only for @Value if resolver didn't handle it)
            // Note: @Inject is now fully handled by InjectArgumentResolver
            if (field.isAnnotationPresent(Value.class)) {
                Value valueAnnotation = field.getAnnotation(Value.class);
                try {
                    Object value = resolveValue(valueAnnotation.value(), field.getType());
                    unsafe.putObject(object, unsafe.objectFieldOffset(field), value);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject @Value for field: " + field.getName() + " in class: " + object.getClass().getName(), e);
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
            if (!Modifier.isStatic(field.getModifiers())) {
                continue; // Skip non-static fields (handled by inject)
            }
            
            // Try resolver system first
            ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                    .targetType(field.getType())
                    .annotations(field.getAnnotations())
                    .field(field)
                    .declaringClass(field.getDeclaringClass())
                    .container(this)
                    .instance(null) // Static fields have no instance
                    .build();
            
            Object resolvedValue = resolveArgument(context);
            if (resolvedValue != null) {
                try {
                    unsafe.putObject(target, unsafe.staticFieldOffset(field), resolvedValue);
                    continue;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject resolved value for static field: " + field.getName() + " in class: " + target.getName(), e);
                }
            }
            
            // Fallback to existing logic for backward compatibility (only for @Value if resolver didn't handle it)
            // Note: @Inject is now fully handled by InjectArgumentResolver
            if (field.isAnnotationPresent(Value.class)) {
                Value valueAnnotation = field.getAnnotation(Value.class);
                try {
                    Object value = resolveValue(valueAnnotation.value(), field.getType());
                    unsafe.putObject(target, unsafe.staticFieldOffset(field), value);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject @Value for static field: " + field.getName() + " in class: " + target.getName(), e);
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
                
                // Resolve method parameters with @Value annotation support
                Class<?>[] parameterTypes = bean.getParameterTypes();
                Annotation[][] parameterAnnotations = bean.getParameterAnnotations();
                Object[] parameters = new Object[parameterTypes.length];
                
                for (int i = 0; i < parameterTypes.length; i++) {
                    // Try resolver system first
                    Parameter parameter = bean.getParameters()[i];
                    ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                            .targetType(parameterTypes[i])
                            .annotations(parameterAnnotations[i])
                            .parameter(parameter)
                            .declaringClass(clazz)
                            .method(bean)
                            .container(this)
                            .instance(instance)
                            .build();
                    
                    Object resolvedValue = resolveArgument(context);
                    if (resolvedValue != null) {
                        parameters[i] = resolvedValue;
                        continue;
                    }
                    
                    // Fallback to existing logic for backward compatibility (only for @Value if resolver didn't handle it)
                    // Note: @Inject is now fully handled by InjectArgumentResolver
                    Value valueAnnotation = findAnnotation(parameterAnnotations[i], Value.class);
                    if (valueAnnotation != null) {
                        parameters[i] = resolveValue(valueAnnotation.value(), parameterTypes[i]);
                        continue;
                    }
                    
                    // If no resolver handled it and it's not @Value, throw an error
                    throw new RuntimeException("Unable to resolve @Bean method parameter: " + parameterTypes[i].getName() + 
                            " in method: " + bean.getName() + " of class: " + clazz.getName() + 
                            ". Use @Inject, @Value, or @OptionalDependency annotation.");
                }
                
                Object beanInstance = bean.invoke(instance, parameters); //Invoke the method with resolved parameters
                if (beanInstance == null) {
                    System.err.println("Unable to register bean for " + bean.getReturnType().getName() + ". Value most not be null!");
                    continue;
                }
                // Invoke PostConstruct on bean instance if present
                invokePostConstruct(beanInstance);
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

    /**
     * Get the effective package name from the root annotation or detect it from the root class.
     * If packageName is specified in the annotation (non-empty), it will be used.
     * Otherwise, the package name will be detected from the root class.
     *
     * @param rootAnnotation The @Root annotation
     * @param rootClass The root class
     * @return The effective package name to scan
     */
    private String getEffectivePackageName(Root rootAnnotation, Class<?> rootClass) {
        String packageName = rootAnnotation.packageName();
        if (packageName == null || packageName.isEmpty()) {
            // Auto-detect package name from root class
            Package pkg = rootClass.getPackage();
            if (pkg != null) {
                packageName = pkg.getName();
            } else {
                // Fallback: extract package name from class name
                String className = rootClass.getName();
                int lastDot = className.lastIndexOf('.');
                if (lastDot > 0) {
                    packageName = className.substring(0, lastDot);
                } else {
                    // Default package - use empty string
                    packageName = "";
                }
            }
        }
        return packageName;
    }

    /**
     * Find an annotation of the specified type in an array of annotations.
     * 
     * @param annotations The array of annotations to search
     * @param annotationType The annotation type to find
     * @param <T> The annotation type
     * @return The annotation instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    private <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> annotationType) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return (T) annotation;
            }
        }
        return null;
    }

    /**
     * Resolve a @Value annotation expression to the appropriate type.
     * Supports Spring Boot-style property resolution with default values.
     * 
     * @param expression The property expression (e.g., "${app.timeout:5000}")
     * @param targetType The target type to convert to
     * @return The resolved and converted value
     */
    private Object resolveValue(String expression, Class<?> targetType) {
        Environment env = Environment.getInstance();
        String resolvedValue = env.resolveProperty(expression);
        
        // Convert to target type
        if (targetType == String.class) {
            return resolvedValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to int for expression: " + expression, e);
            }
        } else if (targetType == long.class || targetType == Long.class) {
            try {
                return Long.parseLong(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to long for expression: " + expression, e);
            }
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(resolvedValue);
        } else if (targetType == double.class || targetType == Double.class) {
            try {
                return Double.parseDouble(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to double for expression: " + expression, e);
            }
        } else if (targetType == float.class || targetType == Float.class) {
            try {
                return Float.parseFloat(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to float for expression: " + expression, e);
            }
        } else {
            throw new RuntimeException("Unsupported type for @Value injection: " + targetType.getName() + " for expression: " + expression);
        }
    }
}
