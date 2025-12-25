package net.vortexdevelopment.vinject.di.utils;

import net.vortexdevelopment.vinject.annotation.Registry;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.di.registry.AnnotationHandler;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility class for common reflection operations in VInject.
 */
public class DependencyUtils {

    /**
     * Finds an annotation of the specified type in an array of annotations.
     *
     * @param annotations    The array of annotations to search
     * @param annotationType The annotation type to find
     * @param <T>            The annotation type
     * @return The annotation instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> annotationType) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return (T) annotation;
            }
        }
        return null;
    }

    /**
     * Gets the annotation type handled by an AnnotationHandler.
     *
     * @param handler The AnnotationHandler to inspect
     * @return The annotation class, or null if not found
     */
    public static Class<? extends Annotation> getAnnotationFromHandler(AnnotationHandler handler) {
        Registry annotation = handler.getClass().getAnnotation(Registry.class);
        return annotation != null ? annotation.annotation() : null;
    }



    /**
     * Extracts the generic entity type from a CrudRepository implementation.
     *
     * @param repositoryClass The repository class to inspect
     * @return The entity class, or null if not found
     */
    public static Class<?> getGenericTypeFromCrudRepository(Class<?> repositoryClass) {
        // Iterate over all implemented interfaces
        for (Type genericInterface : repositoryClass.getGenericInterfaces()) {
            // Check if the interface is parameterized and extends CrudRepository
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass && CrudRepository.class.isAssignableFrom(rawClass)) {
                    // Extract the first generic type argument (e.g., User)
                    Type entityType = parameterizedType.getActualTypeArguments()[0];
                    if (entityType instanceof Class<?> entityClass) {
                        return entityClass; // Return the entity class (e.g., User)
                    }
                }
            }
        }
        // Recurse up the class hierarchy if no match is found
        if (repositoryClass.getSuperclass() != null) {
            return getGenericTypeFromCrudRepository(repositoryClass.getSuperclass());
        }
        return null; // Return null if no generic type is found
    }

    public static boolean hasDefaultConstructor(Class<?> clazz) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
