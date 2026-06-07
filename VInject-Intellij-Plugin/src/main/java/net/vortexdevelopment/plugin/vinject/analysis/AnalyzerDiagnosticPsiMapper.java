package net.vortexdevelopment.plugin.vinject.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import net.vortexdevelopment.vinject.analyzer.Diagnostic;
import net.vortexdevelopment.vinject.analyzer.DiagnosticLocation;
import org.jetbrains.annotations.Nullable;

public final class AnalyzerDiagnosticPsiMapper {

    private AnalyzerDiagnosticPsiMapper() {
    }

    public static @Nullable PsiElement findElement(Project project, Diagnostic diagnostic) {
        DiagnosticLocation location = diagnostic.getLocation();
        if (location == null || location.getClassName() == null) {
            return null;
        }

        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(location.getClassName(), GlobalSearchScope.projectScope(project));
        if (psiClass == null || location.getMemberName() == null) {
            return psiClass;
        }

        for (PsiField field : psiClass.getFields()) {
            if (location.getMemberName().equals(field.getName())) {
                return field;
            }
        }

        for (PsiMethod method : psiClass.getMethods()) {
            if (location.getMemberName().equals(method.getName())) {
                return method;
            }
        }

        return psiClass;
    }
}
