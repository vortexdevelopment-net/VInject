package net.vortexdevelopment.vinject.di.utils;

import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.Qualifier;
import net.vortexdevelopment.vinject.annotation.component.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public final class BeanNamingUtils {

    private BeanNamingUtils() {
    }

    public static String resolveComponentName(Class<?> clazz) {
        Qualifier qualifier = clazz.getAnnotation(Qualifier.class);
        if (qualifier != null && !qualifier.value().isEmpty()) {
            return qualifier.value();
        }
        Component component = clazz.getAnnotation(Component.class);
        if (component != null && !component.name().isEmpty()) {
            return component.name();
        }
        return "";
    }

    public static String resolveBeanMethodName(Method method) {
        Qualifier qualifier = method.getAnnotation(Qualifier.class);
        if (qualifier != null && !qualifier.value().isEmpty()) {
            return qualifier.value();
        }
        Bean bean = method.getAnnotation(Bean.class);
        if (bean != null && !bean.name().isEmpty()) {
            return bean.name();
        }
        return "";
    }

    public static String extractQualifier(Annotation[] annotations) {
        if (annotations == null) {
            return "";
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof Qualifier qualifier && !qualifier.value().isEmpty()) {
                return qualifier.value();
            }
        }
        return "";
    }
}
