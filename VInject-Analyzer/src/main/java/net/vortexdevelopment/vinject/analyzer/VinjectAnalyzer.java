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
import net.vortexdevelopment.vinject.annotation.Qualifier;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.util.TypeHierarchyUtils;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.ForeignKey;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.Index;
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

        validateDatabaseAnnotations(loadableCandidates, diagnostics);

        Discovery discovery = discover(loadableCandidates, request, diagnostics);
        List<DependencyEdge> edges = buildEdges(discovery, request, diagnostics);

        List<Class<?>> serviceLoadOrder = sortByDependencies(discovery.serviceClasses, edges, diagnostics);
        List<Class<?>> componentLoadOrder = sortByDependencies(discovery.componentLoadClasses, edges, diagnostics);

        ApplicationModel applicationModel = new ApplicationModel(discovery.beans, discovery.registryHandlers, discovery.registryTargets);
        DependencyGraph dependencyGraph = new DependencyGraph(edges);
        DependencyLoadPlan loadPlan = new DependencyLoadPlan(serviceLoadOrder, componentLoadOrder);
        return new VinjectAnalysisResult(applicationModel, dependencyGraph, loadPlan, diagnostics);
    }

    private void validateDatabaseAnnotations(Collection<Class<?>> candidates, List<Diagnostic> diagnostics) {
        for (Class<?> candidate : candidates) {
            boolean entity = candidate.isAnnotationPresent(Entity.class);
            Index[] classIndexes = candidate.getAnnotationsByType(Index.class);
            if (!entity && classIndexes.length > 0) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.INDEX_NOT_ON_ENTITY,
                        DiagnosticLocation.classLocation(candidate), candidate.getName()));
            }
            if (entity) {
                validateEntityIndexes(candidate, classIndexes, diagnostics);
            }

            for (Field field : safeDeclaredFields(candidate, diagnostics)) {
                Index[] fieldIndexes = field.getAnnotationsByType(Index.class);
                if (fieldIndexes.length > 0) {
                    if (!entity) {
                        diagnostics.add(Diagnostic.error(DiagnosticCode.INDEX_NOT_ON_ENTITY,
                                DiagnosticLocation.memberLocation(candidate, field.getName()), describe(field)));
                    } else {
                        validateFieldIndexes(candidate, field, fieldIndexes, diagnostics);
                    }
                }
                ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
                if (foreignKey != null) {
                    validateForeignKey(candidate, field, foreignKey, entity, diagnostics);
                }
            }
        }
        validateForeignKeyDeleteCycles(candidates, diagnostics);
    }

    private void validateForeignKeyDeleteCycles(Collection<Class<?>> candidates, List<Diagnostic> diagnostics) {
        List<ForeignKeyDeleteEdge> edges = collectForeignKeyDeleteEdges(candidates, diagnostics);
        Map<Class<?>, List<ForeignKeyDeleteEdge>> outgoing = new LinkedHashMap<>();
        Set<Class<?>> nodes = new LinkedHashSet<>();
        for (ForeignKeyDeleteEdge edge : edges) {
            nodes.add(edge.owner());
            nodes.add(edge.target());
            outgoing.computeIfAbsent(edge.owner(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoing.values().forEach(list -> list.sort(Comparator
                .comparing((ForeignKeyDeleteEdge edge) -> edge.target().getName())
                .thenComparing(edge -> edge.field().getName())));

        Map<Class<?>, Integer> indexes = new HashMap<>();
        Map<Class<?>, Integer> lowLinks = new HashMap<>();
        ArrayDeque<Class<?>> stack = new ArrayDeque<>();
        Set<Class<?>> onStack = new HashSet<>();
        int[] nextIndex = {0};

        nodes.stream().sorted(Comparator.comparing(Class::getName)).forEach(node -> {
            if (!indexes.containsKey(node)) {
                findForeignKeyComponents(node, outgoing, indexes, lowLinks, stack, onStack,
                        nextIndex, diagnostics);
            }
        });
    }

    private List<ForeignKeyDeleteEdge> collectForeignKeyDeleteEdges(Collection<Class<?>> candidates,
                                                                    List<Diagnostic> diagnostics) {
        List<ForeignKeyDeleteEdge> edges = new ArrayList<>();
        ArrayDeque<Class<?>> pending = candidates.stream()
                .filter(candidate -> candidate.isAnnotationPresent(Entity.class))
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toCollection(ArrayDeque::new));
        Set<Class<?>> visited = new HashSet<>();

        while (!pending.isEmpty()) {
            Class<?> owner = pending.removeFirst();
            if (!visited.add(owner)) continue;
            for (Field field : safeDeclaredFields(owner, diagnostics)) {
                ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
                if (foreignKey == null || !isPersistentField(field)) continue;
                Class<?> target = foreignKey.entity() == void.class ? field.getType() : foreignKey.entity();
                if (!target.isAnnotationPresent(Entity.class)) continue;
                if (!visited.contains(target)) pending.addLast(target);

                // SET_NULL breaks the delete chain. Self references are valid for trees such as parent_id.
                if (owner.equals(target) || foreignKey.onDelete() == ForeignKeyAction.SET_NULL) continue;
                edges.add(new ForeignKeyDeleteEdge(owner, target, field, foreignKey.onDelete()));
            }
        }
        return edges;
    }

    private void findForeignKeyComponents(
            Class<?> node,
            Map<Class<?>, List<ForeignKeyDeleteEdge>> outgoing,
            Map<Class<?>, Integer> indexes,
            Map<Class<?>, Integer> lowLinks,
            ArrayDeque<Class<?>> stack,
            Set<Class<?>> onStack,
            int[] nextIndex,
            List<Diagnostic> diagnostics
    ) {
        int index = nextIndex[0]++;
        indexes.put(node, index);
        lowLinks.put(node, index);
        stack.push(node);
        onStack.add(node);

        for (ForeignKeyDeleteEdge edge : outgoing.getOrDefault(node, Collections.emptyList())) {
            Class<?> target = edge.target();
            if (!indexes.containsKey(target)) {
                findForeignKeyComponents(target, outgoing, indexes, lowLinks, stack, onStack,
                        nextIndex, diagnostics);
                lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(target)));
            } else if (onStack.contains(target)) {
                lowLinks.put(node, Math.min(lowLinks.get(node), indexes.get(target)));
            }
        }

        if (!lowLinks.get(node).equals(indexes.get(node))) return;
        Set<Class<?>> component = new LinkedHashSet<>();
        Class<?> member;
        do {
            member = stack.pop();
            onStack.remove(member);
            component.add(member);
        } while (!member.equals(node));
        if (component.size() < 2) return;

        List<ForeignKeyDeleteEdge> cycleEdges = outgoing.values().stream()
                .flatMap(Collection::stream)
                .filter(edge -> component.contains(edge.owner()) && component.contains(edge.target()))
                .sorted(Comparator.comparing((ForeignKeyDeleteEdge edge) -> edge.owner().getName())
                        .thenComparing(edge -> edge.field().getName()))
                .toList();
        ForeignKeyDeleteEdge locationEdge = cycleEdges.stream()
                .filter(edge -> edge.action() == ForeignKeyAction.RESTRICT
                        || edge.action() == ForeignKeyAction.NO_ACTION)
                .findFirst()
                .orElse(cycleEdges.get(0));
        String entities = component.stream()
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.joining(" <-> "));
        DiagnosticLocation location = DiagnosticLocation.memberLocation(
                locationEdge.owner(), locationEdge.field().getName());

        if (cycleEdges.stream().anyMatch(edge -> edge.action() == ForeignKeyAction.RESTRICT
                || edge.action() == ForeignKeyAction.NO_ACTION)) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.FOREIGN_KEY_RESTRICTIVE_DELETE_CYCLE, location, entities));
        } else {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.FOREIGN_KEY_CASCADE_DELETE_CYCLE, location, entities));
        }
    }

    private void validateEntityIndexes(Class<?> entity, Index[] indexes, List<Diagnostic> diagnostics) {
        Set<String> indexNames = new HashSet<>();
        for (Index index : indexes) {
            if (index.columns().length == 0) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.INVALID_INDEX_DECLARATION,
                        DiagnosticLocation.classLocation(entity),
                        "Class-level @Index must declare at least one column on " + entity.getName()));
                continue;
            }
            validateIndexColumns(entity, index, DiagnosticLocation.classLocation(entity), diagnostics);
            validateIndexName(entity, index, indexNames, diagnostics);
        }
        for (Field field : safeDeclaredFields(entity, diagnostics)) {
            for (Index index : field.getAnnotationsByType(Index.class)) {
                validateIndexName(entity, index, indexNames, diagnostics);
            }
        }
    }

    private void validateFieldIndexes(Class<?> entity, Field field, Index[] indexes, List<Diagnostic> diagnostics) {
        DiagnosticLocation location = DiagnosticLocation.memberLocation(entity, field.getName());
        if (!isPersistentField(field)) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.INVALID_INDEX_DECLARATION, location,
                    "Field-level @Index requires @Column, @Id, or @Temporal on " + describe(field)));
        }
        for (Index index : indexes) {
            if (index.columns().length > 0) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.INVALID_INDEX_DECLARATION, location,
                        "Field-level @Index must not declare columns on " + describe(field)));
            }
        }
    }

    private void validateIndexColumns(Class<?> entity, Index index, DiagnosticLocation location,
                                      List<Diagnostic> diagnostics) {
        Set<Field> resolved = new HashSet<>();
        for (String requested : index.columns()) {
            Field field = resolveEntityField(entity, requested);
            if (field == null) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.UNKNOWN_INDEX_COLUMN, location,
                        requested, entity.getName()));
            } else if (!resolved.add(field)) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.DUPLICATE_INDEX_COLUMN, location,
                        requested, entity.getName()));
            }
        }
    }

    private void validateIndexName(Class<?> entity, Index index, Set<String> names,
                                   List<Diagnostic> diagnostics) {
        if (index.name().isBlank()) return;
        String normalized = index.name().toLowerCase(java.util.Locale.ENGLISH);
        if (!names.add(normalized)) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.DUPLICATE_INDEX_NAME,
                    DiagnosticLocation.classLocation(entity), index.name(), entity.getName()));
        }
    }

    private void validateForeignKey(Class<?> owner, Field field, ForeignKey foreignKey, boolean ownerIsEntity,
                                    List<Diagnostic> diagnostics) {
        DiagnosticLocation location = DiagnosticLocation.memberLocation(owner, field.getName());
        if (!ownerIsEntity || !isPersistentField(field)) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_NOT_ON_ENTITY, location, describe(field)));
            return;
        }

        Class<?> targetEntity = foreignKey.entity();
        if (targetEntity == void.class) {
            if (field.getType().isAnnotationPresent(Entity.class)) {
                targetEntity = field.getType();
            } else {
                diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_ENTITY_REQUIRED, location, describe(field)));
                return;
            }
        }
        if (!targetEntity.isAnnotationPresent(Entity.class)) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_TARGET_NOT_ENTITY, location,
                    targetEntity.getName()));
            return;
        }

        Field targetField = foreignKey.referencedColumn().isBlank()
                ? findPrimaryKey(targetEntity)
                : resolveEntityField(targetEntity, foreignKey.referencedColumn());
        if (targetField == null) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_TARGET_COLUMN_UNKNOWN, location,
                    foreignKey.referencedColumn().isBlank() ? "<primary key>" : foreignKey.referencedColumn(),
                    targetEntity.getName()));
            return;
        }
        Column targetColumn = targetField.getAnnotation(Column.class);
        boolean targetIsKey = targetField.isAnnotationPresent(Id.class)
                || (targetColumn != null && (targetColumn.primaryKey() || targetColumn.unique()));
        if (!targetIsKey) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_TARGET_NOT_KEY, location,
                    describe(targetField)));
        }

        if (!field.getType().equals(targetEntity)
                && !boxed(field.getType()).equals(boxed(targetField.getType()))) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_TYPE_MISMATCH, location,
                    describe(field), field.getType().getName(), describe(targetField), targetField.getType().getName()));
        }

        if ((foreignKey.onDelete() == ForeignKeyAction.SET_NULL || foreignKey.onUpdate() == ForeignKeyAction.SET_NULL)
                && !isNullable(field)) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.FOREIGN_KEY_SET_NULL_NOT_NULLABLE, location,
                    describe(field)));
        }
    }

    private Field resolveEntityField(Class<?> entity, String name) {
        for (Field field : entity.getDeclaredFields()) {
            if (!isPersistentField(field)) continue;
            if (field.getName().equals(name) || physicalColumnName(field).equals(name)) return field;
        }
        return null;
    }

    private Field findPrimaryKey(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (field.isAnnotationPresent(Id.class) || (column != null && column.primaryKey())) return field;
        }
        return null;
    }

    private boolean isPersistentField(Field field) {
        return field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)
                || hasAnnotation(field, "net.vortexdevelopment.vinject.annotation.database.Temporal");
    }

    private String physicalColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) return column.name();
        String temporalName = annotationStringValue(field,
                "net.vortexdevelopment.vinject.annotation.database.Temporal", "name");
        return temporalName == null || temporalName.isBlank() ? field.getName() : temporalName;
    }

    private boolean isNullable(Field field) {
        if (field.isAnnotationPresent(Id.class)) return false;
        Column column = field.getAnnotation(Column.class);
        if (column != null) return column.nullable();
        Boolean temporalNullable = annotationBooleanValue(field,
                "net.vortexdevelopment.vinject.annotation.database.Temporal", "nullable");
        return temporalNullable == null || temporalNullable;
    }

    private boolean hasAnnotation(Field field, String annotationName) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationName)) return true;
        }
        return false;
    }

    private String annotationStringValue(Field field, String annotationName, String method) {
        Object value = annotationValue(field, annotationName, method);
        return value instanceof String string ? string : null;
    }

    private Boolean annotationBooleanValue(Field field, String annotationName, String method) {
        Object value = annotationValue(field, annotationName, method);
        return value instanceof Boolean bool ? bool : null;
    }

    private Object annotationValue(Field field, String annotationName, String method) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (!annotation.annotationType().getName().equals(annotationName)) continue;
            try {
                return annotation.annotationType().getMethod(method).invoke(annotation);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private String describe(Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName();
    }

    private Set<Class<?>> collectCandidates(VinjectAnalysisRequest request, List<Diagnostic> diagnostics) {
        Set<Class<?>> candidates = new LinkedHashSet<>(request.getCandidateClasses());

        Root root = request.getRootAnnotation();
        Class<?> rootClass = request.getRootClass();
        if (root == null || rootClass == null) {
            if (candidates.isEmpty()) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.ANALYZER_MISSING_INPUT));
            }
            return candidates;
        }

        Reflections reflections = new Reflections(createConfiguration(root, rootClass));
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
        ClassLoader classLoader = rootClass.getClassLoader();

        ConfigurationBuilder builder = new ConfigurationBuilder();
        if (!rootPackage.isEmpty()) {
            builder.forPackage(rootPackage, classLoader);
        }
        for (String includedPackage : includedPackages) {
            builder.forPackage(includedPackage, classLoader);
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
                discovery.beans.add(new BeanModel(
                        clazz,
                        clazz,
                        BeanKind.COMPONENT,
                        priorityOf(clazz),
                        null,
                        providedTypes(clazz),
                        resolveBeanName(clazz)
                ));
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

    private Set<Class<?>> providedTypes(Class<?> clazz) {
        return new LinkedHashSet<>(TypeHierarchyUtils.collectRegistrationTypes(clazz));
    }

    private String resolveBeanName(Class<?> clazz) {
        Qualifier qualifier = clazz.getAnnotation(Qualifier.class);
        if (qualifier != null && !qualifier.value().isEmpty()) {
            return qualifier.value();
        }
        Component component = clazz.getAnnotation(Component.class);
        if (component != null && !component.name().isEmpty()) {
            return component.name();
        }
        return "";
    }

    private String resolveBeanMethodName(Method method) {
        Qualifier qualifier = method.getAnnotation(Qualifier.class);
        if (qualifier != null && !qualifier.value().isEmpty()) {
            return qualifier.value();
        }
        Bean bean = method.getAnnotation(Bean.class);
        if (bean != null && !bean.name().isEmpty()) {
            return bean.name();
        }
        return "";
    }

    private String extractQualifier(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier qualifier && !qualifier.value().isEmpty()) {
                return qualifier.value();
            }
        }
        return "";
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
            Set<Class<?>> aliases = new LinkedHashSet<>(
                    TypeHierarchyUtils.collectRegistrationTypes(method.getReturnType()));
            discovery.beans.add(new BeanModel(
                    method.getReturnType(),
                    serviceClass,
                    BeanKind.BEAN_METHOD,
                    0,
                    method,
                    aliases,
                    resolveBeanMethodName(method)
            ));
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
                    extractQualifier(field.getAnnotations()),
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
            inspectDependency(
                    source,
                    parameters[i].getType(),
                    memberName,
                    kind,
                    required,
                    hard,
                    extractQualifier(parameterAnnotations[i]),
                    discovery,
                    request,
                    edges,
                    diagnostics
            );
        }
    }

    private void inspectDependency(
            Class<?> source,
            Class<?> requestedType,
            String memberName,
            DependencyEdgeKind kind,
            boolean required,
            boolean hard,
            String qualifierName,
            Discovery discovery,
            VinjectAnalysisRequest request,
            List<DependencyEdge> edges,
            List<Diagnostic> diagnostics
    ) {
        if (isPreRegistered(requestedType, request)) {
            return;
        }

        List<BeanModel> providers = discovery.providers.getOrDefault(requestedType, Collections.emptyList());
        if (!qualifierName.isEmpty()) {
            providers = providers.stream()
                    .filter(provider -> qualifierName.equals(provider.qualifierName()))
                    .toList();
            if (providers.isEmpty()) {
                DiagnosticLocation location = DiagnosticLocation.memberLocation(source, memberName);
                if (required) {
                    diagnostics.add(Diagnostic.error(
                            DiagnosticCode.MISSING_NAMED_DEPENDENCY,
                            location,
                            qualifierName,
                            requestedType.getName(),
                            source.getName()
                    ));
                } else {
                    diagnostics.add(Diagnostic.warning(
                            DiagnosticCode.OPTIONAL_DEPENDENCY_UNRESOLVED,
                            location,
                            requestedType.getName() + " (qualifier: " + qualifierName + ")",
                            source.getName()
                    ));
                }
                return;
            }
        }
        List<BeanModel> exactProviders = providers.stream()
                .filter(provider -> provider.beanType().equals(requestedType))
                .toList();
        if (!exactProviders.isEmpty()) {
            providers = exactProviders;
        }
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

    private record ForeignKeyDeleteEdge(Class<?> owner, Class<?> target, Field field,
                                        ForeignKeyAction action) {
    }
}
