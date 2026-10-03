package net.vortexdevelopment.plugin.vinject.syntax;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import net.vortexdevelopment.plugin.vinject.container.BaseComponents;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DatabaseAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {
    private static final String ENTITY = BaseComponents.ENTITY;
    private static final String COLUMN = BaseComponents.COLUMN;
    private static final String ID = BaseComponents.ID;
    private static final String TEMPORAL = BaseComponents.TEMPORAL;
    private static final String INDEX = BaseComponents.INDEX;
    private static final String FOREIGN_KEY = BaseComponents.FOREIGN_KEY;

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                validateClassIndexes(psiClass, holder);
            }

            @Override
            public void visitField(@NotNull PsiField field) {
                validateFieldIndexes(field, holder);
                validateForeignKey(field, holder);
            }
        };
    }

    private static void validateClassIndexes(PsiClass psiClass, ProblemsHolder holder) {
        List<PsiAnnotation> indexes = annotations(psiClass.getModifierList(), INDEX);
        if (indexes.isEmpty()) return;
        if (!hasAnnotation(psiClass.getModifierList(), ENTITY)) {
            indexes.forEach(index -> problem(holder, index, "@Index may only be used on an @Entity class"));
            return;
        }

        Map<String, PsiField> fields = resolvableFields(psiClass);
        Set<String> names = new HashSet<>();
        for (PsiAnnotation index : indexes) {
            List<AnnotationString> columns = stringArray(index, "columns");
            if (columns.isEmpty()) {
                problem(holder, index, "Class-level @Index must declare at least one column");
            }
            Set<PsiField> resolved = new HashSet<>();
            for (AnnotationString column : columns) {
                PsiField field = fields.get(column.value());
                if (field == null) {
                    problem(holder, column.element(), "Unknown entity field or @Column name '" + column.value() + "'");
                } else if (!resolved.add(field)) {
                    problem(holder, column.element(), "Index contains the same resolved column more than once");
                }
            }
            String name = stringValue(index, "name");
            if (name != null && !name.isBlank() && !names.add(name.toLowerCase())) {
                problem(holder, index, "Duplicate index name '" + name + "'");
            }
        }
    }

    private static void validateFieldIndexes(PsiField field, ProblemsHolder holder) {
        List<PsiAnnotation> indexes = annotations(field.getModifierList(), INDEX);
        if (indexes.isEmpty()) return;
        PsiClass owner = field.getContainingClass();
        if (owner == null || !hasAnnotation(owner.getModifierList(), ENTITY)) {
            indexes.forEach(index -> problem(holder, index, "@Index may only be used on a field of an @Entity class"));
            return;
        }
        if (!isPersistent(field)) {
            indexes.forEach(index -> problem(holder, index, "@Index requires a persisted @Column, @Id, or @Temporal field"));
        }
        for (PsiAnnotation index : indexes) {
            if (!stringArray(index, "columns").isEmpty()) {
                problem(holder, index, "Field-level @Index must not declare columns");
            }
        }
    }

    private static void validateForeignKey(PsiField field, ProblemsHolder holder) {
        PsiAnnotation foreignKey = field.getAnnotation(FOREIGN_KEY);
        if (foreignKey == null) return;
        PsiClass owner = field.getContainingClass();
        if (owner == null || !hasAnnotation(owner.getModifierList(), ENTITY) || !isPersistent(field)) {
            problem(holder, foreignKey, "@ForeignKey requires a persisted field on an @Entity class");
            return;
        }

        PsiClass target = declaredClassValue(foreignKey, "entity");
        if (target == null) {
            target = PsiTypesUtil.getPsiClass(field.getType());
            if (target == null || !hasAnnotation(target.getModifierList(), ENTITY)) {
                problem(holder, foreignKey, "Scalar foreign-key fields must declare entity = ReferencedEntity.class");
                return;
            }
        }
        if (!hasAnnotation(target.getModifierList(), ENTITY)) {
            problem(holder, foreignKey, "Foreign-key target must be annotated with @Entity");
            return;
        }

        String requestedColumn = stringValue(foreignKey, "referencedColumn");
        PsiField targetField = requestedColumn == null || requestedColumn.isBlank()
                ? primaryKey(target)
                : resolvableFields(target).get(requestedColumn);
        if (targetField == null) {
            PsiAnnotationMemberValue value = foreignKey.findDeclaredAttributeValue("referencedColumn");
            problem(holder, value == null ? foreignKey : value,
                    requestedColumn == null || requestedColumn.isBlank()
                            ? "Referenced entity does not declare a primary key"
                            : "Unknown referenced Java field or @Column name '" + requestedColumn + "'");
            return;
        }

        PsiAnnotation targetColumn = targetField.getAnnotation(COLUMN);
        if (!hasAnnotation(targetField.getModifierList(), ID)
                && !booleanValue(targetColumn, "primaryKey", false)
                && !booleanValue(targetColumn, "unique", false)) {
            problem(holder, foreignKey, "Referenced field must be a primary key or unique column");
        }

        PsiClass localEntityType = PsiTypesUtil.getPsiClass(field.getType());
        if (localEntityType == null || !localEntityType.equals(target)) {
            if (!boxedCanonicalName(field.getType()).equals(boxedCanonicalName(targetField.getType()))) {
                problem(holder, foreignKey, "Foreign-key field type does not match referenced field type");
            }
        }

        if ((enumValueEndsWith(foreignKey, "onDelete", "SET_NULL")
                || enumValueEndsWith(foreignKey, "onUpdate", "SET_NULL")) && !nullable(field)) {
            problem(holder, foreignKey, "SET_NULL requires @Column(nullable = true)");
        }

        validateForeignKeyDeleteCycle(foreignKey, owner, target, holder);
    }

    private static void validateForeignKeyDeleteCycle(PsiAnnotation sourceForeignKey, PsiClass owner,
                                                      PsiClass target, ProblemsHolder holder) {
        String sourceAction = deleteAction(sourceForeignKey);
        if (owner.equals(target) || sourceAction.equals("SET_NULL")) return;

        List<ForeignKeyPsiEdge> returnPath = findDeletePath(target, owner, new HashSet<>());
        if (returnPath == null) return;

        Set<PsiClass> cycleClasses = new HashSet<>();
        cycleClasses.add(owner);
        cycleClasses.add(target);
        returnPath.forEach(edge -> cycleClasses.add(edge.target()));

        boolean restrictive = !sourceAction.equals("CASCADE")
                || returnPath.stream().anyMatch(edge -> !edge.action().equals("CASCADE"));
        String entities = cycleClasses.stream()
                .map(PsiClass::getName)
                .sorted()
                .reduce((left, right) -> left + " <-> " + right)
                .orElse(owner.getName());
        if (restrictive) {
            problem(holder, sourceForeignKey,
                    "Restrictive foreign-key deletion cycle detected among " + entities
                            + "; use SET_NULL on one relationship or remove the reverse reference",
                    ProblemHighlightType.GENERIC_ERROR);
        } else {
            problem(holder, sourceForeignKey,
                    "Cascading foreign-key deletion cycle detected among " + entities
                            + "; deleting one entity may recursively delete the entire cycle",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
    }

    private static List<ForeignKeyPsiEdge> findDeletePath(PsiClass current, PsiClass destination,
                                                           Set<PsiClass> visiting) {
        if (!visiting.add(current)) return null;
        try {
            List<PsiField> fields = new ArrayList<>(List.of(current.getFields()));
            fields.sort(Comparator.comparing(PsiField::getName));
            for (PsiField field : fields) {
                PsiAnnotation foreignKey = field.getAnnotation(FOREIGN_KEY);
                if (foreignKey == null || !isPersistent(field)) continue;
                String action = deleteAction(foreignKey);
                if (action.equals("SET_NULL")) continue;
                PsiClass target = foreignKeyTarget(field, foreignKey);
                if (target == null || current.equals(target)) continue;

                ForeignKeyPsiEdge edge = new ForeignKeyPsiEdge(target, action);
                if (target.equals(destination)) return new ArrayList<>(List.of(edge));
                List<ForeignKeyPsiEdge> tail = findDeletePath(target, destination, visiting);
                if (tail != null) {
                    List<ForeignKeyPsiEdge> path = new ArrayList<>();
                    path.add(edge);
                    path.addAll(tail);
                    return path;
                }
            }
            return null;
        } finally {
            visiting.remove(current);
        }
    }

    private static PsiClass foreignKeyTarget(PsiField field, PsiAnnotation foreignKey) {
        PsiClass declared = declaredClassValue(foreignKey, "entity");
        if (declared != null) return declared;
        PsiClass fieldType = PsiTypesUtil.getPsiClass(field.getType());
        return fieldType != null && hasAnnotation(fieldType.getModifierList(), ENTITY) ? fieldType : null;
    }

    private static String deleteAction(PsiAnnotation foreignKey) {
        if (enumValueEndsWith(foreignKey, "onDelete", "SET_NULL")) return "SET_NULL";
        if (enumValueEndsWith(foreignKey, "onDelete", "CASCADE")) return "CASCADE";
        return "RESTRICT";
    }

    private static Map<String, PsiField> resolvableFields(PsiClass psiClass) {
        Map<String, PsiField> fields = new HashMap<>();
        for (PsiField field : psiClass.getFields()) {
            if (!isPersistent(field)) continue;
            fields.put(field.getName(), field);
            fields.put(physicalName(field), field);
        }
        return fields;
    }

    private static PsiField primaryKey(PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            PsiAnnotation column = field.getAnnotation(COLUMN);
            if (hasAnnotation(field.getModifierList(), ID) || booleanValue(column, "primaryKey", false)) return field;
        }
        return null;
    }

    private static boolean isPersistent(PsiField field) {
        return hasAnnotation(field.getModifierList(), COLUMN)
                || hasAnnotation(field.getModifierList(), ID)
                || hasAnnotation(field.getModifierList(), TEMPORAL);
    }

    private static String physicalName(PsiField field) {
        PsiAnnotation column = field.getAnnotation(COLUMN);
        String name = stringValue(column, "name");
        if (name != null && !name.isBlank()) return name;
        PsiAnnotation temporal = field.getAnnotation(TEMPORAL);
        name = stringValue(temporal, "name");
        return name == null || name.isBlank() ? field.getName() : name;
    }

    private static boolean nullable(PsiField field) {
        if (hasAnnotation(field.getModifierList(), ID)) return false;
        PsiAnnotation column = field.getAnnotation(COLUMN);
        if (column != null) return booleanValue(column, "nullable", true);
        PsiAnnotation temporal = field.getAnnotation(TEMPORAL);
        return temporal == null || booleanValue(temporal, "nullable", true);
    }

    private static List<PsiAnnotation> annotations(PsiModifierList modifierList, String qualifiedName) {
        List<PsiAnnotation> result = new ArrayList<>();
        if (modifierList == null) return result;
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            if (qualifiedName.equals(annotation.getQualifiedName())) result.add(annotation);
        }
        return result;
    }

    private static boolean hasAnnotation(PsiModifierList modifierList, String qualifiedName) {
        return modifierList != null && modifierList.findAnnotation(qualifiedName) != null;
    }

    private static List<AnnotationString> stringArray(PsiAnnotation annotation, String attribute) {
        List<AnnotationString> values = new ArrayList<>();
        PsiAnnotationMemberValue member = annotation.findDeclaredAttributeValue(attribute);
        if (member == null) return values;
        if (member instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                String value = constantString(initializer);
                if (value != null) values.add(new AnnotationString(value, initializer));
            }
        } else {
            String value = constantString(member);
            if (value != null) values.add(new AnnotationString(value, member));
        }
        return values;
    }

    private static String stringValue(PsiAnnotation annotation, String attribute) {
        if (annotation == null) return null;
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
        return value == null ? null : constantString(value);
    }

    private static String constantString(PsiAnnotationMemberValue value) {
        Object constant = JavaPsiFacade.getInstance(value.getProject())
                .getConstantEvaluationHelper().computeConstantExpression(value);
        return constant instanceof String string ? string : null;
    }

    private static boolean booleanValue(PsiAnnotation annotation, String attribute, boolean defaultValue) {
        if (annotation == null) return defaultValue;
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
        if (value == null) return defaultValue;
        Object constant = JavaPsiFacade.getInstance(value.getProject())
                .getConstantEvaluationHelper().computeConstantExpression(value);
        return constant instanceof Boolean bool ? bool : defaultValue;
    }

    private static PsiClass declaredClassValue(PsiAnnotation annotation, String attribute) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attribute);
        if (value instanceof PsiClassObjectAccessExpression classObject) {
            return PsiTypesUtil.getPsiClass(classObject.getOperand().getType());
        }
        return null;
    }

    private static boolean enumValueEndsWith(PsiAnnotation annotation, String attribute, String suffix) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
        return value != null && value.getText().endsWith(suffix);
    }

    private static String boxedCanonicalName(PsiType type) {
        return switch (type.getCanonicalText()) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "boolean" -> "java.lang.Boolean";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "char" -> "java.lang.Character";
            default -> type.getCanonicalText();
        };
    }

    private static void problem(ProblemsHolder holder, com.intellij.psi.PsiElement element, String message) {
        holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static void problem(ProblemsHolder holder, com.intellij.psi.PsiElement element, String message,
                                ProblemHighlightType highlightType) {
        holder.registerProblem(element, message, highlightType);
    }

    private record AnnotationString(String value, PsiAnnotationMemberValue element) {
    }

    private record ForeignKeyPsiEdge(PsiClass target, String action) {
    }
}
