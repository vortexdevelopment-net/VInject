package net.vortexdevelopment.plugin.vinject.syntax;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

public class ErrorFilter extends HighlightErrorFilter {



    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        System.out.println("Error Element: " + element.getErrorDescription());
        return false;
    }
}
