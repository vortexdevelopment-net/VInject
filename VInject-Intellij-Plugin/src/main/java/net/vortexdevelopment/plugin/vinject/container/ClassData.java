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

    private static final Set<String> EXCLUDED_TYPES = Set.of(
            "java.lang.Object",
            "java.io.Serializable",
            "java.lang.Cloneable",
            "java.lang.AutoCloseable"
    );

    private String qualifiedName;
    private Set<String> beans = ConcurrentHashMap.newKeySet(); //Provided beans by the class

    public ClassData(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public ClassData(PsiClass psiClass, PsiAnnotation annotation) {
        this.qualifiedName = psiClass.getQualifiedName();

        //Check if the class is annotated with @Service
        if (Objects.equals(annotation.getQualifiedName(), BaseComponents.SERVICE)) {
            //Get all Beans
            for (PsiMethod method : psiClass.getMethods()) {
                if (method.getAnnotation(BaseComponents.BEAN) != null) {
                    PsiAnnotation beanAnnotation = method.getAnnotation(BaseComponents.BEAN);
                    if (beanAnnotation != null) {
                        //Add return type of the method
                        PsiType returnType = method.getReturnType();
                        if (returnType != null) {
                            beans.add(returnType.getCanonicalText());
                            PsiClass returnClass = com.intellij.psi.util.PsiUtil.resolveClassInType(returnType);
                            addHierarchy(returnClass);
                        }
                    }
                }
            }
        }

        //Check for component annotations
        if (Objects.equals(annotation.getQualifiedName(), BaseComponents.COMPONENT)) {
            beans.add(psiClass.getQualifiedName());
            addHierarchy(psiClass);
        }

        //Check for repository annotations
        if (Objects.equals(annotation.getQualifiedName(), BaseComponents.REPOSITORY)) {
            beans.add(psiClass.getQualifiedName());
        }

        //Root annotation
        if (Objects.equals(annotation.getQualifiedName(), BaseComponents.ROOT)) {
            //Add the package name
            beans.add(psiClass.getQualifiedName());
        }
    }

    public boolean isClassProvided(PsiClass psiClass) {
        return qualifiedName.equals(psiClass.getQualifiedName()) || beans.contains(psiClass.getQualifiedName());
    }

    private void addHierarchy(PsiClass psiClass) {
        if (psiClass == null) {
            return;
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            String name = superClass.getQualifiedName();
            if (name != null && !EXCLUDED_TYPES.contains(name) && beans.add(name)) {
                addHierarchy(superClass);
            }
        }
    }


    @Override
    public String toString() {
        return "ClassData{" +
               "qualifiedName='" + qualifiedName + '\'' +
               ", beans=" + beans +
               '}';
    }
}
