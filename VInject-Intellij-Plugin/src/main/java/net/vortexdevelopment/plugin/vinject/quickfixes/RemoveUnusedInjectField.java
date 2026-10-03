package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

public class RemoveUnusedInjectField implements LocalQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Remove unused field";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getEndElement();
        if (!(element instanceof PsiField psiField)) return;

        Plugin.runWriteAction(psiField::delete);
    }
}
