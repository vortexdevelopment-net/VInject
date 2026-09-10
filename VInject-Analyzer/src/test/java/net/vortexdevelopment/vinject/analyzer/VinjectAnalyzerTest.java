package net.vortexdevelopment.vinject.analyzer;

import net.vortexdevelopment.vinject.analyzer.diagnostic.DiagnosticSeverity;
import net.vortexdevelopment.vinject.analyzer.model.BeanKind;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Qualifier;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.ForeignKey;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.Index;
import net.vortexdevelopment.vinject.analyzer.fixtures.included.IncludedPackageComponent;
import net.vortexdevelopment.vinject.analyzer.fixtures.root.IncludedPackageRoot;
import net.vortexdevelopment.vinject.analyzer.fixtures.root.RootPackageComponent;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VinjectAnalyzerTest {

    @Test
    void ordersTransitiveFieldDependencies() {
        VinjectAnalysisResult result = analyze(ComponentA.class, ComponentB.class, ComponentC.class);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.loadPlan().componentLoadOrder())
                .containsSubsequence(ComponentC.class, ComponentB.class, ComponentA.class);
    }

    @Test
    void reportsMissingRequiredDependency() {
        VinjectAnalysisResult result = analyze(MissingDependencyConsumer.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DEP-001")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsAmbiguousDependencyProviders() {
        VinjectAnalysisResult result = analyze(AmbiguousConsumer.class, AmbiguousProviderA.class, AmbiguousProviderB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DEP-002")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void autoRegistersInterface() {
        VinjectAnalysisResult result = analyze(AutoPortConsumer.class, AutoPortImpl.class);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void exactConcreteTypeWinsWhenInterfaceIsAmbiguous() {
        VinjectAnalysisResult result = analyze(
                ConcreteConsumer.class,
                AmbiguousProviderA.class,
                AmbiguousProviderB.class
        );

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void autoRegistersParentInterfacesAndSuperclasses() {
        VinjectAnalysisResult result = analyze(ParentConsumer.class, ParentPortImpl.class, DerivedComponent.class);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void autoRegistersBeanReturnTypeInterfaces() {
        VinjectAnalysisResult result = analyze(BeanInterfaceService.class, BeanPortConsumer.class, ComponentC.class);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void resolvesQualifierWhenMultipleProvidersExist() {
        VinjectAnalysisResult result = analyze(
                NamedPortConsumer.class,
                NamedPortProviderA.class,
                NamedPortProviderB.class
        );

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void reportsMissingNamedDependency() {
        VinjectAnalysisResult result = analyze(MissingNamedConsumer.class, NamedPortProviderA.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DEP-005")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsConstructorDependencyCycleAsError() {
        VinjectAnalysisResult result = analyze(ConstructorCycleA.class, ConstructorCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-CYCLE-001")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsFieldDependencyCycleAsWarning() {
        VinjectAnalysisResult result = analyze(FieldCycleA.class, FieldCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-CYCLE-002")
                        && diagnostic.severity() == DiagnosticSeverity.WARNING);
    }

    @Test
    void discoversBeanMethodProvidersAndValidatesVoidReturn() {
        VinjectAnalysisResult result = analyze(BeanService.class, ComponentC.class);

        assertThat(result.applicationModel().beans())
                .anyMatch(bean -> bean.beanType().equals(ProducedBean.class)
                        && bean.kind() == BeanKind.BEAN_METHOD);
        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-BEAN-001")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void discoversRegistryTargets() {
        VinjectAnalysisResult result = analyze(RegistryHandler.class, RegistryTarget.class);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.applicationModel().registryTargets())
                .anyMatch(target -> target.targetClass().equals(RegistryTarget.class)
                        && target.handlerClass().equals(RegistryHandler.class));
        assertThat(result.loadPlan().componentLoadOrder()).contains(RegistryTarget.class);
    }

    @Test
    void rootScanningHonorsIncludedPackages() {
        Root root = IncludedPackageRoot.class.getAnnotation(Root.class);

        VinjectAnalysisResult result = new VinjectAnalyzer().analyze(VinjectAnalysisRequest.builder()
                .root(root, IncludedPackageRoot.class)
                .build());

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.loadPlan().componentLoadOrder()).contains(IncludedPackageComponent.class);
    }

    @Test
    void rootScanningMergesWithExplicitCandidateClasses() {
        Root root = IncludedPackageRoot.class.getAnnotation(Root.class);

        VinjectAnalysisResult result = new VinjectAnalyzer().analyze(VinjectAnalysisRequest.builder()
                .candidateClasses(Set.of(IncludedPackageRoot.class))
                .root(root, IncludedPackageRoot.class)
                .build());

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.loadPlan().componentLoadOrder())
                .contains(RootPackageComponent.class, IncludedPackageComponent.class);
    }

    @Test
    void acceptsJavaFieldAndPhysicalColumnNamesInIndexesAndForeignKeys() {
        VinjectAnalysisResult result = analyze(ValidIsland.class, ValidUpgrade.class, ValidPlayer.class);

        assertThat(result.diagnostics())
                .noneMatch(diagnostic -> diagnostic.code().startsWith("VINJECT-DB-"));
    }

    @Test
    void reportsMisspelledIndexAndForeignKeyColumns() {
        VinjectAnalysisResult result = analyze(ValidIsland.class, InvalidDatabaseReferences.class);

        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-003"));
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-009"));
    }

    @Test
    void reportsSetNullOnRequiredForeignKeyAndTypeMismatch() {
        VinjectAnalysisResult result = analyze(ValidIsland.class, InvalidForeignKeyShape.class);

        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-011"));
        assertThat(result.diagnostics()).anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-012"));
    }

    @Test
    void rejectsRestrictiveForeignKeyDeleteCycle() {
        VinjectAnalysisResult result = analyze(RestrictiveCycleA.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-013")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void rejectsMixedRestrictAndCascadeDeleteCycle() {
        VinjectAnalysisResult result = analyze(MixedCycleA.class, MixedCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-013")
                        && diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void warnsAboutCascadeOnlyDeleteCycle() {
        VinjectAnalysisResult result = analyze(CascadeCycleA.class, CascadeCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-014")
                        && diagnostic.severity() == DiagnosticSeverity.WARNING);
        assertThat(result.diagnostics()).noneMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-013"));
    }

    @Test
    void setNullBreaksForeignKeyDeleteCycle() {
        VinjectAnalysisResult result = analyze(SetNullCycleA.class, SetNullCycleB.class);

        assertThat(result.diagnostics())
                .noneMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-013")
                        || diagnostic.code().equals("VINJECT-DB-014"));
    }

    @Test
    void permitsSelfReferencingHierarchy() {
        VinjectAnalysisResult result = analyze(SelfReferencingEntity.class);

        assertThat(result.diagnostics())
                .noneMatch(diagnostic -> diagnostic.code().equals("VINJECT-DB-013")
                        || diagnostic.code().equals("VINJECT-DB-014"));
    }

    private VinjectAnalysisResult analyze(Class<?>... classes) {
        return new VinjectAnalyzer().analyze(VinjectAnalysisRequest.builder()
                .candidateClasses(Set.of(classes))
                .build());
    }

    @Component
    static class ComponentA {
        @Inject ComponentB componentB;
    }

    @Component
    static class ComponentB {
        @Inject ComponentC componentC;
    }

    @Component
    static class ComponentC {
    }

    @Component
    static class MissingDependencyConsumer {
        @Inject MissingDependency missingDependency;
    }

    static class MissingDependency {
    }

    interface AmbiguousPort {
    }

    @Component
    static class AmbiguousProviderA implements AmbiguousPort {
    }

    @Component
    static class AmbiguousProviderB implements AmbiguousPort {
    }

    @Component
    static class AmbiguousConsumer {
        @Inject AmbiguousPort port;
    }

    @Component
    static class ConcreteConsumer {
        @Inject AmbiguousProviderA provider;
    }

    interface AutoPort {
    }

    @Component
    static class AutoPortImpl implements AutoPort {
    }

    @Component
    static class AutoPortConsumer {
        @Inject AutoPort port;
    }

    interface ParentPort {
    }

    interface ChildPort extends ParentPort {
    }

    @Component
    static class ParentPortImpl implements ChildPort {
    }

    @Component
    static class BaseComponent {
    }

    @Component
    static class DerivedComponent extends BaseComponent {
    }

    @Component
    static class ParentConsumer {
        @Inject ParentPort parentPort;
        @Inject BaseComponent baseComponent;
    }

    @Component(name = "portA")
    static class NamedPortProviderA implements AmbiguousPort {
    }

    @Qualifier("portB")
    @Component
    static class NamedPortProviderB implements AmbiguousPort {
    }

    @Component
    static class NamedPortConsumer {
        @Inject
        @Qualifier("portB")
        AmbiguousPort port;
    }

    @Component
    static class MissingNamedConsumer {
        @Inject
        @Qualifier("missing")
        AmbiguousPort port;
    }

    @Component
    static class ConstructorCycleA {
        ConstructorCycleA(ConstructorCycleB b) {
        }
    }

    @Component
    static class ConstructorCycleB {
        ConstructorCycleB(ConstructorCycleA a) {
        }
    }

    @Component
    static class FieldCycleA {
        @Inject FieldCycleB b;
    }

    @Component
    static class FieldCycleB {
        @Inject FieldCycleA a;
    }

    @Service
    static class BeanService {
        @Bean
        ProducedBean producedBean(ComponentC componentC) {
            return new ProducedBean();
        }

        @Bean
        void invalidBean() {
        }
    }

    @Service
    static class BeanInterfaceService {
        @Bean
        ProducedBean producedBean(ComponentC componentC) {
            return new ProducedBean();
        }
    }

    interface BeanPort {
    }

    static class ProducedBean implements BeanPort {
    }

    @Component
    static class BeanPortConsumer {
        @Inject BeanPort beanPort;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface CustomRegistryAnnotation {
    }

    @Registry(annotation = CustomRegistryAnnotation.class)
    static class RegistryHandler extends AnnotationHandler {
    }

    @CustomRegistryAnnotation
    static class RegistryTarget {
        @PostConstruct
        void init() {
        }
    }

    @Entity(table = "islands")
    static class ValidIsland {
        @Id
        @Column(name = "island_id", nullable = false)
        UUID id;
    }

    @Entity(table = "upgrades")
    @Index(columns = {"islandId", "upgrade_type"}, unique = true)
    static class ValidUpgrade {
        @Id UUID id;

        @Index
        @ForeignKey(entity = ValidIsland.class)
        @Column(name = "island_id", nullable = false)
        UUID islandId;

        @Column(name = "upgrade_type")
        String upgradeType;
    }

    @Entity(table = "players")
    static class ValidPlayer {
        @Id UUID id;

        @ForeignKey(entity = ValidIsland.class, referencedColumn = "island_id",
                onDelete = ForeignKeyAction.SET_NULL)
        @Column(name = "island_id")
        UUID islandId;
    }

    @Entity
    @Index(columns = "islnad_id")
    static class InvalidDatabaseReferences {
        @Id UUID id;

        @ForeignKey(entity = ValidIsland.class, referencedColumn = "islnad_id")
        @Column(name = "island_id")
        UUID islandId;
    }

    @Entity
    static class InvalidForeignKeyShape {
        @Id UUID id;

        @ForeignKey(entity = ValidIsland.class, onDelete = ForeignKeyAction.SET_NULL)
        @Column(nullable = false)
        String islandId;
    }

    @Entity
    static class RestrictiveCycleA {
        @Id UUID id;
        @ForeignKey(entity = RestrictiveCycleB.class) @Column UUID bId;
    }

    @Entity
    static class RestrictiveCycleB {
        @Id UUID id;
        @ForeignKey(entity = RestrictiveCycleA.class) @Column UUID aId;
    }

    @Entity
    static class MixedCycleA {
        @Id UUID id;
        @ForeignKey(entity = MixedCycleB.class, onDelete = ForeignKeyAction.CASCADE) @Column UUID bId;
    }

    @Entity
    static class MixedCycleB {
        @Id UUID id;
        @ForeignKey(entity = MixedCycleA.class) @Column UUID aId;
    }

    @Entity
    static class CascadeCycleA {
        @Id UUID id;
        @ForeignKey(entity = CascadeCycleB.class, onDelete = ForeignKeyAction.CASCADE) @Column UUID bId;
    }

    @Entity
    static class CascadeCycleB {
        @Id UUID id;
        @ForeignKey(entity = CascadeCycleA.class, onDelete = ForeignKeyAction.CASCADE) @Column UUID aId;
    }

    @Entity
    static class SetNullCycleA {
        @Id UUID id;
        @ForeignKey(entity = SetNullCycleB.class, onDelete = ForeignKeyAction.SET_NULL) @Column UUID bId;
    }

    @Entity
    static class SetNullCycleB {
        @Id UUID id;
        @ForeignKey(entity = SetNullCycleA.class) @Column UUID aId;
    }

    @Entity
    static class SelfReferencingEntity {
        @Id UUID id;
        @ForeignKey(entity = SelfReferencingEntity.class) @Column UUID parentId;
    }
}
