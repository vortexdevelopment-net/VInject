package net.vortexdevelopment.vinject.di.resolver;

import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.config.Environment;

/**
 * Built-in resolver for @Value annotation.
 * Handles property injection from application.properties, environment variables, and system properties.
 */
@ArgumentResolver(value = Value.class, priority = 100)
public class ValueArgumentResolver implements ArgumentResolverProcessor {
    
    @Override
    public boolean canResolve(ArgumentResolverContext context) {
        return context.hasAnnotation(Value.class);
    }
    
    @Override
    public Object resolve(ArgumentResolverContext context) {
        Value valueAnnotation = context.getAnnotation(Value.class);
        if (valueAnnotation == null) {
            return null;
        }
        
        String expression = valueAnnotation.value();
        Class<?> targetType = context.getTargetType();
        
        return resolveValue(expression, targetType);
    }
    
    
    /**
     * Resolve a @Value annotation expression to the appropriate type.
     * Supports Spring Boot-style property resolution with default values.
     * 
     * @param expression The property expression (e.g., "${app.timeout:5000}")
     * @param targetType The target type to convert to
     * @return The resolved and converted value
     */
    private Object resolveValue(String expression, Class<?> targetType) {
        Environment env = Environment.getInstance();
        String resolvedValue = env.resolveProperty(expression);
        
        // Convert to target type
        if (targetType == String.class) {
            return resolvedValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to int for expression: " + expression, e);
            }
        } else if (targetType == long.class || targetType == Long.class) {
            try {
                return Long.parseLong(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to long for expression: " + expression, e);
            }
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(resolvedValue);
        } else if (targetType == double.class || targetType == Double.class) {
            try {
                return Double.parseDouble(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to double for expression: " + expression, e);
            }
        } else if (targetType == float.class || targetType == Float.class) {
            try {
                return Float.parseFloat(resolvedValue);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to float for expression: " + expression, e);
            }
        } else {
            throw new RuntimeException("Unsupported type for @Value injection: " + targetType.getName() + " for expression: " + expression);
        }
    }
}

