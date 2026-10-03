package net.vortexdevelopment.plugin.vinject.syntax;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import net.vortexdevelopment.plugin.vinject.container.BaseComponents;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;
import net.vortexdevelopment.plugin.vinject.quickfixes.BeanNonAnnotatedQuickFix;
import net.vortexdevelopment.plugin.vinject.quickfixes.BeanUsedInNonServiceClass;
import net.vortexdevelopment.plugin.vinject.quickfixes.EntityPrimitiveTypeFix;
import net.vortexdevelopment.plugin.vinject.quickfixes.InjectToNonComponentClass;
import net.vortexdevelopment.plugin.vinject.quickfixes.MoveFieldToConstructorParameter;
import net.vortexdevelopment.plugin.vinject.quickfixes.RemoveInjectNonComponentClass;
import net.vortexdevelopment.plugin.vinject.quickfixes.RemoveNonComponentConstructorParameters;
import net.vortexdevelopment.plugin.vinject.quickfixes.RemoveUnusedInjectField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ComponentHighlighter extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Ensure @Inject is only used in VInject-managed classes";
    }

    @Override
    public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
        //Check if the method is in a Service class
        //If any methods are not annotated with @Bean, show error
        if (!isServiceClass(method.getContainingClass()) || method.getAnnotation(BaseComponents.BEAN) != null) {
            return null;
        }

        return new ProblemDescriptor[]{manager.createProblemDescriptor(
                method,
                "Method is not annotated with @Bean in a Service class",
                true,
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                isOnTheFly,
                new BeanNonAnnotatedQuickFix()
        )};
    }

    @Override
    public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        addBeanOutsideServiceProblem(psiClass, manager, isOnTheFly, descriptors);
        addInvalidConstructorParameterProblems(psiClass, manager, isOnTheFly, descriptors);
        addMissingEntityIdProblem(psiClass, manager, isOnTheFly, descriptors);
        return toProblemDescriptors(descriptors);
    }

    @Override
    public ProblemDescriptor @Nullable [] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        PsiClass containingClass = field.getContainingClass();

        if (isInjectionTarget(containingClass)) {
            addInjectionProblems(field, manager, isOnTheFly, descriptors);
        } else if (field.getAnnotation(BaseComponents.INJECT) != null) {
            descriptors.add(manager.createProblemDescriptor(
                    field,
                    "You can only use @Inject in VInject-managed classes such as @Component or @Entity",
                    true,
                    ProblemHighlightType.GENERIC_ERROR,
                    isOnTheFly,
                    new InjectToNonComponentClass(), new RemoveInjectNonComponentClass()
            ));
        }

        addEntityPrimitiveTypeProblem(field, manager, isOnTheFly, descriptors);
        return toProblemDescriptors(descriptors);
    }

    private void addBeanOutsideServiceProblem(@NotNull PsiClass psiClass, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        if (isServiceClass(psiClass) || !hasBeanMethodOutsideService(psiClass)) {
            return;
        }

        descriptors.add(manager.createProblemDescriptor(
                psiClass,
                "Class not annotated with @Service and @Bean annotation used",
                true,
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                isOnTheFly,
                new BeanUsedInNonServiceClass()
        ));
    }

    private boolean hasBeanMethodOutsideService(@NotNull PsiClass psiClass) {
        return Arrays.stream(psiClass.getMethods())
                .anyMatch(method -> method.getAnnotation(BaseComponents.BEAN) != null
                        && !isServiceClass(method.getContainingClass()));
    }

    private void addInvalidConstructorParameterProblems(@NotNull PsiClass psiClass,
            @NotNull InspectionManager manager, boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        if (!ClassDataManager.isComponentClass(psiClass)
                || psiClass.getAnnotation(BaseComponents.INJECTABLE) != null) {
            return;
        }

        for (PsiMethod constructor : psiClass.getConstructors()) {
            for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                if (isValidConstructorParameter(parameter)) {
                    continue;
                }
                descriptors.add(createInvalidConstructorParameterProblem(parameter, manager, isOnTheFly));
            }
        }
    }

    private boolean isValidConstructorParameter(@NotNull PsiParameter parameter) {
        if (parameter.getType() instanceof PsiPrimitiveType) {
            return false;
        }
        if (parameter.getType() instanceof PsiClassType classType) {
            return ClassDataManager.isClassProvided(classType.resolve());
        }
        return true;
    }

    private ProblemDescriptor createInvalidConstructorParameterProblem(@NotNull PsiParameter parameter,
            @NotNull InspectionManager manager, boolean isOnTheFly) {
        return manager.createProblemDescriptor(
                parameter,
                "Component class constructor can only have other components as parameters",
                true,
                ProblemHighlightType.GENERIC_ERROR,
                isOnTheFly,
                new RemoveNonComponentConstructorParameters()
        );
    }

    private void addMissingEntityIdProblem(@NotNull PsiClass psiClass, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        if (psiClass.getAnnotation(BaseComponents.ENTITY) == null || hasIdField(psiClass)) {
            return;
        }

        PsiElement identifier = psiClass.getNameIdentifier();
        descriptors.add(manager.createProblemDescriptor(
                identifier == null ? psiClass : identifier,
                "Entity class must have an @Id field",
                true,
                ProblemHighlightType.GENERIC_ERROR,
                isOnTheFly
        ));
    }

    private boolean hasIdField(@NotNull PsiClass psiClass) {
        return Arrays.stream(psiClass.getAllFields())
                .anyMatch(field -> field.getAnnotation(BaseComponents.ID) != null);
    }

    private void addInjectionProblems(@NotNull PsiField field, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        if (field.getAnnotation(BaseComponents.INJECT) == null
                || !(field.getType() instanceof PsiClassType classType)) {
            return;
        }

        addMissingProviderProblem(field, classType, manager, isOnTheFly, descriptors);
        addUnusedInjectionProblem(field, manager, isOnTheFly, descriptors);
        addConstructorInjectionProblem(field, manager, isOnTheFly, descriptors);
    }

    private void addMissingProviderProblem(@NotNull PsiField field, @NotNull PsiClassType classType,
            @NotNull InspectionManager manager, boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        PsiClass providedClass = classType.resolve();
        if (providedClass == null || ClassDataManager.isClassProvided(providedClass)) {
            return;
        }

        descriptors.add(manager.createProblemDescriptor(
                field,
                "No Bean class found for " + providedClass.getName(),
                true,
                ProblemHighlightType.GENERIC_ERROR,
                isOnTheFly
        ));
    }

    private void addUnusedInjectionProblem(@NotNull PsiField field, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        Project project = field.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        if (ReferencesSearch.search(field, scope).findFirst() != null || field.hasAnnotation("lombok.Getter")) {
            return;
        }

        descriptors.add(manager.createProblemDescriptor(
                field,
                "Injected field is never used",
                true,
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                isOnTheFly,
                new RemoveUnusedInjectField()
        ));
    }

    private void addConstructorInjectionProblem(@NotNull PsiField field, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }

        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null || !Arrays.stream(containingClass.getConstructors())
                .anyMatch(constructor -> isFieldReferencedInConstructor(field, constructor))) {
            return;
        }

        descriptors.add(manager.createProblemDescriptor(
                field,
                "Field injection cannot be used in constructor. Field will be null during constructor execution.",
                true,
                ProblemHighlightType.GENERIC_ERROR,
                isOnTheFly,
                new MoveFieldToConstructorParameter()
        ));
    }

    private void addEntityPrimitiveTypeProblem(@NotNull PsiField field, @NotNull InspectionManager manager,
            boolean isOnTheFly, @NotNull List<ProblemDescriptor> descriptors) {
        PsiClass containingClass = field.getContainingClass();
        PsiAnnotation columnAnnotation = field.getAnnotation(BaseComponents.COLUMN);
        PsiAnnotation temporalAnnotation = field.getAnnotation(BaseComponents.TEMPORAL);
        if (containingClass == null
                || containingClass.getAnnotation(BaseComponents.ENTITY) == null
                || !(field.getType() instanceof PsiPrimitiveType)
                || columnAnnotation != null && temporalAnnotation != null) {
            return;
        }

        descriptors.add(manager.createProblemDescriptor(
                field,
                "Primitive types are not allowed in Entity classes",
                true,
                ProblemHighlightType.GENERIC_ERROR,
                isOnTheFly,
                new EntityPrimitiveTypeFix()
        ));
    }

    private boolean isInjectionTarget(@Nullable PsiClass containingClass) {
        return containingClass != null && (ClassDataManager.isComponentClass(containingClass)
                || containingClass.getAnnotation(BaseComponents.ENTITY) != null);
    }

    private boolean isServiceClass(@Nullable PsiClass psiClass) {
        return psiClass != null && psiClass.getAnnotation(BaseComponents.SERVICE) != null;
    }

    private ProblemDescriptor @Nullable [] toProblemDescriptors(@NotNull List<ProblemDescriptor> descriptors) {
        return descriptors.isEmpty() ? null : descriptors.toArray(new ProblemDescriptor[0]);
    }

    /**
     * Checks if a field is referenced within a constructor body.
     * @param field The field to check
     * @param constructor The constructor to check
     * @return true if the field is referenced in the constructor body
     */
    private boolean isFieldReferencedInConstructor(@NotNull PsiField field, @NotNull PsiMethod constructor) {
        PsiCodeBlock body = constructor.getBody();
        if (body == null) {
            return false;
        }

        return PsiTreeUtil.findChildrenOfType(body, PsiReferenceExpression.class).stream()
                .map(PsiReferenceExpression::resolve)
                .anyMatch(field::equals);
    }
}
