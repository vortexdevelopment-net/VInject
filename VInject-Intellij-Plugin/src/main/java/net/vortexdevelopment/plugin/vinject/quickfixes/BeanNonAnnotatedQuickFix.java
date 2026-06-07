package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class BeanNonAnnotatedQuickFix implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Add @Bean annotation";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiMethod method)) {
            return;
        }

        // Ensure write action for modifying the PSI tree
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> {
            // Add the annotation to the method
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@net.vortexdevelopment.vinject.annotation.Bean", method);
            method.getModifierList().addBefore(annotation, method.getModifierList().getFirstChild());

            // Optimize imports after adding the annotation
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(method);
        });
    }
}
