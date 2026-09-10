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
    INVALID_BEAN_VOID_RETURN("VINJECT-BEAN-001", "@Bean method must not return void: %s"),
    INVALID_BEAN_SERVICE_CONSTRUCTOR(
            "VINJECT-BEAN-003",
            "@Service classes with @Bean methods must have a default constructor: %s"
    ),
    MISSING_DEPENDENCY("VINJECT-DEP-001", "Missing dependency %s required by %s"),
    AMBIGUOUS_DEPENDENCY("VINJECT-DEP-002", "Ambiguous dependency %s required by %s. Providers: %s"),
    MISSING_NAMED_DEPENDENCY(
            "VINJECT-DEP-005",
            "Named dependency '%s' of type %s required by %s was not found"
    ),
    OPTIONAL_DEPENDENCY_UNRESOLVED("VINJECT-DEP-003", "Optional dependency %s is not available for %s"),
    YAML_EARLY_LOAD_DEPENDENCY(
            "VINJECT-DEP-004",
            "Dependency %s required by YAML configuration %s is not available during YAML configuration loading. Providers are loaded later: %s"
    ),

    HARD_DEPENDENCY_CYCLE("VINJECT-CYCLE-001", "Constructor or lifecycle dependency cycle detected: %s"),
    DEFERRED_DEPENDENCY_CYCLE("VINJECT-CYCLE-002", "Field/setter dependency cycle will be deferred at runtime: %s"),

    INDEX_NOT_ON_ENTITY("VINJECT-DB-001", "@Index may only be used on @Entity classes or their fields: %s"),
    INVALID_INDEX_DECLARATION("VINJECT-DB-002", "%s"),
    UNKNOWN_INDEX_COLUMN("VINJECT-DB-003", "@Index references unknown field or column '%s' on %s"),
    DUPLICATE_INDEX_COLUMN("VINJECT-DB-004", "@Index references field or column '%s' more than once on %s"),
    DUPLICATE_INDEX_NAME("VINJECT-DB-005", "Duplicate index name '%s' on %s"),
    FOREIGN_KEY_NOT_ON_ENTITY("VINJECT-DB-006", "@ForeignKey may only be used on a persisted @Entity field: %s"),
    FOREIGN_KEY_ENTITY_REQUIRED("VINJECT-DB-007", "Scalar @ForeignKey field %s must declare entity"),
    FOREIGN_KEY_TARGET_NOT_ENTITY("VINJECT-DB-008", "@ForeignKey target %s is not annotated with @Entity"),
    FOREIGN_KEY_TARGET_COLUMN_UNKNOWN("VINJECT-DB-009", "@ForeignKey references unknown field or column '%s' on %s"),
    FOREIGN_KEY_TARGET_NOT_KEY("VINJECT-DB-010", "@ForeignKey target %s must be a primary or unique field"),
    FOREIGN_KEY_TYPE_MISMATCH("VINJECT-DB-011", "Foreign-key field %s has type %s but target %s has type %s"),
    FOREIGN_KEY_SET_NULL_NOT_NULLABLE("VINJECT-DB-012", "SET_NULL requires nullable=true on %s"),
    FOREIGN_KEY_RESTRICTIVE_DELETE_CYCLE(
            "VINJECT-DB-013",
            "Restrictive foreign-key deletion cycle detected among %s; use SET_NULL on one relationship or remove the reverse reference"
    ),
    FOREIGN_KEY_CASCADE_DELETE_CYCLE(
            "VINJECT-DB-014",
            "Cascading foreign-key deletion cycle detected among %s; deleting one entity may recursively delete the entire cycle"
    );

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
