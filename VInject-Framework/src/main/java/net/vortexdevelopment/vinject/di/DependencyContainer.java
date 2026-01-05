package net.vortexdevelopment.vinject.di;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.DependsOn;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.RegisterDatabaseSerializer;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationHandler;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.di.engine.ConditionEvaluator;
import net.vortexdevelopment.vinject.di.engine.DependencyGraphResolver;
import net.vortexdevelopment.vinject.di.engine.InjectionEngine;
import net.vortexdevelopment.vinject.di.lifecycle.LifecycleManager;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandlerRegistry;
import net.vortexdevelopment.vinject.di.registry.RegistryOrder;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverContext;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverProcessor;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverRegistry;
import net.vortexdevelopment.vinject.di.scan.ClasspathScanner;
import net.vortexdevelopment.vinject.di.utils.DependencyUtils;
import net.vortexdevelopment.vinject.event.EventManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DependencyContainer implements DependencyRepository {

    @Getter private final Map<Class<?>, Object> dependencies;
    @Getter private final Class<?> rootClass;
    private final Set<Class<?>> entities;
    private final Set<Class<?>> skippedDueToDependsOn;
    private final Map<Class<?>, List<String>> missingDependenciesByClass;
    private AnnotationHandlerRegistry annotationHandlerRegistry;
    private final ArgumentResolverRegistry argumentResolverRegistry;
    @Getter private final EventManager eventManager;
    @Getter private final LifecycleManager lifecycleManager;
    @Getter private final InjectionEngine injectionEngine;
    private final ConditionEvaluator conditionEvaluator;
    private final DependencyGraphResolver dependencyGraphResolver;
    
    // Circular dependency handling
    private final ThreadLocal<Set<Class<?>>> currentlyCreating = ThreadLocal.withInitial(HashSet::new);

    @Getter private static DependencyContainer instance;

    @SuppressWarnings({"unchecked"})
    public DependencyContainer(Root rootAnnotation, Class<?> rootClass, @Nullable Object rootInstance, Database database, RepositoryContainer repositoryContainer, @Nullable Consumer<Void> onPreComponentLoad) {
        instance = this;
        eventManager = new EventManager(this);
        lifecycleManager = new LifecycleManager(this);
        injectionEngine = new InjectionEngine(this);
        conditionEvaluator = new ConditionEvaluator();
        dependencyGraphResolver = new DependencyGraphResolver(this);
        dependencies = new ConcurrentHashMap<>();
        entities = ConcurrentHashMap.newKeySet();
        skippedDueToDependsOn = ConcurrentHashMap.newKeySet();
        missingDependenciesByClass = new ConcurrentHashMap<>();
        annotationHandlerRegistry = new AnnotationHandlerRegistry();
        argumentResolverRegistry = new ArgumentResolverRegistry();

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
        this.rootClass = rootClass;

        if (onPreComponentLoad != null) {
            onPreComponentLoad.accept(null);
        }

        // Included packages override ignored packages
        ClasspathScanner scanner = new ClasspathScanner(rootAnnotation, rootClass);
        Reflections reflections = scanner.getReflections();

        // Get all entities first so we can initialize the database before components
        if (database != null) {
            entities.addAll(scanner.getTypesAnnotatedWith(Entity.class));
        }

        // Load YAML configuration classes so components/services can depend on them
        // We collect only classes that can be loaded (respect DependsOn)
        Set<Class<?>> yamlConfigClasses = scanner.scanAndFilter(YamlConfiguration.class, this::canLoadClass);
        Set<Class<?>> yamlDirectoryClasses = scanner.scanAndFilter((Class<? extends Annotation>) YamlDirectory.class, this::canLoadClass);

        final ConfigurationContainer configurationContainer;
        if (!yamlConfigClasses.isEmpty() || !yamlDirectoryClasses.isEmpty()) {
            // Create the configuration container and register configs into this dependency container
            configurationContainer = new ConfigurationContainer(this, scanner, reflections, yamlConfigClasses);
        } else {
            configurationContainer = null;
        }
        // Register the ConfigurationContainer itself so it can be injected
        if (configurationContainer != null) {
            this.dependencies.put(ConfigurationContainer.class, configurationContainer);
        }

        processAnnotationHandlers(RegistryOrder.ENTITIES, scanner);

        // Auto-register RegisterDatabaseSerializer implementations FIRST
        // This must happen before repository registration because RepositoryInvocationHandler
        // creates EntityMetadata in its constructor, which needs serializers to be registered
        if (database != null) {
            scanner.scanAndFilter(RegisterDatabaseSerializer.class, this::canLoadClass).forEach(serializerClass -> {
                try {
                    RegisterDatabaseSerializer annotation = serializerClass.getAnnotation(RegisterDatabaseSerializer.class);
                    if (annotation == null) {
                        return;
                    }
                    Class<?> targetType = annotation.value();
                    Object instance = newInstance(serializerClass);
                    if (instance instanceof DatabaseSerializer serializer) {
                        database.registerSerializer(targetType, serializer);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Unable to register RegisterDatabaseSerializer: " + serializerClass.getName(), e);
                }
            });
        }
        
        // Register Repositories (after serializers are registered)
        scanner.scanAndFilter(Repository.class, this::canLoadClass).forEach(repositoryClass -> {
            // Check if the class implements CrudRepository
            if (!ReflectionUtils.getAllSuperTypes(repositoryClass).contains(CrudRepository.class)) {
                throw new RuntimeException("Class: " + repositoryClass.getName()
                        + " annotated with @Repository does not implement CrudRepository");
            }

            // Find the generic type argument of CrudRepository
            Class<?> entityClass = DependencyUtils.getGenericTypeFromCrudRepository(repositoryClass);
            if (entityClass == null) {
                throw new RuntimeException("Unable to determine generic type for CrudRepository in class: " + repositoryClass.getName());
            }

            eventManager.registerEventListeners(repositoryClass);

            // Register the repository and its entity type
            RepositoryInvocationHandler<?, ?> proxy = repositoryContainer.registerRepository(repositoryClass, entityClass, this);
            this.dependencies.put(repositoryClass, proxy.create());
        });
        
        //Run database initialization (Create, Update tables)
        if (database != null) {
            database.initializeEntityMetadata(this);
            database.verifyTables();
        }

        //Collect all Registry annotations
        registerHandlers(scanner);

        //Collect all ArgumentResolver annotations
        registerArgumentResolvers(scanner);

        processAnnotationHandlers(RegistryOrder.FIRST, scanner);

        //Register Beans and Services
        scanner.scanAndFilter(Service.class, this::canLoadClass).forEach(serviceClass -> {
            eventManager.registerEventListeners(serviceClass);
            registerBeans(serviceClass);
        });

        processAnnotationHandlers(RegistryOrder.SERVICES, scanner);

        processAnnotationHandlers(RegistryOrder.REPOSITORIES, scanner);

        //Register Components
        //Need to create a loading order to avoid circular dependencies and inject issues
        Set<Class<?>> allComponents = scanner.getTypesAnnotatedWith(Component.class);
        Set<Class<?>> loadableComponents = allComponents.stream().filter(this::canLoadClass).collect(Collectors.toSet());
        dependencyGraphResolver.createLoadingOrder(loadableComponents).stream().sorted(Comparator.comparingInt(value -> {
            Component component = value.getAnnotation(Component.class);
            if (component != null) {
                return component.priority();
            }
            return 10;
        })).forEach(componentClass -> {
            eventManager.registerEventListeners(componentClass);
            registerComponent(componentClass);
        });

        processAnnotationHandlers(RegistryOrder.COMPONENTS, scanner);

        // Fully inject the root instance after all dependencies are ready
        injectionEngine.inject(rootInstance);

        // Scan for @OnDestroy methods (including root instance)
        lifecycleManager.scanDestroyMethods();
    }

    /**
     * Get all registered entity classes.
     *
     * @return Set of entity classes
     */
    public Set<Class<?>> getAllEntities() {
        return Collections.unmodifiableSet(entities);
    }

    /**
     * Release all resources held by the dependency container.
     * Invokes any @OnDestroy methods before clearing dependencies.
     */
    public void release() {
        // Call any OnDestroy methods before clearing
        lifecycleManager.invokeDestroyMethods();
        dependencies.clear();
        entities.clear();
        annotationHandlerRegistry = null;
        eventManager.clear();
        lifecycleManager.clear();
    }

    /**
     * Resolve an argument using the argument resolver system.
     * Falls back to existing logic if no resolver handles it.
     *
     * @param context The resolver context
     * @return The resolved value, or null if no resolver handled it
     */
    @Nullable
    public Object resolveArgument(ArgumentResolverContext context) {
        // Try all resolvers in priority order
        for (ArgumentResolverProcessor resolver : argumentResolverRegistry.getAllResolvers()) {
            if (resolver.canResolve(context)) {
                return resolver.resolve(context);
            }
        }
        return null;
    }

    public <T> T newInstance(Class<T> clazz) {
        return newInstance(clazz, !clazz.isAnnotationPresent(Entity.class));
    }

    public <T> T newInstance(Class<T> clazz, boolean cache) {
        if (cache) {
            Object component = dependencies.get(clazz);
            if (component != null) {
                return clazz.cast(component);
            }
        }
        
        // Check if we're already creating this class (circular dependency)
        Set<Class<?>> creating = currentlyCreating.get();
        if (creating.contains(clazz)) {
            // Circular dependency detected - this is only allowed for field injection
            throw new RuntimeException(
                "Circular dependency detected for class: " + clazz.getName() + 
                "\nCircular dependencies are only supported with field injection (@Inject on fields)." +
                "\nConstructor injection cannot handle circular dependencies because the instance must be fully created before it can be injected." +
                "\n\nTo resolve this issue, use field injection instead of constructor parameters."
            );
        }
        
        try {
            creating.add(clazz);
            T instance;
            
            if (!DependencyUtils.hasDefaultConstructor(clazz)) {
                Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
                Object[] parameters = resolveParameters(clazz, constructor, null);
                
                instance = clazz.cast(constructor.newInstance(parameters));
            } else {
                Constructor<T> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            }

            // Register the instance in the dependency container BEFORE injecting fields
            Class<?>[] subclasses = {};
            if (clazz.isAnnotationPresent(Component.class)) {
                subclasses = clazz.getAnnotation(Component.class).registerSubclasses();
            }
            registerInstanceAndSubclasses(clazz, instance, subclasses, cache);
            
            // Inject static fields (now safe to refer to the class itself)
            injectionEngine.injectStatic(clazz);
            
            // Inject instance fields
            injectionEngine.inject(instance);
            
            // Invoke post construct after all dependencies are injected
            lifecycleManager.invokePostConstruct(instance);
            return instance;
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Unable to create new instance of class: " + clazz.getName(), e);
        } finally {
            creating.remove(clazz);
        }
    }

    private void registerInstanceAndSubclasses(Class<?> clazz, Object instance, Class<?>[] subclasses, boolean cache) {
        if (!cache) return;
        dependencies.put(clazz, instance);
        for (Class<?> subclass : subclasses) {
            dependencies.put(subclass, instance);
        }
    }

    private Object[] resolveParameters(Class<?> declaringClass, Executable executable, @Nullable Object instance) {
        Parameter[] parameters = executable.getParameters();
        Annotation[][] annotations = executable.getParameterAnnotations();
        Object[] resolvedValues = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            ArgumentResolverContext.Builder builder = new ArgumentResolverContext.Builder()
                    .targetType(parameters[i].getType())
                    .annotations(annotations[i])
                    .parameter(parameters[i])
                    .declaringClass(declaringClass)
                    .container(this)
                    .instance(instance);
            
            if (executable instanceof Method m) {
                builder.method(m);
            } else if (executable instanceof Constructor<?> c) {
                builder.constructor(c);
            }

            Object resolvedValue = resolveArgument(builder.build());
            
            // Fallback for @Value if not handled by resolver
            if (resolvedValue == null) {
                Value valueAnnotation = DependencyUtils.findAnnotation(annotations[i], Value.class);
                if (valueAnnotation != null) {
                    resolvedValue = injectionEngine.resolveValue(valueAnnotation.value(), parameters[i].getType());
                }
            }

            if (resolvedValue == null && DependencyUtils.findAnnotation(annotations[i], OptionalDependency.class) == null) {
                String type = (executable instanceof Constructor) ? "constructor parameter" : "@Bean method parameter";
                String source = (executable instanceof Method m) ? " in method: " + m.getName() : "";
                throw new RuntimeException("Unable to resolve " + type + ": " + parameters[i].getType().getName() +
                        source + " in class: " + declaringClass.getName() +
                        ". Use @Inject, @Value, or @OptionalDependency annotation.");
            }
            resolvedValues[i] = resolvedValue;
        }
        return resolvedValues;
    }

    /**
     * Bridge method for LifecycleManager to resolve arguments for methods.
     */
    public Object resolveLifecycleArgument(Object instance, Method method, Parameter parameter, int index) {
        return resolveParameter(instance.getClass(), method, index, instance);
    }

    @Override
    public <T> @NotNull T getDependency(Class<T> dependency) {
        Object result = dependencies.get(dependency);
        if (result == null) {
            throw new RuntimeException("Dependency not found for class: " + dependency.getName());
        }
        return dependency.cast(result);
    }

    @Override
    public <T> @Nullable T getDependencyOrNull(Class<T> dependency) {
        Object result = dependencies.get(dependency);
        return result != null ? dependency.cast(result) : null;
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

    @Override
    public void addBean(@NotNull Class<?> dependency, @NotNull Object instance) {
        dependencies.put(dependency, instance);
    }

    public void registerComponent(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Component.class)) {
            //Do not register non component classes
            return;
        }
        newInstance(clazz); //This will auto register the component and subclasses if needed
    }

    /**
     * Register all @Bean methods in the given class.
     *
     * @param clazz The class to scan for @Bean methods
     */
    public void registerBeans(Class<?> clazz) {
        try {
            Set<Method> beans = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Bean.class))
                    .collect(Collectors.toSet());
            if (beans.isEmpty()) return;

            if (!DependencyUtils.hasDefaultConstructor(clazz)) {
                throw new RuntimeException("Could not register beans, class: " + clazz.getName() + " does not have a default constructor");
            }

            Object instance = newInstance(clazz);
            dependencies.put(clazz, instance);

            for (Method beanMethod : beans) {
                beanMethod.setAccessible(true);
                Object[] parameters = resolveParameters(clazz, beanMethod, instance);

                Object beanInstance = (Object) beanMethod.invoke(instance, parameters);
                if (beanInstance == null) {
                    System.err.println("Unable to register bean for " + beanMethod.getReturnType().getName() + ". Value must not be null!");
                    continue;
                }

                lifecycleManager.invokePostConstruct(beanInstance);

                Bean annotation = beanMethod.getAnnotation(Bean.class);
                registerInstanceAndSubclasses(beanMethod.getReturnType(), beanInstance, annotation.registerSubclasses(), true);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Unable to register beans", e);
        }
    }

    boolean canLoadClass(Class<?> clazz) {
        if (!checkDependsOnAnnotation(clazz)) {
            return false;
        }
        if (!conditionEvaluator.checkConditionalAnnotation(clazz)) {
            return false;
        }
        if (!conditionEvaluator.checkYamlConditionalAnnotation(clazz)) {
            return false;
        }
        return true;
    }

    private boolean checkDependsOnAnnotation(Class<?> clazz) {
        DependsOn dependsOn = clazz.getAnnotation(DependsOn.class);
        if (dependsOn == null) {
            return true;
        }
        List<String> missing = new ArrayList<>();
        ClassLoader loader = clazz.getClassLoader();
        try {
            Class<?> val = dependsOn.value();
            if (val != null && val != Void.class) {
                if (!conditionEvaluator.isClassPresent(val.getName(), loader)) {
                    missing.add(val.getName());
                }
            }
        } catch (Throwable ignored) {
            missing.add("<unavailable:value>");
        }
        try {
            for (Class<?> c : dependsOn.values()) {
                if (c != null && !conditionEvaluator.isClassPresent(c.getName(), loader)) {
                    missing.add(c.getName());
                }
            }
        } catch (Throwable ignored) {
            missing.add("<unavailable:values>");
        }
        String className = dependsOn.className();
        if (className != null && !className.isEmpty() && !conditionEvaluator.isClassPresent(className, loader)) {
            missing.add(className);
        }
        for (String name : dependsOn.classNames()) {
            if (name != null && !name.isEmpty() && !conditionEvaluator.isClassPresent(name, loader)) {
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

    private Object resolveParameter(Class<?> declaringClass, Executable executable, int index, @Nullable Object instance) {
        Parameter parameter = executable.getParameters()[index];
        Annotation[] annotations = executable.getParameterAnnotations()[index];

        ArgumentResolverContext.Builder builder = new ArgumentResolverContext.Builder()
                .targetType(parameter.getType())
                .annotations(annotations)
                .parameter(parameter)
                .declaringClass(declaringClass)
                .container(this)
                .instance(instance);

        if (executable instanceof Method method) {
            builder.method(method);
        } else if (executable instanceof Constructor<?> constructor) {
            builder.constructor(constructor);
        }

        Object resolvedValue = resolveArgument(builder.build());
        if (resolvedValue != null) {
            return resolvedValue;
        }

        // Fallback for @Value
        Value valueAnnotation = DependencyUtils.findAnnotation(annotations, Value.class);
        if (valueAnnotation != null) {
            return injectionEngine.resolveValue(valueAnnotation.value(), parameter.getType());
        }

        return null;
    }

    private void processAnnotationHandlers(RegistryOrder order, ClasspathScanner scanner) {
        annotationHandlerRegistry.getHandlers(order).forEach(handler -> {
            Class<? extends Annotation> annotation = DependencyUtils.getAnnotationFromHandler(handler);
            if (annotation == null) return;
            scanner.getTypesAnnotatedWith(annotation).forEach(clazz -> {
                if (canLoadClass(clazz)) {
                    handler.handle(clazz, dependencies.get(clazz), this);
                }
            });
        });
    }

    private void registerHandlers(ClasspathScanner scanner) {
        scanner.scanRegistryHandlers().forEach(clazz -> {
            if (AnnotationHandler.class.isAssignableFrom(clazz)) {
                AnnotationHandler instance = (AnnotationHandler) newInstance(clazz);
                Class<? extends Annotation> annotation = DependencyUtils.getAnnotationFromHandler(instance);
                if (annotation == null) {
                    throw new RuntimeException("Annotation not found for class: " + clazz + ". Make sure to return a valid annotation in getAnnotation method");
                }
                eventManager.registerEventListeners(clazz);
                annotationHandlerRegistry.registerHandler(annotation, instance);
            } else {
                throw new RuntimeException("Class: " + clazz.getName() + " annotated with @Registry does not extend AnnotationHandler");
            }
        });
    }

    private void registerArgumentResolvers(ClasspathScanner scanner) {
        scanner.scanArgumentResolvers().forEach(clazz -> {
            if (ArgumentResolverProcessor.class.isAssignableFrom(clazz)) {
                try {
                    ArgumentResolverProcessor instance = (ArgumentResolverProcessor) newInstance(clazz);
                    ArgumentResolver annotation = clazz.getAnnotation(ArgumentResolver.class);
                    if (annotation == null) return;

                    eventManager.registerEventListeners(clazz);
                    int priority = annotation.priority();

                    Class<? extends Annotation>[] values = annotation.values();
                    if (values != null && values.length > 0) {
                        for (Class<? extends Annotation> supportedAnnotation : values) {
                            argumentResolverRegistry.registerResolver(supportedAnnotation, instance, priority);
                        }
                    } else {
                        argumentResolverRegistry.registerResolver(annotation.value(), instance, priority);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Unable to register ArgumentResolverProcessor: " + clazz.getName(), e);
                }
            } else {
                throw new RuntimeException("Class: " + clazz.getName() + " annotated with @ArgumentResolver does not implement ArgumentResolverProcessor");
            }
        });
    }
}
