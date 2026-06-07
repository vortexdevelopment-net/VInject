package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MoveFieldToConstructorParameter implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Move field injection to constructor parameter";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiField psiField)) {
            return;
        }

        Plugin.runWriteAction(() -> {
            PsiClass containingClass = psiField.getContainingClass();
            if (containingClass == null) {
                return;
            }

            // Get field type and name
            PsiType fieldType = psiField.getType();
            String fieldName = psiField.getName();
            if (fieldName == null) {
                return;
            }

            // Find the constructor that uses this field (or use the first constructor, or create one)
            PsiMethod[] constructors = containingClass.getConstructors();
            PsiMethod targetConstructor = null;
            
            // First, try to find a constructor that references this field
            for (PsiMethod constructor : constructors) {
                if (isFieldReferencedInMethod(psiField, constructor)) {
                    targetConstructor = constructor;
                    break;
                }
            }
            
            // Get factory for creating PSI elements
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiElementFactory factory = facade.getElementFactory();

            // If no constructor uses the field, use the first constructor or create a default one
            if (targetConstructor == null) {
                if (constructors.length > 0) {
                    targetConstructor = constructors[0];
                } else {
                    // Create a new constructor
                    targetConstructor = factory.createConstructor();
                    containingClass.add(targetConstructor);
                }
            }

            // Check if field is used outside of constructors
            boolean isFieldUsedOutsideConstructors = isFieldUsedOutsideConstructors(psiField, containingClass, project);

            // Add parameter to constructor (without @Inject annotation)
            String parameterTypeText = fieldType.getCanonicalText();
            String parameterText = parameterTypeText + " " + fieldName;
            PsiParameter newParameter = factory.createParameterFromText(parameterText, targetConstructor);
            
            // Add parameter to constructor
            PsiParameterList parameterList = targetConstructor.getParameterList();
            parameterList.add(newParameter);

            // Add assignment in constructor body: this.fieldName = fieldName;
            // Only add assignment if field is still needed (used outside constructors)
            if (isFieldUsedOutsideConstructors) {
                PsiCodeBlock constructorBody = targetConstructor.getBody();
                if (constructorBody != null) {
                    String assignmentText = "this." + fieldName + " = " + fieldName + ";";
                    PsiStatement assignment = factory.createStatementFromText(assignmentText, targetConstructor);
                    
                    // Add assignment at the beginning of the constructor body
                    PsiStatement[] statements = constructorBody.getStatements();
                    if (statements.length > 0) {
                        constructorBody.addBefore(assignment, statements[0]);
                    } else {
                        constructorBody.add(assignment);
                    }
                }
            }

            // Remove @Inject annotation from field or delete field entirely
            if (isFieldUsedOutsideConstructors) {
                // Field is used elsewhere, just remove @Inject annotation
                if (psiField.getModifierList() != null) {
                    PsiAnnotation injectAnnotation = psiField.getModifierList()
                            .findAnnotation("net.vortexdevelopment.vinject.annotation.Inject");
                    if (injectAnnotation != null) {
                        injectAnnotation.delete();
                    }
                }
            } else {
                // Field is only used in constructors, delete it entirely
                psiField.delete();
            }

            // Optimize imports
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(containingClass);
        });
    }

    private boolean isFieldReferencedInMethod(@NotNull PsiField field, @NotNull PsiMethod method) {
        // Check if the field is referenced in the method body
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }

        // Find all references to the field within the method body
        Collection<PsiReferenceExpression> references = PsiTreeUtil.findChildrenOfType(body, PsiReferenceExpression.class);
        for (PsiReferenceExpression ref : references) {
            PsiElement resolved = ref.resolve();
            if (resolved != null && resolved.equals(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a field is used anywhere outside of constructors.
     * @param field The field to check
     * @param containingClass The class containing the field
     * @param project The project
     * @return true if the field is used outside constructors, false otherwise
     */
    private boolean isFieldUsedOutsideConstructors(@NotNull PsiField field, @NotNull PsiClass containingClass, @NotNull Project project) {
        // Search for all references to the field
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Query<PsiReference> query = ReferencesSearch.search(field, scope);
        
        for (PsiReference reference : query) {
            PsiElement element = reference.getElement();
            if (element == null) {
                continue;
            }
            
            // Skip if this is the field declaration itself
            PsiField parentField = PsiTreeUtil.getParentOfType(element, PsiField.class);
            if (parentField != null && parentField.equals(field)) {
                // This is the field declaration itself, skip it
                continue;
            }
            
            // Check if this reference is inside a constructor
            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (containingMethod != null && containingMethod.isConstructor()) {
                // This reference is in a constructor, skip it
                continue;
            }
            
            // Found a reference outside of constructors
            return true;
        }
        
        return false;
    }
}
