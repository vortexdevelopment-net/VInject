package net.vortexdevelopment.plugin.vinject.annotation;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntityAugmentProvider extends PsiAugmentProvider {

    @Override
    public <Psi extends PsiElement> @NotNull List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type, @Nullable String nameHint) {

        if (!(element instanceof PsiClass psiClass)) {
            return Collections.emptyList();
        }

        PsiAnnotation entityAnnotation = psiClass.getAnnotation("net.vortexdevelopment.vinject.annotation.database.Entity");
        if (entityAnnotation == null) {
            return Collections.emptyList();
        }

        if (type.equals(PsiField.class)) {
            return buildSyntheticFields(psiClass);
        }

        if (type.equals(PsiMethod.class)) {
            return buildSyntheticMethods(psiClass);
        }
        return Collections.emptyList();
    }

    private <T extends PsiElement> List<T> buildSyntheticFields(@NotNull PsiClass psiClass) {
        Project project = psiClass.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        // Example: private Set<String> modifiedFields = ConcurrentHashMap.newKeySet();
        PsiType setStringType =
                factory.createTypeFromText("java.util.Set<java.lang.String>", psiClass);

        // Provide initializer
        LightFieldBuilder fieldWithInitializer = new LightFieldBuilder(psiClass.getManager(), "modifiedFields", setStringType) {
            @Override
            public PsiExpression getInitializer() {
                return factory.createExpressionFromText(
                        "java.util.concurrent.ConcurrentHashMap.newKeySet()",
                        psiClass
                );
            }
        };

        @SuppressWarnings("unchecked")
        List<T> result = Collections.singletonList((T) fieldWithInitializer);
        return result;
    }

    private <T extends PsiElement> List<T> buildSyntheticMethods(@NotNull PsiClass psiClass) {
        Project project = psiClass.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        List<T> result = new ArrayList<>();

        // 1) resetModifiedFields()
        {
            LightMethodBuilder resetMethod = new LightMethodBuilder(
                    psiClass.getManager(), JavaLanguage.INSTANCE, "resetModifiedFields"
            ) {
                @Override
                public PsiCodeBlock getBody() {
                    return factory.createCodeBlockFromText(
                            "{ this.modifiedFields.clear(); }",
                            psiClass
                    );
                }
            };
            resetMethod.setMethodReturnType(PsiTypes.voidType());
            resetMethod.setContainingClass(psiClass);
            resetMethod.addModifier(PsiModifier.PUBLIC);

            // Cast to T
            result.add((T) resetMethod);
        }

        // 2) isFieldModified(String fieldName)
        {
            LightMethodBuilder isFieldModified = new LightMethodBuilder(
                    psiClass.getManager(), JavaLanguage.INSTANCE, "isFieldModified"
            ) {
                @Override
                public PsiCodeBlock getBody() {
                    return factory.createCodeBlockFromText(
                            "{ return this.modifiedFields.contains(fieldName); }",
                            psiClass
                    );
                }
            };
            isFieldModified.setMethodReturnType(PsiTypes.booleanType());
            isFieldModified.setContainingClass(psiClass);
            isFieldModified.addModifier(PsiModifier.PUBLIC);
            isFieldModified.addParameter("fieldName",
                    PsiType.getJavaLangString(psiClass.getManager(), psiClass.getResolveScope())
            );

            result.add((T) isFieldModified);
        }

        // 3) For each existing field, add a getter and setter
//        for (PsiField field : psiClass.getFields()) {
//            if (field.hasModifierProperty(PsiModifier.STATIC)) {
//                continue; // skip static fields
//            }
//            String fieldName = field.getName();
//
//            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
//
//            // a) Getter
//            String getterName = "get" + capitalized;
//            LightMethodBuilder getter = new LightMethodBuilder(
//                    psiClass.getManager(), JavaLanguage.INSTANCE, getterName
//            ) {
//                @Override
//                public PsiCodeBlock getBody() {
//                    return factory.createCodeBlockFromText(
//                            "{ return this." + fieldName + "; }",
//                            psiClass
//                    );
//                }
//
//                @Override
//                public @NotNull PsiElement getNavigationElement() {
//                    return field;
//                }
//            };
//            getter.setMethodReturnType(field.getType());
//            getter.setContainingClass(psiClass);
//            getter.addModifier(PsiModifier.PUBLIC);
//            result.add((T) getter);
//
//            // b) Setter
//            String setterName = "set" + capitalized;
//            LightMethodBuilder setter = new LightMethodBuilder(
//                    psiClass.getManager(), JavaLanguage.INSTANCE, setterName
//            ) {
//                @Override
//                public PsiCodeBlock getBody() {
//                    // Mark the field as modified
//                    return factory.createCodeBlockFromText(
//                            "{ this." + fieldName + " = value; "
//                                    + "  this.modifiedFields.add(\"" + fieldName + "\"); }",
//                            psiClass
//                    );
//                }
//
//                @Override
//                public @NotNull PsiElement getNavigationElement() {
//                    return field;
//                }
//            };
//            setter.setMethodReturnType(PsiTypes.voidType());
//            setter.setContainingClass(psiClass);
//            setter.addModifier(PsiModifier.PUBLIC);
//            setter.addParameter("value", field.getType());
//            result.add((T) setter);
//        }

        return result;
    }
}