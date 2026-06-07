package net.vortexdevelopment.vinject.analyzer;

import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.analyzer.fixtures.included.IncludedPackageComponent;
import net.vortexdevelopment.vinject.analyzer.fixtures.root.IncludedPackageRoot;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

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
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-DEP-001")
                        && diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsAmbiguousDependencyProviders() {
        VinjectAnalysisResult result = analyze(AmbiguousConsumer.class, AmbiguousProviderA.class, AmbiguousProviderB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-DEP-002")
                        && diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsConstructorDependencyCycleAsError() {
        VinjectAnalysisResult result = analyze(ConstructorCycleA.class, ConstructorCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-CYCLE-001")
                        && diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void reportsFieldDependencyCycleAsWarning() {
        VinjectAnalysisResult result = analyze(FieldCycleA.class, FieldCycleB.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-CYCLE-002")
                        && diagnostic.getSeverity() == DiagnosticSeverity.WARNING);
    }

    @Test
    void discoversBeanMethodProvidersAndValidatesVoidReturn() {
        VinjectAnalysisResult result = analyze(BeanService.class, ComponentC.class);

        assertThat(result.applicationModel().getBeans())
                .anyMatch(bean -> bean.getBeanType().equals(ProducedBean.class)
                        && bean.getKind() == BeanKind.BEAN_METHOD);
        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-BEAN-001")
                        && diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
    }

    @Test
    void discoversRegistryTargets() {
        VinjectAnalysisResult result = analyze(RegistryHandler.class, RegistryTarget.class);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.applicationModel().getRegistryTargets())
                .anyMatch(target -> target.targetClass().equals(RegistryTarget.class)
                        && target.handlerClass().equals(RegistryHandler.class));
        assertThat(result.loadPlan().componentLoadOrder()).contains(RegistryTarget.class);
    }

    @Test
    void validatesRegisterSubclassesAssignability() {
        VinjectAnalysisResult result = analyze(InvalidAliasComponent.class);

        assertThat(result.diagnostics())
                .anyMatch(diagnostic -> diagnostic.getCode().equals("VINJECT-COMP-001")
                        && diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
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

    @Component(registerSubclasses = AmbiguousPort.class)
    static class AmbiguousProviderA implements AmbiguousPort {
    }

    @Component(registerSubclasses = AmbiguousPort.class)
    static class AmbiguousProviderB implements AmbiguousPort {
    }

    @Component
    static class AmbiguousConsumer {
        @Inject AmbiguousPort port;
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

    static class ProducedBean {
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

    @Component(registerSubclasses = AmbiguousPort.class)
    static class InvalidAliasComponent {
    }
}
