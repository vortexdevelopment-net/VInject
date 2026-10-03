package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import net.vortexdevelopment.plugin.vinject.Plugin;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveNonComponentConstructorParameters implements LocalQuickFix {
    
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Remove non component constructor parameters";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiParameter psiParameter)) return;

        //Remove the parameters
        Plugin.runWriteAction(() -> {
            List<PsiParameter> remove = new ArrayList<>();
            PsiMethod psiConstructor = (PsiMethod) psiParameter.getParent().getParent();

            for (PsiParameter parameter : psiConstructor.getParameterList().getParameters()) {
                if (parameter.getType() instanceof PsiClassType psiClassType) {
                    if (!ClassDataManager.isClassProvided(psiClassType.resolve())) {
                        remove.add(parameter);
                    }
                }
                if (parameter.getType() instanceof PsiPrimitiveType psiPrimitiveType) {
                    remove.add(parameter);
                }
            }

            //Remove the parameters
            for (PsiParameter parameter : remove) {
                parameter.delete();
            }
        });
    }
}
