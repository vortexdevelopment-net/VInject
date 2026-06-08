package net.vortexdevelopment.vinject.analyzer;

import net.vortexdevelopment.vinject.analyzer.diagnostic.Diagnostic;
import net.vortexdevelopment.vinject.analyzer.diagnostic.DiagnosticCode;
import net.vortexdevelopment.vinject.analyzer.diagnostic.DiagnosticLocation;
import net.vortexdevelopment.vinject.analyzer.model.ApplicationModel;
import net.vortexdevelopment.vinject.analyzer.model.BeanKind;
import net.vortexdevelopment.vinject.analyzer.model.BeanModel;
import net.vortexdevelopment.vinject.analyzer.model.DependencyEdge;
import net.vortexdevelopment.vinject.analyzer.model.DependencyEdgeKind;
import net.vortexdevelopment.vinject.analyzer.model.DependencyGraph;
import net.vortexdevelopment.vinject.analyzer.model.DependencyLoadPlan;
import net.vortexdevelopment.vinject.analyzer.model.RegistryHandlerModel;
import net.vortexdevelopment.vinject.analyzer.model.RegistryTargetModel;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.OptionalDependency;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.di.registry.RegistryOrder;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VinjectAnalyzer {

    private static final Set<String> BUILT_IN_TYPES = Set.of(
            "net.vortexdevelopment.vinject.di.DependencyContainer",
            "net.vortexdevelopment.vinject.database.Database",
            "net.vortexdevelopment.vinject.database.repository.RepositoryContainer",
            "net.vortexdevelopment.vinject.event.EventManager",
            "net.vortexdevelopment.vinject.database.cache.CacheCoordinator",
            "net.vortexdevelopment.vinject.database.cache.CacheManager",
            "net.vortexdevelopment.vinject.database.cache.CacheManagerImpl",
            "net.vortexdevelopment.vinject.di.ConfigurationContainer"
    );

    public VinjectAnalysisResult analyze(VinjectAnalysisRequest request) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<Class<?>> candidates = collectCandidates(request, diagnostics);
        Predicate<Class<?>> loadPredicate = request.getLoadPredicate();

        List<Class<?>> loadableCandidates = candidates.stream()
                .filter(clazz -> isLoadable(clazz, loadPredicate, diagnostics))
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());

        Discovery discovery = discover(loadableCandidates, request, diagnostics);
        List<DependencyEdge> edges = buildEdges(discovery, request, diagnostics);

        List<Class<?>> serviceLoadOrder = sortByDependencies(discovery.serviceClasses, edges, diagnostics);
        List<Class<?>> componentLoadOrder = sortByDependencies(discovery.componentLoadClasses, edges, diagnostics);

        ApplicationModel applicationModel = new ApplicationModel(discovery.beans, discovery.registryHandlers, discovery.registryTargets);
        DependencyGraph dependencyGraph = new DependencyGraph(edges);
        DependencyLoadPlan loadPlan = new DependencyLoadPlan(serviceLoadOrder, componentLoadOrder);
        return new VinjectAnalysisResult(applicationModel, dependencyGraph, loadPlan, diagnostics);
    }

    private Set<Class<?>> collectCandidates(VinjectAnalysisRequest request, List<Diagnostic> diagnostics) {
        if (!request.getCandidateClasses().isEmpty()) {
            return new LinkedHashSet<>(request.getCandidateClasses());
        }

        Root root = request.getRootAnnotation();
        Class<?> rootClass = request.getRootClass();
        if (root == null || rootClass == null) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.ANALYZER_MISSING_INPUT));
            return Collections.emptySet();
        }

        Reflections reflections = new Reflections(createConfiguration(root, rootClass));
        Set<Class<?>> candidates = new LinkedHashSet<>();
        candidates.add(rootClass);
        candidates.addAll(reflections.getTypesAnnotatedWith(Component.class));
        candidates.addAll(reflections.getTypesAnnotatedWith(Service.class));
        candidates.addAll(reflections.getTypesAnnotatedWith(Repository.class));
        candidates.addAll(reflections.getTypesAnnotatedWith(Registry.class));

        for (Class<? extends Annotation> annotation : root.componentAnnotations()) {
            candidates.addAll(reflections.getTypesAnnotatedWith(annotation));
        }

        for (Class<?> handlerClass : new ArrayList<>(candidates)) {
            Registry registry = handlerClass.getAnnotation(Registry.class);
            if (registry != null) {
                candidates.addAll(reflections.getTypesAnnotatedWith(registry.annotation()));
            }
        }

        for (Class<?> candidate : new ArrayList<>(candidates)) {
            for (Annotation annotation : candidate.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(Component.class)) {
                    candidates.addAll(reflections.getTypesAnnotatedWith(annotation.annotationType()));
                }
            }
        }

        return candidates;
    }

    private ConfigurationBuilder createConfiguration(Root rootAnnotation, Class<?> rootClass) {
        String rootPackage = getEffectivePackageName(rootAnnotation, rootClass);
        String rootPackagePath = rootPackage.replace('.', '/');
        String[] ignoredPackages = rootAnnotation.ignoredPackages();
        String[] includedPackages = rootAnnotation.includedPackages();

        ConfigurationBuilder builder = new ConfigurationBuilder();
        if (!rootPackage.isEmpty()) {
            builder.forPackage(rootPackage);
        }
        for (String includedPackage : includedPackages) {
            builder.forPackage(includedPackage);
        }

        builder.filterInputsBy(s -> {
            if (s == null || s.startsWith("META-INF") || !s.endsWith(".class")) {
                return false;
            }

            boolean isUnderRoot = rootPackage.isEmpty() || isClassInPackagePath(s, rootPackagePath);
            boolean isUnderIncluded = false;
            for (String includedPackage : includedPackages) {
                if (isClassInPackagePath(s, includedPackage.replace('.', '/'))) {
                    isUnderIncluded = true;
                    break;
                }
            }

            if (!isUnderRoot && !isUnderIncluded) {
                return false;
            }

            for (String ignoredPackage : ignoredPackages) {
                if (!isUnderIncluded && isClassInPackagePath(s, ignoredPackage.replace('.', '/'))) {
                    return false;
                }
            }

            return true;
        });

        return builder;
    }

    private boolean isClassInPackagePath(String classFilePath, String packagePath) {
        if (packagePath == null || packagePath.isEmpty()) {
            return true;
        }
        return classFilePath.startsWith(packagePath + "/") || classFilePath.equals(packagePath + ".class");
    }

    private String getEffectivePackageName(Root rootAnnotation, Class<?> rootClass) {
        String packageName = rootAnnotation.packageName();
        if (packageName != null && !packageName.isEmpty()) {
            return packageName;
        }
        Package pkg = rootClass.getPackage();
        if (pkg != null) {
            return pkg.getName();
        }
        String className = rootClass.getName();
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    private boolean isLoadable(Class<?> clazz, Predicate<Class<?>> loadPredicate, List<Diagnostic> diagnostics) {
        if (clazz == null || clazz.isAnnotation() || clazz.isEnum()) {
            return false;
        }
        try {
            return loadPredicate.test(clazz);
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.CONDITION_EVALUATION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    e.getMessage()
            ));
            return false;
        } catch (Throwable e) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.CONDITION_INSPECTION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName(),
                    e.getMessage()
            ));
            return false;
        }
    }

    private Discovery discover(List<Class<?>> candidates, VinjectAnalysisRequest request, List<Diagnostic> diagnostics) {
        Discovery discovery = new Discovery();

        for (Class<?> clazz : candidates) {
            if (clazz.isAnnotationPresent(Registry.class)) {
                discoverRegistryHandler(clazz, discovery, diagnostics);
            }
        }

        for (RegistryHandlerModel handler : discovery.registryHandlers) {
            for (Class<?> candidate : candidates) {
                if (candidate.isAnnotationPresent(handler.annotationClass())) {
                    RegistryTargetModel target = new RegistryTargetModel(
                            candidate,
                            handler.annotationClass(),
                            handler.handlerClass(),
                            handler.order()
                    );
                    discovery.registryTargets.add(target);
                    if (handler.order() == RegistryOrder.COMPONENTS) {
                        discovery.componentLoadClasses.add(candidate);
                        discovery.beans.add(new BeanModel(candidate, candidate, BeanKind.REGISTRY_TARGET, priorityOf(candidate), null, Collections.emptySet()));
                    }
                }
            }
        }

        if (request.getRootClass() != null) {
            discovery.beans.add(new BeanModel(request.getRootClass(), request.getRootClass(), BeanKind.ROOT, 0, null, Collections.emptySet()));
        }

        for (Class<?> clazz : candidates) {
            if (clazz.isAnnotationPresent(Service.class)) {
                discovery.serviceClasses.add(clazz);
                discovery.beans.add(new BeanModel(clazz, clazz, BeanKind.SERVICE, 0, null, Collections.emptySet()));
                discoverBeanMethods(clazz, discovery, diagnostics);
            }

            if (clazz.isAnnotationPresent(Repository.class)) {
                discovery.beans.add(new BeanModel(clazz, clazz, BeanKind.REPOSITORY, 0, null, Collections.emptySet()));
            }

            if (clazz.isAnnotationPresent(YamlConfiguration.class) || clazz.isAnnotationPresent(YamlDirectory.class)) {
                discovery.beans.add(new BeanModel(clazz, clazz, BeanKind.YAML_CONFIGURATION, 0, null, Collections.emptySet()));
            }

            if (isComponentLike(clazz, request)) {
                discovery.componentLoadClasses.add(clazz);
                discovery.beans.add(new BeanModel(clazz, clazz, BeanKind.COMPONENT, priorityOf(clazz), null, componentAliases(clazz, diagnostics)));
            }
        }

        for (Class<?> type : request.getPreRegisteredTypes()) {
            discovery.beans.add(new BeanModel(type, type, BeanKind.EXTERNAL, 0, null, Collections.emptySet()));
        }

        discovery.rebuildProviders();
        return discovery;
    }

    private void discoverRegistryHandler(Class<?> clazz, Discovery discovery, List<Diagnostic> diagnostics) {
        Registry registry = clazz.getAnnotation(Registry.class);
        if (!extendsByName(clazz, "net.vortexdevelopment.vinject.di.registry.AnnotationHandler")) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.INVALID_REGISTRY_HANDLER,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName()
            ));
            return;
        }
        discovery.registryHandlers.add(new RegistryHandlerModel(clazz, registry.annotation(), registry.order()));
    }

    private boolean extendsByName(Class<?> clazz, String typeName) {
        Class<?> current = clazz;
        while (current != null) {
            if (current.getName().equals(typeName)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private boolean isComponentLike(Class<?> clazz, VinjectAnalysisRequest request) {
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isEnum()) {
            return false;
        }
        if (clazz.isAnnotationPresent(Component.class)) {
            return true;
        }
        Root root = request.getRootAnnotation();
        if (root != null) {
            for (Class<? extends Annotation> annotation : root.componentAnnotations()) {
                if (clazz.isAnnotationPresent(annotation)) {
                    return true;
                }
            }
        }
        for (Annotation annotation : clazz.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Component.class)) {
                return true;
            }
        }
        return false;
    }

    private int priorityOf(Class<?> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            return component.priority();
        }
        for (Annotation annotation : clazz.getAnnotations()) {
            Component metaComponent = annotation.annotationType().getAnnotation(Component.class);
            if (metaComponent != null) {
                return metaComponent.priority();
            }
        }
        return 10;
    }

    private Set<Class<?>> componentAliases(Class<?> clazz, List<Diagnostic> diagnostics) {
        Component component = clazz.getAnnotation(Component.class);
        if (component == null) {
            return Collections.emptySet();
        }
        Set<Class<?>> aliases = new LinkedHashSet<>();
        for (Class<?> alias : component.registerSubclasses()) {
            if (!alias.isAssignableFrom(clazz)) {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.INVALID_COMPONENT_ALIAS,
                        DiagnosticLocation.classLocation(clazz),
                        alias.getName(),
                        clazz.getName()
                ));
            }
            aliases.add(alias);
        }
        return aliases;
    }

    private void discoverBeanMethods(Class<?> serviceClass, Discovery discovery, List<Diagnostic> diagnostics) {
        Set<Method> beanMethods = new LinkedHashSet<>();
        for (Method method : safeDeclaredMethods(serviceClass, diagnostics)) {
            if (method.isAnnotationPresent(Bean.class)) {
                beanMethods.add(method);
            }
        }
        if (!beanMethods.isEmpty() && !hasDefaultConstructor(serviceClass, diagnostics)) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.INVALID_BEAN_SERVICE_CONSTRUCTOR,
                    DiagnosticLocation.classLocation(serviceClass),
                    serviceClass.getName()
            ));
        }
        for (Method method : beanMethods) {
            if (method.getReturnType().equals(Void.TYPE)) {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.INVALID_BEAN_VOID_RETURN,
                        DiagnosticLocation.memberLocation(serviceClass, method.getName()),
                        method.getName()
                ));
                continue;
            }
            Bean bean = method.getAnnotation(Bean.class);
            Set<Class<?>> aliases = new LinkedHashSet<>();
            for (Class<?> alias : bean.registerSubclasses()) {
                if (!alias.isAssignableFrom(method.getReturnType())) {
                    diagnostics.add(Diagnostic.error(
                            DiagnosticCode.INVALID_BEAN_ALIAS,
                            DiagnosticLocation.memberLocation(serviceClass, method.getName()),
                            alias.getName(),
                            method.getReturnType().getName()
                    ));
                }
                aliases.add(alias);
            }
            discovery.beans.add(new BeanModel(method.getReturnType(), serviceClass, BeanKind.BEAN_METHOD, 0, method, aliases));
        }
    }

    private List<DependencyEdge> buildEdges(Discovery discovery, VinjectAnalysisRequest request, List<Diagnostic> diagnostics) {
        List<DependencyEdge> edges = new ArrayList<>();
        Set<Class<?>> inspectedClasses = new LinkedHashSet<>();
        inspectedClasses.addAll(discovery.serviceClasses);
        inspectedClasses.addAll(discovery.componentLoadClasses);

        for (Class<?> clazz : inspectedClasses) {
            inspectConstructor(clazz, discovery, request, edges, diagnostics);
            inspectFields(clazz, discovery, request, edges, diagnostics);
            inspectInjectMethods(clazz, discovery, request, edges, diagnostics);
            inspectPostConstruct(clazz, discovery, request, edges, diagnostics);
        }

        for (BeanModel bean : discovery.beans) {
            if (bean.kind() == BeanKind.BEAN_METHOD && bean.beanMethod() != null) {
                inspectExecutable(
                        bean.implementationClass(),
                        bean.beanMethod(),
                        DependencyEdgeKind.BEAN_METHOD,
                        bean.beanMethod().getName(),
                        true,
                        discovery,
                        request,
                        edges,
                        diagnostics
                );
            }
        }

        return edges;
    }

    private void inspectConstructor(Class<?> clazz, Discovery discovery, VinjectAnalysisRequest request, List<DependencyEdge> edges, List<Diagnostic> diagnostics) {
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isEnum() || hasDefaultConstructor(clazz, diagnostics)) {
            return;
        }
        Constructor<?>[] constructors = safeDeclaredConstructors(clazz, diagnostics);
        if (constructors.length == 0) {
            return;
        }
        inspectExecutable(clazz, constructors[0], DependencyEdgeKind.CONSTRUCTOR, "<init>", true, discovery, request, edges, diagnostics);
    }

    private void inspectFields(Class<?> clazz, Discovery discovery, VinjectAnalysisRequest request, List<DependencyEdge> edges, List<Diagnostic> diagnostics) {
        for (Field field : safeDeclaredFields(clazz, diagnostics)) {
            if (!field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            inspectDependency(
                    clazz,
                    field.getType(),
                    field.getName(),
                    DependencyEdgeKind.FIELD,
                    !field.isAnnotationPresent(OptionalDependency.class),
                    false,
                    discovery,
                    request,
                    edges,
                    diagnostics
            );
        }
    }

    private void inspectInjectMethods(Class<?> clazz, Discovery discovery, VinjectAnalysisRequest request, List<DependencyEdge> edges, List<Diagnostic> diagnostics) {
        for (Method method : safeDeclaredMethods(clazz, diagnostics)) {
            if (!method.isAnnotationPresent(Inject.class)) {
                continue;
            }
            inspectExecutable(clazz, method, DependencyEdgeKind.METHOD, method.getName(), false, discovery, request, edges, diagnostics);
        }
    }

    private void inspectPostConstruct(Class<?> clazz, Discovery discovery, VinjectAnalysisRequest request, List<DependencyEdge> edges, List<Diagnostic> diagnostics) {
        for (Method method : safeDeclaredMethods(clazz, diagnostics)) {
            if (!method.isAnnotationPresent(PostConstruct.class)) {
                continue;
            }
            inspectExecutable(clazz, method, DependencyEdgeKind.POST_CONSTRUCT, method.getName(), true, discovery, request, edges, diagnostics);
        }
    }

    private void inspectExecutable(
            Class<?> source,
            Executable executable,
            DependencyEdgeKind kind,
            String memberName,
            boolean hard,
            Discovery discovery,
            VinjectAnalysisRequest request,
            List<DependencyEdge> edges,
            List<Diagnostic> diagnostics
    ) {
        Parameter[] parameters = executable.getParameters();
        Annotation[][] parameterAnnotations = executable.getParameterAnnotations();
        for (int i = 0; i < parameters.length; i++) {
            if (hasAnnotation(parameterAnnotations[i], Value.class)) {
                continue;
            }
            boolean required = !hasAnnotation(parameterAnnotations[i], OptionalDependency.class);
            inspectDependency(source, parameters[i].getType(), memberName, kind, required, hard, discovery, request, edges, diagnostics);
        }
    }

    private void inspectDependency(
            Class<?> source,
            Class<?> requestedType,
            String memberName,
            DependencyEdgeKind kind,
            boolean required,
            boolean hard,
            Discovery discovery,
            VinjectAnalysisRequest request,
            List<DependencyEdge> edges,
            List<Diagnostic> diagnostics
    ) {
        if (isPreRegistered(requestedType, request)) {
            return;
        }

        List<BeanModel> providers = discovery.providers.getOrDefault(requestedType, Collections.emptyList());
        if (providers.isEmpty()) {
            DiagnosticLocation location = DiagnosticLocation.memberLocation(source, memberName);
            if (required) {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.MISSING_DEPENDENCY,
                        location,
                        requestedType.getName(),
                        source.getName()
                ));
            } else {
                diagnostics.add(Diagnostic.warning(
                        DiagnosticCode.OPTIONAL_DEPENDENCY_UNRESOLVED,
                        location,
                        requestedType.getName(),
                        source.getName()
                ));
            }
            return;
        }

        if (isYamlConfigurationClass(source)) {
            List<BeanModel> lateProviders = providers.stream()
                    .filter(provider -> isLateYamlProvider(provider.kind()))
                    .toList();
            if (!lateProviders.isEmpty()) {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.YAML_EARLY_LOAD_DEPENDENCY,
                        DiagnosticLocation.memberLocation(source, memberName),
                        requestedType.getName(),
                        source.getName(),
                        lateProviders.stream()
                                .map(provider -> provider.implementationClass().getName() + " (" + provider.kind() + ")")
                                .collect(Collectors.joining(", "))
                ));
                return;
            }
        }

        Set<Class<?>> distinctProviders = providers.stream()
                .map(BeanModel::implementationClass)
                .collect(Collectors.toSet());
        if (distinctProviders.size() > 1) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.AMBIGUOUS_DEPENDENCY,
                    DiagnosticLocation.memberLocation(source, memberName),
                    requestedType.getName(),
                    source.getName(),
                    distinctProviders.stream().map(Class::getName).collect(Collectors.joining(", "))
            ));
            return;
        }

        BeanModel provider = providers.get(0);
        Class<?> target = provider.implementationClass();
        if (!source.equals(target)) {
            edges.add(new DependencyEdge(source, target, kind, memberName, required, hard));
        }
    }

    private boolean isYamlConfigurationClass(Class<?> source) {
        return source.isAnnotationPresent(YamlConfiguration.class) || source.isAnnotationPresent(YamlDirectory.class);
    }

    private boolean isLateYamlProvider(BeanKind kind) {
        return kind == BeanKind.COMPONENT
                || kind == BeanKind.SERVICE
                || kind == BeanKind.BEAN_METHOD
                || kind == BeanKind.REGISTRY_TARGET
                || kind == BeanKind.REPOSITORY;
    }

    private boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreRegistered(Class<?> type, VinjectAnalysisRequest request) {
        return request.getPreRegisteredTypes().contains(type)
                || request.getPreRegisteredTypeNames().contains(type.getName())
                || BUILT_IN_TYPES.contains(type.getName());
    }

    private boolean hasDefaultConstructor(Class<?> clazz, List<Diagnostic> diagnostics) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.ANALYZER_DEFAULT_CONSTRUCTOR_INSPECTION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName(),
                    e.getMessage()
            ));
            return true;
        }
    }

    private Method[] safeDeclaredMethods(Class<?> clazz, List<Diagnostic> diagnostics) {
        try {
            return clazz.getDeclaredMethods();
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.ANALYZER_METHOD_INSPECTION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName(),
                    e.getMessage()
            ));
            return new Method[0];
        }
    }

    private Field[] safeDeclaredFields(Class<?> clazz, List<Diagnostic> diagnostics) {
        try {
            return clazz.getDeclaredFields();
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.ANALYZER_FIELD_INSPECTION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName(),
                    e.getMessage()
            ));
            return new Field[0];
        }
    }

    private Constructor<?>[] safeDeclaredConstructors(Class<?> clazz, List<Diagnostic> diagnostics) {
        try {
            return clazz.getDeclaredConstructors();
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.ANALYZER_CONSTRUCTOR_INSPECTION_FAILED,
                    DiagnosticLocation.classLocation(clazz),
                    clazz.getName(),
                    e.getMessage()
            ));
            return new Constructor<?>[0];
        }
    }

    private List<Class<?>> sortByDependencies(Collection<Class<?>> rawNodes, List<DependencyEdge> edges, List<Diagnostic> diagnostics) {
        Set<Class<?>> nodes = new LinkedHashSet<>(rawNodes);
        Map<Class<?>, List<DependencyEdge>> bySource = new LinkedHashMap<>();
        for (DependencyEdge edge : edges) {
            if (nodes.contains(edge.source()) && nodes.contains(edge.target())) {
                bySource.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            }
        }

        List<Class<?>> orderedNodes = new ArrayList<>(nodes);
        orderedNodes.sort(Comparator.comparingInt(this::priorityOf).thenComparing(Class::getName));

        List<Class<?>> sorted = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        ArrayDeque<Class<?>> visiting = new ArrayDeque<>();

        for (Class<?> node : orderedNodes) {
            visit(node, bySource, visited, visiting, sorted, diagnostics);
        }
        return sorted;
    }

    private void visit(
            Class<?> node,
            Map<Class<?>, List<DependencyEdge>> bySource,
            Set<Class<?>> visited,
            ArrayDeque<Class<?>> visiting,
            List<Class<?>> sorted,
            List<Diagnostic> diagnostics
    ) {
        if (visited.contains(node)) {
            return;
        }
        if (visiting.contains(node)) {
            return;
        }

        visiting.addLast(node);
        List<DependencyEdge> dependencies = new ArrayList<>(bySource.getOrDefault(node, Collections.emptyList()));
        dependencies.sort(Comparator.comparing(edge -> edge.target().getName()));
        for (DependencyEdge edge : dependencies) {
            if (visiting.contains(edge.target())) {
                reportCycle(edge, visiting, diagnostics);
                continue;
            }
            visit(edge.target(), bySource, visited, visiting, sorted, diagnostics);
        }
        visiting.removeLast();
        visited.add(node);
        sorted.add(node);
    }

    private void reportCycle(DependencyEdge closingEdge, ArrayDeque<Class<?>> visiting, List<Diagnostic> diagnostics) {
        List<Class<?>> stack = new ArrayList<>(visiting);
        int start = stack.indexOf(closingEdge.target());
        List<Class<?>> cycle = start >= 0 ? stack.subList(start, stack.size()) : stack;
        String path = cycle.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "))
                + " -> " + closingEdge.target().getSimpleName();

        if (closingEdge.hard()) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.HARD_DEPENDENCY_CYCLE,
                    DiagnosticLocation.classLocation(closingEdge.source()),
                    path
            ));
        } else {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.DEFERRED_DEPENDENCY_CYCLE,
                    DiagnosticLocation.classLocation(closingEdge.source()),
                    path
            ));
        }
    }

    private static final class Discovery {
        private final Set<BeanModel> beans = new LinkedHashSet<>();
        private final Set<Class<?>> serviceClasses = new LinkedHashSet<>();
        private final Set<Class<?>> componentLoadClasses = new LinkedHashSet<>();
        private final List<RegistryHandlerModel> registryHandlers = new ArrayList<>();
        private final List<RegistryTargetModel> registryTargets = new ArrayList<>();
        private final Map<Class<?>, List<BeanModel>> providers = new HashMap<>();

        private void rebuildProviders() {
            providers.clear();
            for (BeanModel bean : beans) {
                for (Class<?> providedType : bean.getProvidedTypes()) {
                    providers.computeIfAbsent(providedType, ignored -> new ArrayList<>()).add(bean);
                }
            }
            providers.values().forEach(list -> list.removeIf(Objects::isNull));
        }
    }
}
