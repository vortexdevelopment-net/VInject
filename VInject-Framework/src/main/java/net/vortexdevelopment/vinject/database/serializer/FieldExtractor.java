package net.vortexdevelopment.vinject.database.serializer;

import net.vortexdevelopment.vinject.annotation.database.FieldValue;
import net.vortexdevelopment.vinject.annotation.database.MethodValue;

import java.lang.reflect.InvocationTargetException;

/**
 * Utility class for extracting values from objects using @FieldValue or @MethodValue annotations.
 */
public class FieldExtractor {

    /**
     * Extract a value from an object using the specified field or method annotation.
     * 
     * @param source The source object to extract from
     * @param fieldAnnotation The @FieldValue annotation (may be null)
     * @param methodAnnotation The @MethodValue annotation (may be null)
     * @return The extracted value, or null if extraction fails
     */
    public static Object extractValue(Object source, FieldValue fieldAnnotation, MethodValue methodAnnotation) {
        if (source == null) {
            return null;
        }

        if (methodAnnotation != null && !methodAnnotation.value().isEmpty()) {
            return extractViaMethodChain(source, methodAnnotation.value());
        }

        if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
            return extractViaFieldPath(source, fieldAnnotation.value());
        }

        return null;
    }

    /**
     * Extract value using a method chain (e.g., "getWorld.getName")
     */
    private static Object extractViaMethodChain(Object source, String methodChain) {
        String[] methods = methodChain.split("\\.");
        Object current = source;

        for (String methodName : methods) {
            if (current == null) {
                return null;
            }

            try {
                java.lang.reflect.Method method = findMethod(current.getClass(), methodName);
                if (method == null) {
                    return null;
                }
                method.setAccessible(true);
                current = method.invoke(current);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        return current;
    }

    /**
     * Extract value using a field path (e.g., "world.name")
     * Tries field access first, then getter methods
     */
    private static Object extractViaFieldPath(Object source, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = source;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            try {
                // Try to get field directly
                try {
                    java.lang.reflect.Field field = current.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    current = field.get(current);
                } catch (NoSuchFieldException e) {
                    // FieldValue not found, try getter method
                    java.lang.reflect.Method getter = findGetterMethod(current.getClass(), part);
                    if (getter != null) {
                        getter.setAccessible(true);
                        current = getter.invoke(current);
                    } else {
                        return null;
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        return current;
    }

    /**
     * Find a method by name (case-insensitive, handles get/is prefixes)
     */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) {
        // Try exact match first
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
        }

        // Try with get/is prefix
        String capitalized = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return clazz.getMethod("is" + capitalized);
        } catch (NoSuchMethodException ignored) {
        }

        // Try declared methods (case-insensitive)
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equalsIgnoreCase(methodName)) {
                return method;
            }
        }

        return null;
    }

    /**
     * Find a getter method for a field name
     */
    private static java.lang.reflect.Method findGetterMethod(Class<?> clazz, String fieldName) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        
        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return clazz.getMethod("is" + capitalized);
        } catch (NoSuchMethodException ignored) {
        }

        return null;
    }
}
