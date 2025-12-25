package net.vortexdevelopment.vinject.di.engine;

import net.vortexdevelopment.vinject.annotation.Conditional;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConditional;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.di.utils.ConditionalOperator;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Handles evaluation of @Conditional and @YamlConditional annotations.
 */
public class ConditionEvaluator {

    /**
     * Checks if a class meets the @Conditional requirements.
     *
     * @param clazz The class to check
     * @return true if conditions are met or no annotation is present
     */
    public boolean checkConditionalAnnotation(Class<?> clazz) {
        Conditional conditional = clazz.getAnnotation(Conditional.class);
        if (conditional == null) {
            return true;
        }

        ClassLoader loader = clazz.getClassLoader();

        // Check required classes (via class literals)
        try {
            Class<?>[] requiredClasses = conditional.classes();
            if (requiredClasses != null && requiredClasses.length > 0) {
                for (Class<?> requiredCls : requiredClasses) {
                    if (requiredCls != null && requiredCls != Void.class) {
                        if (!isClassPresent(requiredCls.getName(), loader)) {
                            return false;
                        }
                    }
                }
            }
        } catch (LinkageError e) {
            return false;
        }

        // Check required class (via string className)
        String className = conditional.className();
        if (className != null && !className.isEmpty()) {
            if (!isClassPresent(className, loader)) {
                return false;
            }
        }

        // Check required classes (via string classNames)
        String[] classNames = conditional.classNames();
        if (classNames != null && classNames.length > 0) {
            for (String clsName : classNames) {
                if (clsName != null && !clsName.isEmpty()) {
                    if (!isClassPresent(clsName, loader)) {
                        return false;
                    }
                }
            }
        }

        // Check property condition
        String propertyName = conditional.property();
        if (propertyName != null && !propertyName.isEmpty()) {
            Environment env = Environment.getInstance();
            String actual = env.getProperty(propertyName);
            String expected = conditional.value();
            ConditionalOperator operator = conditional.operator();
            return evaluateCondition(actual, expected, operator);
        }

        return true;
    }

    /**
     * Checks if a class meets the @YamlConditional requirements.
     *
     * @param clazz The class to check
     * @return true if conditions are met or no annotation is present
     */
    public boolean checkYamlConditionalAnnotation(Class<?> clazz) {
        YamlConditional yamlConditional = clazz.getAnnotation(YamlConditional.class);
        if (yamlConditional == null) {
            return true;
        }

        Class<?> configClass = yamlConditional.configuration();
        if (configClass == null || configClass == Void.class) {
            return false;
        }

        String path = yamlConditional.path();
        if (path == null || path.isEmpty()) {
            return false;
        }

        Object actualValue = null;
        try {
            ConfigurationContainer configurationContainer = ConfigurationContainer.getInstance();
            if (configurationContainer != null) {
                actualValue = configurationContainer.getConfigValue(configClass, path);
            }
        } catch (Throwable ignored) {
        }

        String actual = actualValue != null ? actualValue.toString() : null;
        String expected = yamlConditional.value();
        ConditionalOperator operator = yamlConditional.operator();
        return evaluateCondition(actual, expected, operator);
    }

    /**
     * Evaluates a condition based on actual value, expected value, and operator.
     */
    public boolean evaluateCondition(@Nullable String actual, String expected, ConditionalOperator operator) {
        if (operator == null) {
            operator = ConditionalOperator.EQUALS;
        }

        if (actual == null) {
            return operator == ConditionalOperator.NOT_EQUALS && expected != null && !expected.isEmpty();
        }

        if (expected == null) {
            expected = "";
        }

        return switch (operator) {
            case EQUALS -> Objects.equals(actual, expected);
            case NOT_EQUALS -> !Objects.equals(actual, expected);
            case GREATER_THAN -> compareAsNumberOrString(actual, expected) > 0;
            case LESS_THAN -> compareAsNumberOrString(actual, expected) < 0;
            case GREATER_THAN_OR_EQUALS -> compareAsNumberOrString(actual, expected) >= 0;
            case LESS_THAN_OR_EQUALS -> compareAsNumberOrString(actual, expected) <= 0;
            case CONTAINS -> actual.contains(expected);
            case NOT_CONTAINS -> !actual.contains(expected);
            case STARTS_WITH -> actual.startsWith(expected);
            case ENDS_WITH -> actual.endsWith(expected);
            case MATCHES -> actual.matches(expected);
        };
    }

    /**
     * Checks if a class is present in the classpath.
     */
    public boolean isClassPresent(String name, ClassLoader loader) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Compares two strings either as numbers or lexicographically.
     */
    private int compareAsNumberOrString(String actual, String expected) {
        try {
            double actualNum = Double.parseDouble(actual.trim());
            double expectedNum = Double.parseDouble(expected.trim());
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            return actual.compareTo(expected);
        }
    }
}
