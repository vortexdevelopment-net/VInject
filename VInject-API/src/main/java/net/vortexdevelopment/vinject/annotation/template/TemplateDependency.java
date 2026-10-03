package net.vortexdevelopment.vinject.annotation.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface TemplateDependency {

    /**
     * The artifactId of the dependency to check for template files
     * @return the artifactId of the dependency
     */
    String groupId();

    /**
     * The artifactId of the dependency to check for template files
     * @return the artifactId of the dependency
     */
    String artifactId();

    /**
     * The version of the dependency to check for template files
     * @return the version of the dependency
     */
    String version();
}
