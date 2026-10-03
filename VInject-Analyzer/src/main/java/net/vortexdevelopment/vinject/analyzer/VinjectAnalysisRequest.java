package net.vortexdevelopment.vinject.analyzer;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.component.Root;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

@Getter
public final class VinjectAnalysisRequest {

    private final Root rootAnnotation;
    private final Class<?> rootClass;
    private final Set<Class<?>> candidateClasses;
    private final Set<Class<?>> preRegisteredTypes;
    private final Set<String> preRegisteredTypeNames;
    private final Predicate<Class<?>> loadPredicate;

    private VinjectAnalysisRequest(Builder builder) {
        this.rootAnnotation = builder.rootAnnotation;
        this.rootClass = builder.rootClass;
        this.candidateClasses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.candidateClasses));
        this.preRegisteredTypes = Collections.unmodifiableSet(new LinkedHashSet<>(builder.preRegisteredTypes));
        this.preRegisteredTypeNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.preRegisteredTypeNames));
        this.loadPredicate = builder.loadPredicate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Root rootAnnotation;
        private Class<?> rootClass;
        private final Set<Class<?>> candidateClasses = new LinkedHashSet<>();
        private final Set<Class<?>> preRegisteredTypes = new LinkedHashSet<>();
        private final Set<String> preRegisteredTypeNames = new LinkedHashSet<>();
        private Predicate<Class<?>> loadPredicate = ignored -> true;

        public Builder root(Root rootAnnotation, Class<?> rootClass) {
            this.rootAnnotation = rootAnnotation;
            this.rootClass = rootClass;
            return this;
        }

        public Builder candidateClasses(Set<Class<?>> candidateClasses) {
            this.candidateClasses.addAll(candidateClasses);
            return this;
        }

        public Builder preRegisteredTypes(Set<Class<?>> preRegisteredTypes) {
            this.preRegisteredTypes.addAll(preRegisteredTypes);
            preRegisteredTypes.stream().map(Class::getName).forEach(this.preRegisteredTypeNames::add);
            return this;
        }

        public Builder preRegisteredTypeNames(Set<String> preRegisteredTypeNames) {
            this.preRegisteredTypeNames.addAll(preRegisteredTypeNames);
            return this;
        }

        public Builder loadPredicate(Predicate<Class<?>> loadPredicate) {
            this.loadPredicate = loadPredicate != null ? loadPredicate : ignored -> true;
            return this;
        }

        public VinjectAnalysisRequest build() {
            return new VinjectAnalysisRequest(this);
        }
    }
}
