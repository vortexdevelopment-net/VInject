package net.vortexdevelopment.plugin.vinject.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import net.vortexdevelopment.plugin.vinject.Plugin;
import org.jetbrains.annotations.NotNull;

public class EntityPrimitiveTypeFix implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return "Convert to Reference";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();

        if (!(element instanceof PsiField psiField)) {
            return;
        }

        if (!(psiField.getType() instanceof PsiPrimitiveType primitiveType)) {
            return;
        }

        //Get the primitive type and convert it to a reference
        PsiType type = primitiveType.getBoxedType(psiField);

        if (type == null) {
            return;
        }

        Plugin.runWriteAction(() -> {
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiTypeElement newTypeElement = elementFactory.createTypeElement(type);
            PsiTypeElement oldTypeElement = psiField.getTypeElement();

            if (oldTypeElement != null) {
                oldTypeElement.replace(newTypeElement);
            }
        });
    }
}
