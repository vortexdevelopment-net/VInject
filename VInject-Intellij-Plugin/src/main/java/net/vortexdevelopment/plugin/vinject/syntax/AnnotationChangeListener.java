package net.vortexdevelopment.plugin.vinject.syntax;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import net.vortexdevelopment.plugin.vinject.Plugin;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnnotationChangeListener implements PsiTreeChangeListener {


    private void checkForAnnotationChange(PsiClass oldClass, PsiClass newClass) {

        boolean isOldClassComponent = ClassDataManager.isComponentClass(oldClass);
        boolean isNewClassComponent = ClassDataManager.isComponentClass(newClass);

        if (isOldClassComponent && !isNewClassComponent) {
            //Remove class from the beans
            //Plugin.removeBean(oldClass);
        }

        if (!isOldClassComponent && isNewClassComponent) {
            //Add class to the beans
            //Plugin.addBean(newClass);
        }
    }

    private void checkForAnnotationChange(PsiAnnotation oldAnnotation, PsiAnnotation newAnnotation) {
        String oldAnnotationName = oldAnnotation.getQualifiedName();
        String newAnnotationName = newAnnotation.getQualifiedName();
        
        // Check if both annotations are component annotations
        if (oldAnnotationName != null && newAnnotationName != null &&
            ClassDataManager.COMPONENT_ANNOTATIONS.contains(oldAnnotationName) &&
            ClassDataManager.COMPONENT_ANNOTATIONS.contains(newAnnotationName) &&
            oldAnnotationName.equals(newAnnotationName)) {

            List<String> oldRegisterSubclasses = ClassDataManager.getClassArray(oldAnnotation, "registerSubclasses");
            List<String> newRegisterSubclasses = ClassDataManager.getClassArray(newAnnotation, "registerSubclasses");

            //Get the added or removed classes
            Set<String> addedClasses = new HashSet<>(newRegisterSubclasses);
            addedClasses.removeAll(oldRegisterSubclasses);
            Set<String> removedClasses = new HashSet<>(oldRegisterSubclasses);
            removedClasses.removeAll(newRegisterSubclasses);

            //Add the added classes to the beans
            for (String className : addedClasses) {
                //Plugin.addBean(className);
            }

            //Remove the removed classes from the beans
            for (String className : removedClasses) {
                //Plugin.removeBean(className);
            }
        }
    }

    @Override
    public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        //Get project from the event's file
        if (event.getFile() == null) {
            return;
        }
        Project project = event.getFile().getProject();
        
        //Check if container is already disposed
        if (project.isDisposed()) {
            return;
        }

        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(() -> {
            if (project.isDisposed() || !event.getFile().isValid()) {
                return;
            }

            if (DumbService.isDumb(project)) {
                DumbService.getInstance(project).runWhenSmart(() -> {
                    ClassDataManager.processFileChange(event.getFile());
                });
            } else {
                ClassDataManager.processFileChange(event.getFile());
            }
        });
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    }
}
