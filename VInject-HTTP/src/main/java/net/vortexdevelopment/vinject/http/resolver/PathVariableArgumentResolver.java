package net.vortexdevelopment.vinject.http.resolver;

import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverProcessor;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverContext;
import net.vortexdevelopment.vinject.http.annotation.PathVariable;
import net.vortexdevelopment.vinject.http.dispatcher.PathVariablesHolder;

/**
 * Resolves controller method parameters annotated with @PathVariable.
 * Extracts the variable value from PathVariablesHolder and converts it to the parameter type.
 */
@ArgumentResolver(value = PathVariable.class, priority = 80)
public class PathVariableArgumentResolver implements ArgumentResolverProcessor {

    @Override
    public boolean canResolve(ArgumentResolverContext context) {
        return context.hasAnnotation(PathVariable.class);
    }

    @Override
    public Object resolve(ArgumentResolverContext context) {
        PathVariable annotation = context.getAnnotation(PathVariable.class);
        if (annotation == null) {
            return null;
        }

        String name = annotation.value();
        if (name.isEmpty()) {
            name = context.getParameter().getName();
        }

        PathVariablesHolder holder = context.getContainer().getDependencyOrNull(PathVariablesHolder.class);
        if (holder == null) {
            return null;
        }

        String value = holder.get(name);
        if (value == null) {
            return null;
        }

        Class<?> targetType = context.getTargetType();
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        }

        return value;
    }
}
