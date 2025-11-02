package net.vortexdevelopment.vinject.di.resolver;

import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Context object providing all information needed for argument resolution.
 * Contains metadata about the field or parameter being injected.
 */
public class ArgumentResolverContext {
    
    private final Class<?> targetType;
    private final Annotation[] annotations;
    private final Field field;
    private final Parameter parameter;
    private final Class<?> declaringClass;
    private final Method method;
    private final Constructor<?> constructor;
    private final DependencyContainer container;
    private final Object instance;
    
    private ArgumentResolverContext(Builder builder) {
        this.targetType = builder.targetType;
        this.annotations = builder.annotations;
        this.field = builder.field;
        this.parameter = builder.parameter;
        this.declaringClass = builder.declaringClass;
        this.method = builder.method;
        this.constructor = builder.constructor;
        this.container = builder.container;
        this.instance = builder.instance;
    }
    
    /**
     * Get the target type of the field or parameter.
     * 
     * @return The type that needs to be injected
     */
    public Class<?> getTargetType() {
        return targetType;
    }
    
    /**
     * Get all annotations on the field or parameter.
     * 
     * @return Array of annotations
     */
    public Annotation[] getAnnotations() {
        return annotations;
    }
    
    /**
     * Check if a specific annotation is present.
     * 
     * @param annotationClass The annotation class to check for
     * @return true if the annotation is present
     */
    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationClass)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get a specific annotation if present.
     * 
     * @param annotationClass The annotation class to retrieve
     * @param <T> The annotation type
     * @return The annotation instance, or null if not present
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(annotationClass)) {
                return (T) annotation;
            }
        }
        return null;
    }
    
    /**
     * Get the field being injected (null if this is a parameter).
     * 
     * @return The field, or null if injecting a parameter
     */
    @Nullable
    public Field getField() {
        return field;
    }
    
    /**
     * Get the parameter being injected (null if this is a field).
     * 
     * @return The parameter, or null if injecting a field
     */
    @Nullable
    public Parameter getParameter() {
        return parameter;
    }
    
    /**
     * Get the class that declares the field or method/constructor.
     * 
     * @return The declaring class
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }
    
    /**
     * Get the method containing the parameter (null if constructor or field).
     * 
     * @return The method, or null if constructor or field
     */
    @Nullable
    public Method getMethod() {
        return method;
    }
    
    /**
     * Get the constructor containing the parameter (null if method or field).
     * 
     * @return The constructor, or null if method or field
     */
    @Nullable
    public Constructor<?> getConstructor() {
        return constructor;
    }
    
    /**
     * Get access to the dependency container for resolving dependencies.
     * 
     * @return The dependency container
     */
    public DependencyContainer getContainer() {
        return container;
    }
    
    /**
     * Get the instance being injected into (null for static fields or constructor).
     * 
     * @return The instance, or null for static/constructor injection
     */
    @Nullable
    public Object getInstance() {
        return instance;
    }
    
    /**
     * Check if this is a field injection context.
     * 
     * @return true if injecting a field
     */
    public boolean isField() {
        return field != null;
    }
    
    /**
     * Check if this is a parameter injection context.
     * 
     * @return true if injecting a parameter
     */
    public boolean isParameter() {
        return parameter != null;
    }
    
    /**
     * Builder for creating ArgumentResolverContext instances.
     */
    public static class Builder {
        private Class<?> targetType;
        private Annotation[] annotations;
        private Field field;
        private Parameter parameter;
        private Class<?> declaringClass;
        private Method method;
        private Constructor<?> constructor;
        private DependencyContainer container;
        private Object instance;
        
        public Builder targetType(Class<?> targetType) {
            this.targetType = targetType;
            return this;
        }
        
        public Builder annotations(Annotation[] annotations) {
            this.annotations = annotations;
            return this;
        }
        
        public Builder field(Field field) {
            this.field = field;
            return this;
        }
        
        public Builder parameter(Parameter parameter) {
            this.parameter = parameter;
            return this;
        }
        
        public Builder declaringClass(Class<?> declaringClass) {
            this.declaringClass = declaringClass;
            return this;
        }
        
        public Builder method(Method method) {
            this.method = method;
            return this;
        }
        
        public Builder constructor(Constructor<?> constructor) {
            this.constructor = constructor;
            return this;
        }
        
        public Builder container(DependencyContainer container) {
            this.container = container;
            return this;
        }
        
        public Builder instance(Object instance) {
            this.instance = instance;
            return this;
        }
        
        public ArgumentResolverContext build() {
            if (targetType == null) {
                throw new IllegalStateException("targetType is required");
            }
            if (declaringClass == null) {
                throw new IllegalStateException("declaringClass is required");
            }
            if (container == null) {
                throw new IllegalStateException("container is required");
            }
            if (annotations == null) {
                this.annotations = new Annotation[0];
            }
            return new ArgumentResolverContext(this);
        }
    }
}

