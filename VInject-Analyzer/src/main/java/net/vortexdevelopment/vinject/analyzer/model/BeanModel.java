package net.vortexdevelopment.vinject.analyzer.model;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record BeanModel(Class<?> beanType, Class<?> implementationClass,
                        BeanKind kind, int priority,
                        Method beanMethod,
                        Set<Class<?>> aliases,
                        String qualifierName) {

    public BeanModel(Class<?> beanType, Class<?> implementationClass, BeanKind kind, int priority,
                     Method beanMethod, Set<Class<?>> aliases, String qualifierName) {
        this.beanType = beanType;
        this.implementationClass = implementationClass;
        this.kind = kind;
        this.priority = priority;
        this.beanMethod = beanMethod;
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(aliases));
        this.qualifierName = qualifierName == null ? "" : qualifierName;
    }

    public BeanModel(Class<?> beanType, Class<?> implementationClass, BeanKind kind, int priority,
                     Method beanMethod, Set<Class<?>> aliases) {
        this(beanType, implementationClass, kind, priority, beanMethod, aliases, "");
    }

    public Set<Class<?>> getProvidedTypes() {
        Set<Class<?>> provided = new LinkedHashSet<>();
        provided.add(beanType);
        provided.addAll(aliases);
        return provided;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BeanModel beanModel)) return false;
        return Objects.equals(beanType, beanModel.beanType)
                && Objects.equals(implementationClass, beanModel.implementationClass)
                && kind == beanModel.kind
                && Objects.equals(beanMethod, beanModel.beanMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanType, implementationClass, kind, beanMethod);
    }
}
