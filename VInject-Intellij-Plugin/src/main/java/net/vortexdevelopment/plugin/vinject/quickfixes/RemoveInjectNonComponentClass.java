package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

public class RemoveInjectNonComponentClass implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Remove @Inject annotation from field";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        //Remove @Inject annotation from the field
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiField psiField)) {
            return;
        }

        Plugin.runWriteAction(() -> {
            //resolve the class
            PsiClass psiClass = psiField.getContainingClass();

            //Remove @Inject annotation from field
            // Find and remove the @Inject annotation
            if (psiField.getModifierList() != null) {
                PsiAnnotation injectAnnotation = psiField.getModifierList().findAnnotation("net.vortexdevelopment.vinject.annotation.Inject");
                if (injectAnnotation != null) {
                    injectAnnotation.delete();
                }
            }

            // Optimize imports after adding the annotation
            if (psiClass != null) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
            } else {
                System.out.println("Class is null");
            }
        });
    }
}
