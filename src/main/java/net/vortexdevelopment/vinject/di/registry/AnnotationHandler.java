package net.vortexdevelopment.vinject.di.registry;

import net.vortexdevelopment.vinject.di.DependencyContainer;
import sun.misc.Unsafe;

import java.lang.annotation.Annotation;

public abstract class AnnotationHandler {

    public AnnotationHandler() {
    }

    public abstract Class<? extends Annotation> getAnnotation();

    public abstract void handle(Class<?> clazz, DependencyContainer dependencyContainer);
}
