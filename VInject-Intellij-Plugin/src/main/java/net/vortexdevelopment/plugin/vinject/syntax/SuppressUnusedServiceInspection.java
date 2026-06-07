package net.vortexdevelopment.plugin.vinject.syntax;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressUnusedServiceInspection implements InspectionSuppressor {

    private boolean checkClass(@NotNull PsiClass psiClass) {
        // Check if the class is annotated with @Service
        if (psiClass.getAnnotation("net.vortexdevelopment.vinject.annotation.Service") != null) {
            // Check if it contains any @Bean methods
            for (PsiMethod method : psiClass.getMethods()) {
                if (method.getAnnotation("net.vortexdevelopment.vinject.annotation.Bean") != null) {
                    // Mark the class as "used" by skipping unused highlighting
                    return true; // No problems reported, so no highlighting as unused
                }
            }
        }
        //Non service classes check here:
        return ClassDataManager.isClassProvided(psiClass) && psiClass.getAnnotation("net.vortexdevelopment.vinject.annotation.Service") == null;
    }

    private boolean checkMethod(@NotNull PsiMethod method) {
        //Check @Bean annotation
        if (method.getAnnotation("net.vortexdevelopment.vinject.annotation.Bean") != null) {
            //Check if the method is in a Service class
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                // Mark the method as "used" by skipping unused highlighting
                return containingClass.getAnnotation("net.vortexdevelopment.vinject.annotation.Service") != null; // No problems reported, so no highlighting as unused
            }
        }
        return false;
    }

    private boolean checkField(@NotNull PsiField field) {
        //Check if the field is annotated with @Inject
        if (field.getAnnotation("net.vortexdevelopment.vinject.annotation.Inject") != null) {
            //Check if the field is used in a Service class
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && ClassDataManager.isComponentClass(containingClass)) {
                //get type of the field
                PsiType type = field.getType();
                if (type instanceof PsiClassType psiClassType) {
                    //Check if the type is a bean class
                    PsiClass psiClass = psiClassType.resolve();
                    if (psiClass != null) {
                        return ClassDataManager.isClassProvided(psiClass);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if ("UnusedDeclaration".equals(toolId)) {
            if (element instanceof PsiIdentifier psiIdentifier) {
                if (psiIdentifier.getParent() instanceof PsiClass psiClass) {
                    return checkClass(psiClass);
                }
                if (psiIdentifier.getParent() instanceof PsiMethod psiMethod) {
                    return checkMethod(psiMethod);
                }
                if (psiIdentifier.getParent() instanceof PsiField psiField) {
                    return checkField(psiField);
                }
            }
        }
        if ("ConstantConditions".equals(toolId)) {

            //Check if the field used is annotated with @Inject and the dependency is present
            if (element instanceof PsiIdentifier psiIdentifier) {
                PsiElement parent = psiIdentifier.getParent();
                if (parent instanceof PsiReferenceExpression referenceExpression) {
                    PsiExpression qualifier = referenceExpression.getQualifierExpression();
                    if (qualifier instanceof PsiReferenceExpression qualifierReference) {
                        PsiElement resolved = qualifierReference.resolve();

                        // Check if the resolved element is a field
                        if (resolved instanceof PsiField psiField) {
                            // Check if the field is annotated with @Inject
                            return checkField(psiField);
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return new SuppressQuickFix[0];
    }
}
