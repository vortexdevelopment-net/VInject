package net.vortexdevelopment.transformer;

import org.apache.bcel.Const;
import org.apache.bcel.Constants;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.SimpleElementValue;
import org.apache.bcel.generic.ALOAD;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.INVOKEINTERFACE;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.Type;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.sisu.space.asm.ClassReader;
import org.eclipse.sisu.space.asm.ClassWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Mojo(name = "transform-classes", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EntityTransformer extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
    private File testOutputDirectory;

    /**
     * Optional: explicitly specify the classes directory to process.
     * If set, this overrides the phase-based directory selection.
     */
    @Parameter
    private File classesDirectory;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution session;

    @Override
    public void execute() throws MojoExecutionException {
        String phase = session != null ? session.getLifecyclePhase() : null;

        // If classesDirectory is explicitly configured, use it
        if (classesDirectory != null) {
            if (classesDirectory.exists()) {
                Set<File> classFiles = getClassFiles(classesDirectory);
                for (File classFile : classFiles) {
                    try {
                        processClassFile(classFile);
                    } catch (IOException | MojoExecutionException e) {
                        throw new MojoExecutionException("Failed to process class file: " + classFile, e);
                    }
                }
            } else {
                getLog().warn("Configured classes directory does not exist: " + classesDirectory);
            }
            return;
        }

        // Fall back to phase-based directory selection
        // Process main classes if in process-classes phase
        if (phase != null && phase.equals("process-classes")) {
            if (outputDirectory != null && outputDirectory.exists()) {
                Set<File> classFiles = getClassFiles(outputDirectory);
                for (File classFile : classFiles) {
                    try {
                        processClassFile(classFile);
                    } catch (IOException | MojoExecutionException e) {
                        throw new MojoExecutionException("Failed to process class file: " + classFile, e);
                    }
                }
            }
        }

        // Process test classes if in process-test-classes phase
        if (phase != null && phase.equals("process-test-classes")) {
            if (testOutputDirectory != null && testOutputDirectory.exists()) {
                Set<File> testClassFiles = getClassFiles(testOutputDirectory);
                for (File classFile : testClassFiles) {
                    try {
                        processClassFile(classFile);
                    } catch (IOException | MojoExecutionException e) {
                        throw new MojoExecutionException("Failed to process class file: " + classFile, e);
                    }
                }
            } else {
                getLog().warn("Test output directory does not exist: " + testOutputDirectory);
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

    private void processClassFile(File classFile) throws IOException, MojoExecutionException {
        try (InputStream inputStream = new FileInputStream(classFile)) {
            ClassParser parser = new ClassParser(inputStream, classFile.getName());
            JavaClass javaClass = parser.parse();

            String className = javaClass.getClassName();
            getLog().debug("Processing class file: " + className + " from " + classFile.getName());

            boolean hasEntityAnnotation = Arrays.stream(javaClass.getAnnotationEntries())
                    .anyMatch(annotation -> annotation.getAnnotationType().equals("Lnet/vortexdevelopment/vinject/annotation/database/Entity;"));

            // Check for @YamlId annotation on any field
            boolean hasYamlIdField = false;
            for (Field field : javaClass.getFields()) {
                org.apache.bcel.classfile.AnnotationEntry[] annotations = field.getAnnotationEntries();
                for (org.apache.bcel.classfile.AnnotationEntry annotation : annotations) {
                    String annotationType = annotation.getAnnotationType();
                    if (annotationType.equals("Lnet/vortexdevelopment/vinject/annotation/yaml/YamlId;")) {
                        hasYamlIdField = true;
                        break;
                    }
                }
                if (hasYamlIdField) break;
            }

            // Check for @Inject annotation on any field
            boolean hasInjectField = false;
            for (Field field : javaClass.getFields()) {
                org.apache.bcel.classfile.AnnotationEntry[] annotations = field.getAnnotationEntries();
                for (org.apache.bcel.classfile.AnnotationEntry annotation : annotations) {
                    String annotationType = annotation.getAnnotationType();
                    if (annotationType.equals("Lnet/vortexdevelopment/vinject/annotation/Inject;")) {
                        hasInjectField = true;
                        break;
                    }
                }
                if (hasInjectField) break;
            }

            if (hasEntityAnnotation) {
                byte[] modifiedBytes = modifyEntityClass(javaClass, classFile);
                if (modifiedBytes != null) {
                    try (OutputStream outputStream = new FileOutputStream(classFile)) {
                        outputStream.write(modifiedBytes);
                    }
                }
            } else if (hasYamlIdField) {
                byte[] modifiedBytes = modifyYamlConfigClass(javaClass, classFile);
                if (modifiedBytes != null) {
                    try (OutputStream outputStream = new FileOutputStream(classFile)) {
                        outputStream.write(modifiedBytes);
                    }
                }
            } else if (hasInjectField) {
                byte[] modifiedBytes = modifyComponentClass(javaClass, classFile);
                if (modifiedBytes != null) {
                    try (OutputStream outputStream = new FileOutputStream(classFile)) {
                        outputStream.write(modifiedBytes);
                    }
                }
            }
        }
    }

    private byte[] modifyYamlConfigClass(JavaClass javaClass, File classFile) throws IOException {
        // Modify YAML config classes to add synthetic fields for batch tracking
        ClassGen classGen = new ClassGen(javaClass);
        ConstantPoolGen constantPool = classGen.getConstantPool();

        // Check if fields already exist to avoid duplicates
        boolean hasBatchField = Arrays.stream(classGen.getFields())
                .anyMatch(field -> field.getName().equals("__vinject_yaml_batch_id"));
        boolean hasFileField = Arrays.stream(classGen.getFields())
                .anyMatch(field -> field.getName().equals("__vinject_yaml_file"));

        // Add __vinject_yaml_batch_id field
        if (!hasBatchField) {
            FieldGen batchField = new FieldGen(
                    Constants.ACC_PRIVATE,
                    Type.STRING,
                    "__vinject_yaml_batch_id",
                    constantPool
            );
            classGen.addField(batchField.getField());
        }

        // Add __vinject_yaml_file field
        if (!hasFileField) {
            FieldGen fileField = new FieldGen(
                    Constants.ACC_PRIVATE,
                    Type.STRING,
                    "__vinject_yaml_file",
                    constantPool
            );
            classGen.addField(fileField.getField());
        }

        // Write the modified class to byte array
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            classGen.getJavaClass().dump(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Modifies @Component classes to generate @Inject annotated setters for fields with @Inject annotation.
     * This enables automatic setter generation for dependency injection, providing clean circular dependency handling.
     */
    private byte[] modifyComponentClass(JavaClass javaClass, File classFile) throws IOException {
        ClassGen classGen = new ClassGen(javaClass);
        ConstantPoolGen constantPool = classGen.getConstantPool();
        InstructionFactory factory = new InstructionFactory(classGen, constantPool);

        getLog().info("Generating setters for @Inject fields in @Component class: " + javaClass.getClassName());

        // Find all fields with @Inject annotation
        for (Field field : classGen.getFields()) {
            // Skip static fields
            if ((field.getAccessFlags() & Constants.ACC_STATIC) != 0) {
                continue;
            }

            // Check if field has @Inject annotation
            boolean hasInjectAnnotation = Arrays.stream(field.getAnnotationEntries())
                    .anyMatch(annotation -> annotation.getAnnotationType().equals("Lnet/vortexdevelopment/vinject/annotation/Inject;"));

            if (!hasInjectAnnotation) {
                continue;
            }

            String fieldName = field.getName();
            Type fieldType = field.getType();
            String setterName = "set" + capitalize(fieldName);

            // Check if setter already exists
            boolean hasSetter = Arrays.stream(classGen.getMethods())
                    .anyMatch(m -> m.getName().equals(setterName) && m.getArgumentTypes().length == 1);

            if (hasSetter) {
                getLog().debug("Setter " + setterName + " already exists, skipping generation");
                continue;
            }

            getLog().info("Generating setter: " + setterName + " for field: " + fieldName);

            // Create the setter method
            MethodGen setter = new MethodGen(
                    Const.ACC_PUBLIC,
                    Type.VOID,
                    new Type[]{fieldType},
                    new String[]{fieldName},
                    setterName,
                    classGen.getClassName(),
                    new InstructionList(),
                    constantPool
            );

            // Build setter body: this.fieldName = fieldName;
            InstructionList il = setter.getInstructionList();
            il.append(new ALOAD(0));   // this
            il.append(InstructionFactory.createLoad(fieldType, 1));   // load parameter
            il.append(new PUTFIELD(constantPool.addFieldref(
                    classGen.getClassName(),
                    fieldName,
                    fieldType.getSignature()
            )));
            il.append(InstructionFactory.createReturn(Type.VOID));

            setter.setMaxStack();
            setter.setMaxLocals();
            setter.removeLineNumbers();
            setter.removeLocalVariables();

            classGen.addMethod(setter.getMethod());
            il.dispose();
        }

        // Write the modified class to byte array
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            classGen.getJavaClass().dump(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] modifyEntityClass(JavaClass javaClass, File classFile) throws IOException {
        // Modify the class using BCEL
        ClassGen classGen = new ClassGen(javaClass);
        ConstantPoolGen constantPool = classGen.getConstantPool();

        // Check if the class is already transformed by checking for modifiedFields
        boolean alreadyTransformed = Arrays.stream(classGen.getFields())
                .anyMatch(field -> field.getName().equals("modifiedFields"));

        if (alreadyTransformed) {
            getLog().info("Class " + javaClass.getClassName() + " is already transformed, skipping.");
            return null;
        }

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

        // Modify all methods
        for (Method method : classGen.getMethods()) {
            if (method.getName().equals("<init>")) {
                modifyConstructor(classGen, method, constantPool, modifiedFieldName);
                continue;
            }

            // Skip methods with @PostConstruct
            if (Arrays.stream(method.getAnnotationEntries()).anyMatch(a -> a.getAnnotationType().equals("Lnet/vortexdevelopment/vinject/annotation/PostConstruct;"))) {
                continue;
            }

            // Check for @Cached annotation
            String cachedFieldName = extractCachedAnnotationValue(method);
            if (cachedFieldName != null) {
                enhanceCachedMethod(classGen, method, constantPool, modifiedFieldName, cachedFieldName);
                continue;
            }

            // If it's a setter, enhance it
            if (method.getName().startsWith("set") && method.getArgumentTypes().length == 1) {
                enhanceSetter(classGen, method, constantPool, modifiedFieldName);
            }
        }

        // Add missing getters/setters if they don't exist
        addMissingGettersSetters(classGen, constantPool, modifiedFieldName);

        // Add reset method to clear modifiedFields
        addResetMethod(classGen, constantPool);

        // Add isFieldModified method
        addIsFieldModifiedMethod(classGen, constantPool);

        // Write the modified class to byte array
        byte[] bcelBytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            classGen.getJavaClass().dump(out);
            bcelBytes = out.toByteArray();
        }

        ClassReader cr = new ClassReader(bcelBytes);

        ClassWriter cw = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
        );

        cr.accept(cw, ClassReader.EXPAND_FRAMES);

        byte[] finalBytes = cw.toByteArray();
        return finalBytes;
    }

    private void enhanceSetter(ClassGen classGen, Method method, ConstantPoolGen constantPool, String modifiedFieldName) {
        MethodGen mg = new MethodGen(method, classGen.getClassName(), constantPool);
        InstructionList il = mg.getInstructionList();
        if (il == null) return;

        InstructionHandle start = il.getStart();
        if (start == null) return;

        // Try to find the field name from the setter name (e.g., setAmount -> amount)
        String methodName = method.getName();
        String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);

        // However, it's safer to look at the PUTFIELD instruction if it exists
        for (InstructionHandle handle : il.getInstructionHandles()) {
            if (handle.getInstruction() instanceof PUTFIELD) {
                PUTFIELD putField = (PUTFIELD) handle.getInstruction();
                fieldName = putField.getFieldName(constantPool);
                break;
            }
        }

        InstructionList inject = new InstructionList();
        inject.append(new ALOAD(0)); // this
        inject.append(new GETFIELD(constantPool.addFieldref(
                classGen.getClassName(),
                modifiedFieldName,
                "Ljava/util/Set;"))
        );
        inject.append(new PUSH(constantPool, fieldName)); // "fieldName"
        inject.append(new INVOKEINTERFACE(constantPool.addInterfaceMethodref(
                "java/util/Set", "add", "(Ljava/lang/Object;)Z"), (short) 2));
        inject.append(new POP());

        il.insert(start, inject);

        mg.setMaxStack();
        mg.setMaxLocals();
        mg.removeLineNumbers();
        mg.removeLocalVariables();
        classGen.replaceMethod(method, mg.getMethod());
        il.dispose();
    }

    /**
     * Extracts the field name from a @Cached annotation on a method.
     * 
     * @param method The method to check for @Cached annotation
     * @return The field name from the annotation value, or null if not found
     */
    private String extractCachedAnnotationValue(Method method) {
        AnnotationEntry[] annotations = method.getAnnotationEntries();
        for (AnnotationEntry annotation : annotations) {
            String annotationType = annotation.getAnnotationType();
            if (annotationType.equals("Lnet/vortexdevelopment/vinject/annotation/database/Cached;")) {
                ElementValuePair[] pairs = annotation.getElementValuePairs();
                for (ElementValuePair pair : pairs) {
                    if (pair.getNameString().equals("value")) {
                        if (pair.getValue() instanceof SimpleElementValue) {
                            SimpleElementValue simpleValue = (SimpleElementValue) pair.getValue();
                            return simpleValue.getValueString();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Enhances a method annotated with @Cached to track field modifications.
     * 
     * @param classGen The class generator
     * @param method The method to enhance
     * @param constantPool The constant pool
     * @param modifiedFieldName The name of the modifiedFields field
     * @param fieldName The field name to track (from @Cached annotation)
     */
    private void enhanceCachedMethod(ClassGen classGen, Method method, ConstantPoolGen constantPool, String modifiedFieldName, String fieldName) {
        MethodGen mg = new MethodGen(method, classGen.getClassName(), constantPool);
        InstructionList il = mg.getInstructionList();
        if (il == null) return;

        InstructionHandle start = il.getStart();
        if (start == null) return;

        InstructionList inject = new InstructionList();
        inject.append(new ALOAD(0)); // this
        inject.append(new GETFIELD(constantPool.addFieldref(
                classGen.getClassName(),
                modifiedFieldName,
                "Ljava/util/Set;"))
        );
        inject.append(new PUSH(constantPool, fieldName)); // "fieldName"
        inject.append(new INVOKEINTERFACE(constantPool.addInterfaceMethodref(
                "java/util/Set", "add", "(Ljava/lang/Object;)Z"), (short) 2));
        inject.append(new POP());

        il.insert(start, inject);

        mg.setMaxStack();
        mg.setMaxLocals();
        mg.removeLineNumbers();
        mg.removeLocalVariables();
        classGen.replaceMethod(method, mg.getMethod());
        il.dispose();
    }

    /**
     * Adds getter and setter methods for each field if they don't already exist.
     */
    private void addMissingGettersSetters(ClassGen classGen, ConstantPoolGen constantPool, String modifiedFieldName) {
        Field[] fields = classGen.getFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            if (fieldName.equals(modifiedFieldName)) {
                continue;
            }
            Type fieldType = field.getType();

            String getterName = "get" + capitalize(fieldName);
            String setterName = "set" + capitalize(fieldName);

            boolean hasGetter = Arrays.stream(classGen.getMethods()).anyMatch(m -> m.getName().equals(getterName));
            boolean hasSetter = Arrays.stream(classGen.getMethods()).anyMatch(m -> m.getName().equals(setterName));

            // Add getter if missing
            if (!hasGetter) {
                MethodGen getter = new MethodGen(
                        Constants.ACC_PUBLIC,
                        fieldType,
                        Type.NO_ARGS,
                        null,
                        getterName,
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
                getter.removeLineNumbers();
                getter.removeLocalVariables();
                classGen.addMethod(getter.getMethod());
                getter.getInstructionList().dispose();
            }

            // Add setter if missing
            if (!hasSetter) {
                MethodGen setter = new MethodGen(
                        Const.ACC_PUBLIC,
                        Type.VOID,
                        new Type[]{fieldType},
                        new String[]{fieldName},
                        setterName,
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
                        modifiedFieldName,
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
                setter.removeLineNumbers();
                setter.removeLocalVariables();
                classGen.addMethod(setter.getMethod());
                setter.getInstructionList().dispose();
            }
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
        resetMethod.removeLineNumbers();
        resetMethod.removeLocalVariables();
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
        isFieldModifiedMethod.removeLineNumbers();
        isFieldModifiedMethod.removeLocalVariables();
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
        instructionList.append(superCall, initInstructions);
        getLog().info("Appended initInstructions after superCall in constructor");

        // Update the method
        methodGen.setInstructionList(instructionList);
        methodGen.setMaxStack();
        methodGen.setMaxLocals();
        methodGen.removeLineNumbers();
        methodGen.removeLocalVariables();
        classGen.replaceMethod(method, methodGen.getMethod());
    }

}