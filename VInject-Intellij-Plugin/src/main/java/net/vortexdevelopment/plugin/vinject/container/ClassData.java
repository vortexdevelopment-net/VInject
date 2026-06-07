package net.vortexdevelopment.plugin.vinject.container;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClassData {

    private String qualifiedName;
    private Set<String> beans = ConcurrentHashMap.newKeySet(); //Provided beans by the class

    public ClassData(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public ClassData(PsiClass psiClass, PsiAnnotation annotation) {
        this.qualifiedName = psiClass.getQualifiedName();

        //Check if the class is annotated with @Service
        if (Objects.equals(annotation.getQualifiedName(), "net.vortexdevelopment.vinject.annotation.component.Service")) {
            //Get all Beans
            for (PsiMethod method : psiClass.getMethods()) {
                if (method.getAnnotation("net.vortexdevelopment.vinject.annotation.Bean") != null) {
                    PsiAnnotation beanAnnotation = method.getAnnotation("net.vortexdevelopment.vinject.annotation.Bean");
                    if (beanAnnotation != null) {
                        List<String> registerSubclasses = ClassDataManager.getClassArray(beanAnnotation, "registerSubclasses");
                        beans.addAll(registerSubclasses);

                        //Add return type of the method
                        PsiType returnType = method.getReturnType();
                        if (returnType != null) {
                            beans.add(returnType.getCanonicalText());
                        }
                    }
                }
            }
        }

        //Check for component annotations
        if (Objects.equals(annotation.getQualifiedName(), "net.vortexdevelopment.vinject.annotation.component.Component")) {
            //registerSubclasses
            beans.addAll(ClassDataManager.getClassArray(annotation, "registerSubclasses"));
            beans.add(psiClass.getQualifiedName());
        }

        //Check for repository annotations
        if (Objects.equals(annotation.getQualifiedName(), "net.vortexdevelopment.vinject.annotation.component.Repository")) {
            //registerSubclasses
            beans.addAll(ClassDataManager.getClassArray(annotation, "registerSubclasses"));
        }

        //Root annotation
        if (Objects.equals(annotation.getQualifiedName(), "net.vortexdevelopment.vinject.annotation.component.Root")) {
            //Add the package name
            beans.add(psiClass.getQualifiedName());
        }
    }

    public boolean isClassProvided(PsiClass psiClass) {
        return qualifiedName.equals(psiClass.getQualifiedName()) || beans.contains(psiClass.getQualifiedName());
    }


    @Override
    public String toString() {
        return "ClassData{" +
               "qualifiedName='" + qualifiedName + '\'' +
               ", beans=" + beans +
               '}';
    }
}
