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
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
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
import java.util.List;
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

        // Add the modifiedFields field with inline initialization
        String modifiedFieldName = "modifiedFields";
        ObjectType setType = new ObjectType("java.util.Set");
        FieldGen modifiedFieldsField = new FieldGen(
                Constants.ACC_PRIVATE,
                setType,
                modifiedFieldName,
                constantPool
        );
        classGen.addField(modifiedFieldsField.getField());

        // Modify all constructors to include initialization
        for (Method method : classGen.getMethods()) {
            if (method.getName().equals("<init>")) {
                modifyConstructor(classGen, method, constantPool, modifiedFieldName);
            }
        }


        // Remove all existing methods except constructors
        Method[] methods = classGen.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals("<init>")) {
                classGen.removeMethod(method);
            }
        }

        // Modify existing fields (adding getter, setter, and modification tracking)
        addGettersSetters(classGen, constantPool);

        // Add reset method to clear modifiedFields
        addResetMethod(classGen, constantPool);

        // Add isFieldModified method
        addIsFieldModifiedMethod(classGen, constantPool);

        // Write the modified class to byte array
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            classGen.getJavaClass().dump(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Adds getter and setter methods for each field, excluding modifiedFields.
     */
    private void addGettersSetters(ClassGen classGen, ConstantPoolGen constantPool) {
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
            getter.setMaxLocals();
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
            setter.getInstructionList().append(new PUSH(constantPool, fieldName)); // "fieldName"
            setter.getInstructionList().append(new INVOKEINTERFACE(constantPool.addInterfaceMethodref(
                    "java/util/Set", "add", "(Ljava/lang/Object;)Z"), (short) 2));
            // Discard the boolean result of Set.add(...)
            setter.getInstructionList().append(new POP());

            setter.getInstructionList().append(InstructionFactory.createReturn(Type.VOID));
            setter.setMaxStack();
            setter.setMaxLocals();
            classGen.addMethod(setter.getMethod());
            setter.getInstructionList().dispose();
        }
    }

    /**
     * Adds a resetModifiedFields method to clear the modifiedFields set.
     */
    private void addResetMethod(ClassGen classGen, ConstantPoolGen constantPool) {
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
        resetMethod.setMaxLocals();
        classGen.addMethod(resetMethod.getMethod());
        resetMethod.getInstructionList().dispose();
    }

    /**
     * Adds an isFieldModified method to check if a field has been modified.
     */
    private void addIsFieldModifiedMethod(ClassGen classGen, ConstantPoolGen constantPool) {
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

        // this.modifiedFields.contains(fieldName)
        isFieldModifiedMethod.getInstructionList().append(new ALOAD(0));
        isFieldModifiedMethod.getInstructionList().append(
                new GETFIELD(constantPool.addFieldref(
                        classGen.getClassName(),
                        "modifiedFields",
                        "Ljava/util/Set;"))
        );
        isFieldModifiedMethod.getInstructionList().append(new ALOAD(1)); // fieldName
        isFieldModifiedMethod.getInstructionList().append(new INVOKEINTERFACE(
                constantPool.addInterfaceMethodref("java/util/Set", "contains", "(Ljava/lang/Object;)Z"),
                2 // the number of argument slots (Set itself + the fieldName)
        ));
        isFieldModifiedMethod.getInstructionList().append(InstructionFactory.createReturn(Type.BOOLEAN));

        // Finalize and add the method
        isFieldModifiedMethod.setMaxStack();
        isFieldModifiedMethod.setMaxLocals();
        classGen.addMethod(isFieldModifiedMethod.getMethod());
        isFieldModifiedMethod.getInstructionList().dispose();
    }

    private void modifyConstructor(ClassGen classGen, Method method, ConstantPoolGen constantPool, String fieldName) {
        MethodGen methodGen = new MethodGen(method, classGen.getClassName(), constantPool);
        InstructionList instructionList = methodGen.getInstructionList();
        InstructionFactory factory = new InstructionFactory(classGen, constantPool);

        // Find the "super()" call in the constructor
        InstructionHandle superCall = null;
        for (InstructionHandle handle : instructionList.getInstructionHandles()) {
            if (handle.getInstruction() instanceof INVOKESPECIAL) {
                INVOKESPECIAL invokeSpecial = (INVOKESPECIAL) handle.getInstruction();
                if (invokeSpecial.getMethodName(constantPool).equals("<init>")) {
                    superCall = handle;
                    break;
                }
            }
        }

        if (superCall == null) {
            throw new IllegalStateException("No super() call found in constructor.");
        }

        // Create instructions to initialize modifiedFields using Collections.newSetFromMap()
        InstructionList initInstructions = new InstructionList();
        initInstructions.append(InstructionConstants.ALOAD_0); // Push 'this' onto the stack
        initInstructions.append(factory.createNew("java.util.concurrent.ConcurrentHashMap"));
        initInstructions.append(InstructionConstants.DUP);
        initInstructions.append(factory.createInvoke(
                "java.util.concurrent.ConcurrentHashMap",
                "<init>",
                Type.VOID,
                Type.NO_ARGS,
                Constants.INVOKESPECIAL
        ));
        initInstructions.append(factory.createInvoke(
                "java.util.Collections",
                "newSetFromMap",
                Type.getType("Ljava/util/Set;"),
                new Type[]{Type.getType("Ljava/util/Map;")},
                Constants.INVOKESTATIC
        ));
        initInstructions.append(factory.createPutField(
                classGen.getClassName(),
                fieldName,
                Type.getType("Ljava/util/Set;")
        ));

        // Insert initialization instructions after the "super()" call
        instructionList.insert(superCall, initInstructions);

        // Update the method
        methodGen.setInstructionList(instructionList);
        methodGen.setMaxStack();
        methodGen.setMaxLocals();
        classGen.replaceMethod(method, methodGen.getMethod());
    }

}