package net.vortexdevelopment.vinject.di.registry;

import net.vortexdevelopment.vinject.annotation.Registry;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationHandlerRegistry {

    private Map<Class<? extends Annotation>, AnnotationHandler> handlers;

    public AnnotationHandlerRegistry() {
        handlers = new ConcurrentHashMap<>();
    }

    public void registerHandler(Class<? extends Annotation> annotation, AnnotationHandler handler) {
        handlers.put(annotation, handler);
    }

    public List<AnnotationHandler> getHandlers(RegistryOrder order) {
        return handlers.values().stream().filter(handler -> handler.getClass().getAnnotation(Registry.class).order() == order).toList();
    }
}
