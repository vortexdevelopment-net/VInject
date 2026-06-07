package net.vortexdevelopment.vinject.analyzer;

import net.vortexdevelopment.vinject.di.registry.RegistryOrder;

import java.lang.annotation.Annotation;
import java.util.Objects;

public final class RegistryHandlerModel {

    private final Class<?> handlerClass;
    private final Class<? extends Annotation> annotationClass;
    private final RegistryOrder order;

    public RegistryHandlerModel(Class<?> handlerClass, Class<? extends Annotation> annotationClass, RegistryOrder order) {
        this.handlerClass = handlerClass;
        this.annotationClass = annotationClass;
        this.order = order;
    }

    public Class<?> getHandlerClass() {
        return handlerClass;
    }

    public Class<? extends Annotation> getAnnotationClass() {
        return annotationClass;
    }

    public RegistryOrder getOrder() {
        return order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryHandlerModel that)) return false;
        return Objects.equals(handlerClass, that.handlerClass)
                && Objects.equals(annotationClass, that.annotationClass)
                && order == that.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(handlerClass, annotationClass, order);
    }
}
