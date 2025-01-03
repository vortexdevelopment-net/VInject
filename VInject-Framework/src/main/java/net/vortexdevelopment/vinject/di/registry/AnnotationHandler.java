package net.vortexdevelopment.vinject.di.registry;

import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.annotation.Annotation;

public abstract class AnnotationHandler {

    public AnnotationHandler() {
    }

    public abstract void handle(Class<?> clazz, @Nullable Object instance, DependencyContainer dependencyContainer);
}
