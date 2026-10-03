package net.vortexdevelopment.vinject.analyzer.model;

import net.vortexdevelopment.vinject.di.registry.RegistryOrder;

import java.lang.annotation.Annotation;
import java.util.Objects;

public record RegistryHandlerModel(Class<?> handlerClass,
                                   Class<? extends Annotation> annotationClass,
                                   RegistryOrder order) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryHandlerModel that)) return false;
        return Objects.equals(handlerClass, that.handlerClass)
                && Objects.equals(annotationClass, that.annotationClass)
                && order == that.order;
    }

}
