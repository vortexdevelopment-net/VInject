package net.vortexdevelopment.vinject.di.engine;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.di.resolver.ArgumentResolverContext;
import net.vortexdevelopment.vinject.di.utils.DependencyUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles dependency injection into fields and methods.
 * Centralizes injection logic, including fallback mechanisms and resolver calls.
 */
public class InjectionEngine {

    private final DependencyContainer container;

    public InjectionEngine(DependencyContainer container) {
        this.container = container;
    }

    /**
     * Injects dependencies into non-static fields and calls @Inject annotated setters.
     *
     * @param object The instance to inject dependencies into
     */
    public void inject(@NotNull Object object) {
        Class<?> clazz = object.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                    .targetType(field.getType())
                    .annotations(field.getAnnotations())
                    .field(field)
                    .declaringClass(clazz)
                    .container(container)
                    .instance(object)
                    .build();

            Object resolvedValue = container.resolveArgument(context);
            if (resolvedValue != null) {
                try {
                    field.setAccessible(true);
                    field.set(object, resolvedValue);
                    continue;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject resolved value for field: " + field.getName() + 
                                               " in class: " + clazz.getName(), e);
                }
            }

            // Fallback for @Value
            if (field.isAnnotationPresent(Value.class)) {
                Value valueAnnotation = field.getAnnotation(Value.class);
                try {
                    Object value = resolveValue(valueAnnotation.value(), field.getType());
                    field.setAccessible(true);
                    field.set(object, value);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject @Value for field: " + field.getName() + 
                                               " in class: " + clazz.getName(), e);
                }
            }
        }

        injectSetters(object);
    }

    /**
     * Injects dependencies into static fields of the target class.
     *
     * @param target The class to process
     */
    public void injectStatic(@NotNull Class<?> target) {
        for (Field field : target.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                    .targetType(field.getType())
                    .annotations(field.getAnnotations())
                    .field(field)
                    .declaringClass(target)
                    .container(container)
                    .instance(null)
                    .build();

            Object resolvedValue = container.resolveArgument(context);
            if (resolvedValue != null) {
                try {
                    field.setAccessible(true);
                    field.set(null, resolvedValue);
                    continue;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject resolved value for static field: " + field.getName() + 
                                               " in class: " + target.getName(), e);
                }
            }

            // Fallback for @Value
            if (field.isAnnotationPresent(Value.class)) {
                Value valueAnnotation = field.getAnnotation(Value.class);
                try {
                    Object value = resolveValue(valueAnnotation.value(), field.getType());
                    field.setAccessible(true);
                    field.set(null, value);
                } catch (Exception e) {
                    throw new RuntimeException("Unable to inject @Value for static field: " + field.getName() + 
                                               " in class: " + target.getName(), e);
                }
            }
        }
    }

    /**
     * Resolve a @Value annotation expression to the appropriate type.
     */
    public Object resolveValue(String expression, Class<?> targetType) {
        Environment env = Environment.getInstance();
        String resolvedValue = env.resolveProperty(expression);

        try {
            return switch (targetType.getName()) {
                case "java.lang.String" -> resolvedValue;
                case "int", "java.lang.Integer" -> Integer.parseInt(resolvedValue);
                case "long", "java.lang.Long" -> Long.parseLong(resolvedValue);
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(resolvedValue);
                case "double", "java.lang.Double" -> Double.parseDouble(resolvedValue);
                case "float", "java.lang.Float" -> Float.parseFloat(resolvedValue);
                default -> throw new RuntimeException("Unsupported type for @Value injection: " + targetType.getName() + 
                                                       " for expression: " + expression);
            };
        } catch (NumberFormatException e) {
            throw new RuntimeException("Cannot convert property value '" + resolvedValue + "' to " + 
                                       targetType.getSimpleName() + " for expression: " + expression, e);
        }
    }

    /**
     * Inject dependencies via setter methods.
     */
    private void injectSetters(@NotNull Object object) {
        Class<?> clazz = object.getClass();
        Set<String> injectFieldNames = new HashSet<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                injectFieldNames.add(field.getName());
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            boolean hasInjectAnnotation = method.isAnnotationPresent(Inject.class);
            boolean isSetterForInjectField = false;
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                String fieldName = Character.toLowerCase(method.getName().charAt(3)) + method.getName().substring(4);
                isSetterForInjectField = injectFieldNames.contains(fieldName);
            }

            if (!hasInjectAnnotation && !isSetterForInjectField) {
                continue;
            }

            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() == 0) {
                continue;
            }

            try {
                method.setAccessible(true);
                Class<?>[] parameterTypes = method.getParameterTypes();
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                Object[] parameters = new Object[parameterTypes.length];
                boolean allResolved = true;

                for (int i = 0; i < parameterTypes.length; i++) {
                    ArgumentResolverContext context = new ArgumentResolverContext.Builder()
                            .targetType(parameterTypes[i])
                            .annotations(parameterAnnotations[i])
                            .parameter(method.getParameters()[i])
                            .declaringClass(clazz)
                            .method(method)
                            .container(container)
                            .instance(object)
                            .build();

                    Object resolvedValue = container.resolveArgument(context);
                    if (resolvedValue != null) {
                        parameters[i] = resolvedValue;
                    } else {
                        Value valueAnnotation = DependencyUtils.findAnnotation(parameterAnnotations[i], Value.class);
                        if (valueAnnotation != null) {
                            parameters[i] = resolveValue(valueAnnotation.value(), parameterTypes[i]);
                        } else {
                            allResolved = false;
                            break;
                        }
                    }
                }

                if (allResolved) {
                    method.invoke(object, parameters);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error invoking @Inject setter method " + method.getName() + 
                                           " on " + clazz.getName(), e);
            }
        }
    }
}
