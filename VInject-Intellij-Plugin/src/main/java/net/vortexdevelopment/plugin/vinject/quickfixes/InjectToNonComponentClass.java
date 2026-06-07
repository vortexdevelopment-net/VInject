package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

public class InjectToNonComponentClass implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Add @Component annotation to class";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        //Add @Component annotation to the class

        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiField psiField)) {
            return;
        }

        //resolve the class
        PsiClass psiClass = psiField.getContainingClass();

        // Ensure write action for modifying the PSI tree
        Plugin.runWriteAction(() -> {
            // Add the annotation to the method
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@net.vortexdevelopment.vinject.annotation.Component", psiClass);
            psiClass.getModifierList().addBefore(annotation, psiClass.getModifierList().getFirstChild());

            // Optimize imports after adding the annotation
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiClass);
        });
    }
}
