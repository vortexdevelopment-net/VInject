package net.vortexdevelopment.plugin.vinject.container;

/**
 * Fully qualified names for the VInject annotations understood by the IntelliJ plugin.
 */
public final class BaseComponents {

    public static final String V_INJECT_COMPONENT_PACKAGE =
            value("net.vortexdevelopment.vinject.annotation.component");
    public static final String COMPONENT = V_INJECT_COMPONENT_PACKAGE + ".Component";
    public static final String ELEMENT = V_INJECT_COMPONENT_PACKAGE + ".Element";
    public static final String REGISTRY = V_INJECT_COMPONENT_PACKAGE + ".Registry";
    public static final String REPOSITORY = V_INJECT_COMPONENT_PACKAGE + ".Repository";
    public static final String ROOT = V_INJECT_COMPONENT_PACKAGE + ".Root";
    public static final String SERVICE = V_INJECT_COMPONENT_PACKAGE + ".Service";

    public static final String BEAN = value("net.vortexdevelopment.vinject.annotation.Bean");
    public static final String INJECT = value("net.vortexdevelopment.vinject.annotation.Inject");

    public static final String ENTITY = value("net.vortexdevelopment.vinject.annotation.database.Entity");
    public static final String ID = value("net.vortexdevelopment.vinject.annotation.database.Id");
    public static final String COLUMN = value("net.vortexdevelopment.vinject.annotation.database.Column");
    public static final String TEMPORAL = value("net.vortexdevelopment.vinject.annotation.database.Temporal");
    public static final String INDEX = value("net.vortexdevelopment.vinject.annotation.database.Index");
    public static final String FOREIGN_KEY = value("net.vortexdevelopment.vinject.annotation.database.ForeignKey");

    public static final String INJECTABLE = value("net.vortexdevelopment.vinject.annotation.util.Injectable");
    public static final String REGISTER_TEMPLATE =
            value("net.vortexdevelopment.vinject.annotation.template.RegisterTemplate");
    public static final String YAML_CONFIGURATION =
            value("net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration");
    public static final String YAML_DIRECTORY =
            value("net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory");

    private static String value(String value) {
        return value;
    }

    private BaseComponents() {
    }
}
