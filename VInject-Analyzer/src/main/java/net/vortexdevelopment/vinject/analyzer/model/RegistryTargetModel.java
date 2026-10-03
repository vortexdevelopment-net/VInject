package net.vortexdevelopment.vinject.analyzer.model;

import net.vortexdevelopment.vinject.di.registry.RegistryOrder;

import java.lang.annotation.Annotation;
import java.util.Objects;

public record RegistryTargetModel(Class<?> targetClass,
                                  Class<? extends Annotation> annotationClass,
                                  Class<?> handlerClass,
                                  RegistryOrder order) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryTargetModel that)) return false;
        return Objects.equals(targetClass, that.targetClass)
                && Objects.equals(annotationClass, that.annotationClass)
                && Objects.equals(handlerClass, that.handlerClass)
                && order == that.order;
    }

}
