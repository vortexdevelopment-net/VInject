package net.vortexdevelopment.vinject.analyzer.diagnostic;

public enum DiagnosticCode {
    ANALYZER_MISSING_INPUT(
            "VINJECT-ANALYZER-001",
            "No root annotation/root class or candidate classes were provided for analysis"
    ),
    ANALYZER_METHOD_INSPECTION_FAILED(
            "VINJECT-ANALYZER-002",
            "Unable to inspect methods for %s: %s"
    ),
    ANALYZER_FIELD_INSPECTION_FAILED(
            "VINJECT-ANALYZER-003",
            "Unable to inspect fields for %s: %s"
    ),
    ANALYZER_CONSTRUCTOR_INSPECTION_FAILED(
            "VINJECT-ANALYZER-004",
            "Unable to inspect constructors for %s: %s"
    ),
    ANALYZER_DEFAULT_CONSTRUCTOR_INSPECTION_FAILED(
            "VINJECT-ANALYZER-005",
            "Unable to inspect default constructor for %s: %s"
    ),

    CONDITION_EVALUATION_FAILED("VINJECT-COND-001", "%s"),
    CONDITION_INSPECTION_FAILED("VINJECT-COND-002", "Unable to evaluate load conditions for %s: %s"),

    INVALID_REGISTRY_HANDLER("VINJECT-REG-001", "@Registry class must extend AnnotationHandler: %s"),
    INVALID_COMPONENT_ALIAS("VINJECT-COMP-001", "registerSubclasses target %s is not assignable from %s"),

    INVALID_BEAN_VOID_RETURN("VINJECT-BEAN-001", "@Bean method must not return void: %s"),
    INVALID_BEAN_SERVICE_CONSTRUCTOR(
            "VINJECT-BEAN-003",
            "@Service classes with @Bean methods must have a default constructor: %s"
    ),
    INVALID_BEAN_ALIAS("VINJECT-BEAN-004", "@Bean registerSubclasses target %s is not assignable from %s"),

    MISSING_DEPENDENCY("VINJECT-DEP-001", "Missing dependency %s required by %s"),
    AMBIGUOUS_DEPENDENCY("VINJECT-DEP-002", "Ambiguous dependency %s required by %s. Providers: %s"),
    OPTIONAL_DEPENDENCY_UNRESOLVED("VINJECT-DEP-003", "Optional dependency %s is not available for %s"),
    YAML_EARLY_LOAD_DEPENDENCY(
            "VINJECT-DEP-004",
            "Dependency %s required by YAML configuration %s is not available during YAML configuration loading. Providers are loaded later: %s"
    ),

    HARD_DEPENDENCY_CYCLE("VINJECT-CYCLE-001", "Constructor or lifecycle dependency cycle detected: %s"),
    DEFERRED_DEPENDENCY_CYCLE("VINJECT-CYCLE-002", "Field/setter dependency cycle will be deferred at runtime: %s");

    private final String code;
    private final String messageTemplate;

    DiagnosticCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public String code() {
        return code;
    }

    public String messageTemplate() {
        return messageTemplate;
    }

    public String format(Object... args) {
        return messageTemplate.formatted(args);
    }

    @Override
    public String toString() {
        return code;
    }
}
