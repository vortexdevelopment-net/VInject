package net.vortexdevelopment.vinject.http.registry;

import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;
import net.vortexdevelopment.vinject.http.annotation.RestController;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for @RestController annotated classes.
 * This class ensures that classes annotated with @RestController are picked up by the VInject DependencyContainer.
 */
@Registry(annotation = RestController.class)
public class RestControllerRegistryHandler extends AnnotationHandler {

    @Override
    public void handle(Class<?> clazz, @Nullable Object instance, DependencyContainer dependencyContainer) {
        // The VInject framework will automatically instantiate the class and inject dependencies.
        // We don't need to do any additional processing here, the handler's presence with @Registry
        // is enough to tell the ClasspathScanner to look for @RestController classes and treat them as components.
    }
}
