package net.vortexdevelopment.vinject.analyzer;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class BeanModel {

    private final Class<?> beanType;
    private final Class<?> implementationClass;
    private final BeanKind kind;
    private final int priority;
    private final Method beanMethod;
    private final Set<Class<?>> aliases;

    public BeanModel(Class<?> beanType, Class<?> implementationClass, BeanKind kind, int priority, Method beanMethod, Set<Class<?>> aliases) {
        this.beanType = beanType;
        this.implementationClass = implementationClass;
        this.kind = kind;
        this.priority = priority;
        this.beanMethod = beanMethod;
        this.aliases = Collections.unmodifiableSet(new LinkedHashSet<>(aliases));
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public Class<?> getImplementationClass() {
        return implementationClass;
    }

    public BeanKind getKind() {
        return kind;
    }

    public int getPriority() {
        return priority;
    }

    public Method getBeanMethod() {
        return beanMethod;
    }

    public Set<Class<?>> getAliases() {
        return aliases;
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
