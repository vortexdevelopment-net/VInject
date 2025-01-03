package net.vortexdevelopment.transformer;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.INVOKEINTERFACE;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.INVOKESTATIC;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.Type;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "transform-classes", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EntityTransformer extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
    private File testOutputDirectory;


    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution session;

    @Override
    public void execute() throws MojoExecutionException {
        String phase = session.getLifecyclePhase();


        if (phase.equals("process-classes")) {
            Set<File> classFiles = getClassFiles(outputDirectory);
            for (File classFile : classFiles) {
                try {
                    processClassFile(classFile);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to process class file: " + classFile, e);
                }
            }
            return;
        }

        Set<File> testClassFiles = getClassFiles(testOutputDirectory);
        for (File classFile : testClassFiles) {
            try {
                processClassFile(classFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to process class file: " + classFile, e);
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Set<File> getClassFiles(File outputDirectory) {
        Set<File> classFiles = new HashSet<>();
        try {
            Files.walk(outputDirectory.toPath())
                    .filter(path -> path.toFile().isFile() && path.toString().endsWith(".class"))
                    .map(Path::toFile)
                    .forEach(classFiles::add);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classFiles;
    }

    private void processClassFile(File classFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(classFile)) {
            ClassParser parser = new ClassParser(inputStream, classFile.getName());
            JavaClass javaClass = parser.parse();

            boolean hasDataAnnotation = Arrays.stream(javaClass.getAnnotationEntries())
                    .anyMatch(annotation -> annotation.getAnnotationType().equals("Lnet/vortexdevelopment/vinject/annotation/database/Entity;"));


            if (hasDataAnnotation) {
                byte[] modifiedBytes = modifyClass(javaClass, classFile);
                try (OutputStream outputStream = new FileOutputStream(classFile)) {
                    outputStream.write(modifiedBytes);
                }
            }
        }
    }

    private byte[] modifyClass(JavaClass javaClass, File classFile) throws IOException {
        // Modify the class using BCEL
        ClassGen classGen = new ClassGen(javaClass);
        ConstantPoolGen constantPool = classGen.getConstantPool();

        // Remove all existing no-arg <init> methods to avoid duplicates  (CHANGED)
        removeNoArgConstructors(classGen);

        // Clear all existing methods
        Method[] methods = classGen.getMethods();
        for (Method method : methods) {
            classGen.removeMethod(method);
        }

        // Add the modifiedFields field
        String modifiedFieldName = "modifiedFields";
        String modifiedFieldType = "Ljava/util/Set;";
        FieldGen modifiedFieldsField =
                new FieldGen(Constants.ACC_PRIVATE,
                        new ObjectType("java.util.Set"),
                        modifiedFieldName,
                        constantPool);
        classGen.addField(modifiedFieldsField.getField());

        // Add a single default constructor that calls super() and initializes modifiedFields
        InstructionList constructorInstructions = new InstructionList();

        // Call super()
        constructorInstructions.append(new ALOAD(0));
        int superInit = constantPool.addMethodref(classGen.getSuperclassName(), "<init>", "()V");
        constructorInstructions.append(new INVOKESPECIAL(superInit));

        // Initialize modifiedFields using ConcurrentHashMap.newKeySet()
        constructorInstructions.append(new ALOAD(0)); // this
        int newKeySetRef = constantPool.addMethodref(
                "java.util.concurrent.ConcurrentHashMap",
                "newKeySet",
                "()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"
        );
        constructorInstructions.append(new INVOKESTATIC(newKeySetRef)); // Call static method
        int modifiedFieldRef = constantPool.addFieldref(
                classGen.getClassName(),
                "modifiedFields",
                "Ljava/util/Set;"
        );
        constructorInstructions.append(new PUTFIELD(modifiedFieldRef)); // this.modifiedFields = newKeySet()

        // Return
        constructorInstructions.append(InstructionFactory.createReturn(Type.VOID));

        // Create and add constructor
        MethodGen constructor = new MethodGen(
                Constants.ACC_PUBLIC,
                Type.VOID,
                Type.NO_ARGS,
                null,
                "<init>",
                classGen.getClassName(),
                constructorInstructions,
                constantPool
        );
        constructor.setMaxStack();
        classGen.addMethod(constructor.getMethod());
        constructorInstructions.dispose();

        // Modify existing fields (adding getter, setter, and modification tracking)
        Field[] fields = classGen.getFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if (fieldName.equals("modifiedFields")) {
                continue;
            }
            Type fieldType = field.getType();

            // Add getter
            MethodGen getter = new MethodGen(
                    Constants.ACC_PUBLIC,
                    fieldType,
                    Type.NO_ARGS,
                    null,
                    "get" + capitalize(fieldName),
                    classGen.getClassName(),
                    new InstructionList(),
                    constantPool
            );
            getter.getInstructionList().append(new ALOAD(0));
            getter.getInstructionList().append(new GETFIELD(constantPool.addFieldref(
                    classGen.getClassName(),
                    fieldName,
                    fieldType.getSignature()
            )));
            getter.getInstructionList().append(InstructionFactory.createReturn(fieldType));
            getter.setMaxStack();
            classGen.addMethod(getter.getMethod());
            getter.getInstructionList().dispose();

            // Add setter
            MethodGen setter = new MethodGen(
                    Constants.ACC_PUBLIC,
                    Type.VOID,
                    new Type[]{fieldType},
                    new String[]{fieldName},
                    "set" + capitalize(fieldName),
                    classGen.getClassName(),
                    new InstructionList(),
                    constantPool
            );
            setter.getInstructionList().append(new ALOAD(0));   // this
            setter.getInstructionList().append(new ALOAD(1));   // value
            setter.getInstructionList().append(new PUTFIELD(constantPool.addFieldref(
                    classGen.getClassName(),
                    fieldName,
                    fieldType.getSignature()
            )));

            // Add modifiedFields.add("fieldName");
            setter.getInstructionList().append(new ALOAD(0)); // this
            setter.getInstructionList().append(new GETFIELD(constantPool.addFieldref(
                    classGen.getClassName(),
                    "modifiedFields",
                    "Ljava/util/Set;"))
            );
            setter.getInstructionList().append(new LDC(constantPool.addString(fieldName))); // "fieldName"
            setter.getInstructionList().append(new INVOKEINTERFACE(constantPool.addInterfaceMethodref(
                    "java/util/Set", "add", "(Ljava/lang/Object;)Z"), (short) 2));
            // Discard the boolean result of Set.add(...)
            setter.getInstructionList().append(new POP());

            setter.getInstructionList().append(InstructionFactory.createReturn(Type.VOID));
            setter.setMaxStack();
            classGen.addMethod(setter.getMethod());
            setter.getInstructionList().dispose();
        }

        // Add reset method to clear modifiedFields
        MethodGen resetMethod = new MethodGen(
                Constants.ACC_PUBLIC,
                Type.VOID,
                Type.NO_ARGS,
                null,
                "resetModifiedFields",
                classGen.getClassName(),
                new InstructionList(),
                constantPool
        );
        resetMethod.getInstructionList().append(new ALOAD(0));
        resetMethod.getInstructionList().append(new GETFIELD(constantPool.addFieldref(
                classGen.getClassName(),
                "modifiedFields",
                "Ljava/util/Set;"))
        );
        resetMethod.getInstructionList().append(new INVOKEINTERFACE(constantPool.addInterfaceMethodref(
                "java/util/Set", "clear", "()V"), (short) 1));
        resetMethod.getInstructionList().append(InstructionFactory.createReturn(Type.VOID));
        resetMethod.setMaxStack();
        classGen.addMethod(resetMethod.getMethod());
        resetMethod.getInstructionList().dispose();

        //Add isFieldModified method
        MethodGen isFieldModifiedMethod = new MethodGen(
                Constants.ACC_PUBLIC,
                Type.BOOLEAN,
                new Type[]{Type.STRING},
                new String[]{"fieldName"},
                "isFieldModified",
                classGen.getClassName(),
                new InstructionList(),
                constantPool
        );

        // this
        isFieldModifiedMethod.getInstructionList().append(new ALOAD(0));

        // get this.modifiedFields
        isFieldModifiedMethod.getInstructionList().append(
                new GETFIELD(constantPool.addFieldref(
                        classGen.getClassName(),
                        "modifiedFields",
                        "Ljava/util/Set;"))
        );

        // load the passed-in fieldName onto the stack
        isFieldModifiedMethod.getInstructionList().append(new ALOAD(1));

        // call Set.contains(Object)
        isFieldModifiedMethod.getInstructionList().append(new INVOKEINTERFACE(
                constantPool.addInterfaceMethodref("java/util/Set", "contains", "(Ljava/lang/Object;)Z"),
                2 // the number of argument slots (Set itself + the fieldName)
        ));

        // return the boolean result
        isFieldModifiedMethod.getInstructionList().append(InstructionFactory.createReturn(Type.BOOLEAN));

        // finalize and add the method
        isFieldModifiedMethod.setMaxStack();
        classGen.addMethod(isFieldModifiedMethod.getMethod());
        isFieldModifiedMethod.getInstructionList().dispose();




        // Write the modified class to byte array
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            classGen.getJavaClass().dump(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Helper to remove all existing no-arg constructors so that we do not generate duplicates.
     */
    private void removeNoArgConstructors(ClassGen classGen) {
        Method[] methods = classGen.getMethods();
        java.util.List<Method> toRemove = new ArrayList<>();
        for (Method method : methods) {
            // Look for ()V
            if ("<init>".equals(method.getName()) && method.getArgumentTypes().length == 0) {
                toRemove.add(method);
            }
        }
        for (Method m : toRemove) {
            classGen.removeMethod(m);
        }
    }
}