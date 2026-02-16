package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.NewLineAfter;
import net.vortexdevelopment.vinject.annotation.yaml.NewLineBefore;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class YamlConfig implements ConfigurationSection {
    private DocumentNode root;
    protected final Map<String, KeyedNode> pathIndex = new HashMap<>();
    private int indentStep = 2;
    private File file;
    private boolean dirty = false;

    public YamlConfig(DocumentNode root) {
        this.root = root;
        rebuildIndex();
    }

    public static YamlConfig load(String content) {
        YamlParser parser = new YamlParser();
        DocumentNode rootNode = parser.parse(content);
        YamlConfig config = new YamlConfig(rootNode);
        int discovered = parser.getDiscoveredIndentWidth();
        config.setIndentStep(discovered);
        return config;
    }

    public static YamlConfig load(File file) {
        // Create the file if it doesn't exist
        if (!file.exists()) {
            try {
                if (file.getParentFile() != null) {
                    Files.createDirectories(file.getParentFile().toPath());
                }
                Files.createFile(file.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create new config file: " + file.getAbsolutePath(), e);
            }
        }
        try {
            String content = Files.readString(file.toPath());
            YamlConfig config = load(content);
            config.file = file;
            return config;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setIndentStep(int indentStep) {
        this.indentStep = indentStep;
    }

    public void rebuildIndex() {
        pathIndex.clear();
        indexNodes(root);
    }

    private void indexNodes(YamlNode node) {
        if (node instanceof KeyedNode keyed) {
            pathIndex.put(keyed.getFullDotPath(), keyed);
        }
        for (YamlNode child : node.getChildren()) {
            indexNodes(child);
        }
    }

    public DocumentNode getRoot() {
        return root;
    }

    public KeyedNode getNode(String path) {
        return pathIndex.get(path);
    }

    @Override
    public ConfigurationSection getConfigurationSection() {
        return this;
    }

    public String render() {
        return render(RenderOptions.defaultOptions());
    }

    public String render(RenderOptions options) {
        return root.render(options);
    }

    public static ConfigurationSection fromMap(Map<String, Object> map) {
        DocumentNode doc = new DocumentNode();
        YamlConfig config = new YamlConfig(doc);
        config.updateSectionFromMap(doc, map);
        config.rebuildIndex();
        return config;
    }

    private void updateSectionFromMap(YamlNode parent, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            setRelative(parent, entry.getKey(), entry.getValue(), null, false, false);
        }
    }


    public void inject(String path, Object value, String comment) {
        if (!contains(path)) {
            setInternal(path, value, comment);
        } else if (comment != null && !comment.isEmpty()) {
            KeyedNode node = getNode(path);
            if (node != null) {
                ensureCommentPresent(node, comment);
            }
        }
    }

    @Override
    public void set(String path, Object value, String comment) {
        setInternal(path, value, comment);
    }

    private void setInternal(String path, Object value, String comment) {
        if (path == null) return;
        if (path.isEmpty()) {
            // Full document replace if path is empty
            if (value instanceof Map) {
                root.getChildren().clear();
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                updateSectionFromMap(root, map);
            } else if (value != null && (value.getClass().isAnnotationPresent(YamlItem.class) || value.getClass().isAnnotationPresent(YamlConfiguration.class))) {
                root.getChildren().clear();
                updateNodeFromObject(root, value);
            }
            rebuildIndex();
            dirty = true;
            return;
        }

        String[] parts = path.split("\\.");
        YamlNode current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String currentPath = getPathPrefix(parts, i + 1);
            KeyedNode existing = pathIndex.get(currentPath);

            if (existing == null) {
                existing = (KeyedNode) findChildByKey(current, part);
                if (existing != null) pathIndex.put(currentPath, existing);
            }

            int nextIndent = (current instanceof DocumentNode) ? 0 : current.getIndentation() + indentStep;

            if (existing == null) {
                if (i == parts.length - 1) {
                    if (isAbsent(value)) return;
                    YamlNode newNode = createNode(nextIndent, part, value);
                    if (comment != null && !comment.isEmpty()) {
                        current.addChild(new CommentNode(nextIndent, comment));
                    }
                    current.addChild(newNode);
                    if (newNode instanceof KeyedNode kn) pathIndex.put(currentPath, kn);
                } else {
                    if (isAbsent(value)) return;
                    SectionNode newNode = new SectionNode(nextIndent, part);
                    current.addChild(newNode);
                    pathIndex.put(currentPath, newNode);
                    current = newNode;
                }
                dirty = true;
            } else {
                if (i == parts.length - 1) {
                    if (isAbsent(value)) {
                        YamlNode parent = existing.getParent();
                        if (parent != null) {
                            parent.getChildren().remove(existing);
                            pathIndex.remove(currentPath);
                            rebuildIndex();
                            dirty = true;
                        }
                        return;
                    }
                    updateExistingNode(existing, currentPath, value, comment);
                    dirty = true;
                } else {
                    if (!(existing instanceof SectionNode)) {
                        YamlNode parent = existing.getParent();
                        if (parent != null) {
                            int idx = parent.getChildren().indexOf(existing);
                            SectionNode sn = new SectionNode(existing.getIndentation(), part);
                            parent.getChildren().set(idx, sn);
                            sn.setParent(parent);
                            pathIndex.put(currentPath, sn);
                            current = sn;
                        } else {
                            current = existing;
                        }
                    } else {
                        current = existing;
                    }
                }
            }
        }
        if (dirty) rebuildIndex();
    }

    private boolean isAbsent(Object value) {
        if (value == null) return true;
        if (value instanceof java.util.Collection && ((java.util.Collection<?>) value).isEmpty()) return true;
        if (value instanceof java.util.Map && ((java.util.Map<?, ?>) value).isEmpty()) return true;
        return false;
    }

    private void setRelative(YamlNode parent, String relativePath, Object value, String comment, boolean newLineBefore, boolean newLineAfter) {
        String[] parts = relativePath.split("\\.");
        YamlNode current = parent;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            YamlNode child = findChildByKey(current, part);

            // Indentation for the *next* node to be created.
            // If current is DocumentNode, children are at 0. Otherwise, parent's indent + indentStep.
            int nextIndent = (current instanceof DocumentNode) ? 0 : current.getIndentation() + indentStep;

            if (child == null) {
                if (i == parts.length - 1) { // Last part, create the value node
                    if (isAbsent(value)) return;
                    YamlNode newNode = createNode(nextIndent, part, value);
                    if (comment != null && !comment.isEmpty()) {
                        current.addChild(new CommentNode(nextIndent, comment));
                    }
                    current.addChild(newNode);
                    if (newNode instanceof KeyedNode kn) {
                        if (newLineBefore) {
                            ensureBlankLineBefore(kn);
                        }
                        if (newLineAfter) {
                            ensureBlankLineAfter(kn);
                        }
                    }
                } else { // Not last part, create a section node
                    if (isAbsent(value)) return;
                    SectionNode newNode = new SectionNode(nextIndent, part);
                    current.addChild(newNode);
                    current = newNode;
                }
            } else {
                if (i == parts.length - 1) { // Last part, update existing node
                    if (isAbsent(value)) {
                        current.getChildren().remove(child);
                        return;
                    }
                    if (child instanceof KeyedNode keyed) {
                        YamlNode updated = updateExistingNodeQuiet(keyed, value, comment);
                        if (updated instanceof KeyedNode kn) {
                            if (newLineBefore) {
                                ensureBlankLineBefore(kn);
                            }
                            if (newLineAfter) {
                                ensureBlankLineAfter(kn);
                            }
                        }
                    }
                } else { // Not last part, navigate to existing section
                    if (!(child instanceof SectionNode)) {
                        if (isAbsent(value)) return;
                        // Replace KV or List with Section if it's not already a section
                        int idx = current.getChildren().indexOf(child);
                        SectionNode sn = new SectionNode(nextIndent, part); // Use nextIndent for the new section
                        current.getChildren().set(idx, sn);
                        sn.setParent(current);
                        current = sn;
                    } else {
                        current = child;
                    }
                }
            }
        }
    }

    private YamlNode updateExistingNodeQuiet(KeyedNode existing, Object value, String comment) {
        if (comment != null && !comment.isEmpty()) {
            ensureCommentPresent(existing, comment);
        }

        YamlNode newNode = null;
        // If the new value is a complex type (Map, List, YamlItem annotated object),
        // or if the existing node is not a KeyValueNode, we need to replace the node.
        if (value instanceof Map || value instanceof List || (value != null && (value.getClass().isAnnotationPresent(YamlItem.class) || value.getClass().isAnnotationPresent(YamlConfiguration.class))) || !(existing instanceof KeyValueNode)) {
            YamlNode parent = existing.getParent();
            if (parent != null) {
                int idx = parent.getChildren().indexOf(existing);
                newNode = createNode(existing.getIndentation(), existing.getKey(), value);
                parent.getChildren().set(idx, newNode);
                newNode.setParent(parent);
            }
        } else if (existing instanceof KeyValueNode kv) {
            // If it's a simple value and existing is a KeyValueNode, just update its value.
            kv.setValue(value);
            newNode = kv;
        }
        // If newNode is still null, it means no change was made or existing was not a KeyedNode with a parent.
        return newNode != null ? newNode : existing;
    }

    private YamlNode findChildByKey(YamlNode parent, String key) {
        for (YamlNode child : parent.getChildren()) {
            if (child instanceof KeyedNode kn && kn.getKey().equals(key)) return child;
        }
        return null;
    }

    private void updateExistingNode(KeyedNode existing, String currentPath, Object value, String comment) {
        YamlNode updated = updateExistingNodeQuiet(existing, value, comment);
        if (updated instanceof KeyedNode kn) {
            pathIndex.put(currentPath, kn);
        }
        rebuildIndex();
    }


    private void ensureCommentPresent(KeyedNode node, String comment) {
        YamlNode parent = node.getParent();
        if (parent == null) return;
        int idx = parent.getChildren().indexOf(node);

        // Check if there's already a comment above
        boolean hasComment = false;
        for (int i = idx - 1; i >= 0; i--) {
            YamlNode sibling = parent.getChildren().get(i);
            if (sibling instanceof CommentNode) {
                hasComment = true;
                break;
            } else if (!(sibling instanceof BlankLineNode)) {
                // Stop if we hit another non-comment/non-blank-line node
                break;
            }
        }

        if (!hasComment) {
            // Add comment just before the node
            CommentNode cn = new CommentNode(node.getIndentation(), comment);
            parent.getChildren().add(idx, cn);
            cn.setParent(parent);
        }
    }

    private void ensureBlankLineBefore(KeyedNode node) {
        YamlNode parent = node.getParent();
        if (parent == null) return;
        int idx = parent.getChildren().indexOf(node);

        // Find the topmost comment or blank line above this node
        int firstRelevantIdx = idx;
        for (int i = idx - 1; i >= 0; i--) {
            YamlNode sibling = parent.getChildren().get(i);
            if (sibling instanceof BlankLineNode) {
                return; // Already has a blank line
            }
            if (sibling instanceof CommentNode) {
                firstRelevantIdx = i;
            } else {
                break;
            }
        }

        if (firstRelevantIdx == 0) return;

        // Check if there's already a blank line before the comments
        if (firstRelevantIdx > 0) {
            YamlNode beforeFirst = parent.getChildren().get(firstRelevantIdx - 1);
            if (beforeFirst instanceof BlankLineNode) return;
        }

        // Add blank line at firstRelevantIdx
        BlankLineNode bln = new BlankLineNode(node.getIndentation());
        parent.getChildren().add(firstRelevantIdx, bln);
        bln.setParent(parent);
    }

    private void ensureBlankLineAfter(KeyedNode node) {
        YamlNode parent = node.getParent();
        if (parent == null) return;
        int idx = parent.getChildren().indexOf(node);

        // Check if there's already a blank line after this node
        if (idx < parent.getChildren().size() - 1) {
            YamlNode after = parent.getChildren().get(idx + 1);
            if (after instanceof BlankLineNode) return;
        }

        // Add blank line after the node
        BlankLineNode bln = new BlankLineNode(node.getIndentation());
        parent.getChildren().add(idx + 1, bln);
        bln.setParent(parent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        KeyedNode node = pathIndex.get(path);
        if (node == null) return null; // Handle null case explicitly

        if (node instanceof KeyValueNode kv) {
            return (T) kv.getValue();
        }

        if (node instanceof ListNode ln) {
            return (T) listToObjects(ln);
        }

        if (node instanceof SectionNode sectionNode) {
            return (T) nodeToMap(sectionNode);
        }

        return null;

    }

    private Object getFromNode(YamlNode node) {
        if (node instanceof KeyValueNode kv) {
            return kv.getValue();
        }

        if (node instanceof ListNode ln) {
            return listToObjects(ln);
        }

        if (node instanceof SectionNode sn) {
            return nodeToMap(sn);
        }

        return null;
    }

    private List<Object> listToObjects(ListNode ln) {
        List<Object> list = new ArrayList<>();
        for (YamlNode child : ln.getChildren()) {
            if (child instanceof ListItemNode li) {
                if (li.getChildren().isEmpty()) {
                    list.add(li.getValue());
                } else {
                    list.add(nodeToMap(li));
                }
            }
        }
        return list;
    }

    private Map<String, Object> nodeToMap(YamlNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (YamlNode child : node.getChildren()) {
            if (child instanceof KeyedNode keyed) {
                map.put(keyed.getKey(), getFromNode(keyed));
            }
        }
        return map;
    }

    @Override
    public ConfigurationSection createSection(String path) {
        if (!contains(path)) {
            set(path, new java.util.HashMap<>());
        }
        return getSection(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> type) {
        Object val = get(path);
        if (val == null) return null;
        try {
            return (T) convertValue(val, type);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path, T defaultValue) {
        T val = (T) get(path, defaultValue.getClass());
        return val != null ? val : defaultValue;
    }

    private YamlNode createNode(int indent, String key, Object value) {
        if (value instanceof List<?> list) {
            ListNode ln = new ListNode(indent, key);
            for (Object item : list) {
                if (item instanceof Map || (item != null && (item.getClass().isAnnotationPresent(YamlItem.class) || item.getClass().isAnnotationPresent(YamlConfiguration.class)))) {
                    ListItemNode li = new ListItemNode(indent + indentStep, null);
                    updateNodeFromObject(li, item);
                    ln.addChild(li);
                } else if (item != null && YamlSerializerRegistry.hasSerializer(item.getClass())) {
                    ListItemNode li = new ListItemNode(indent + indentStep, null);
                    @SuppressWarnings("unchecked")
                    YamlSerializerBase<Object> ser = (YamlSerializerBase<Object>) YamlSerializerRegistry.getSerializer(item.getClass());
                    Map<String, Object> serialized = ser.serialize(item);
                    updateSectionFromMap(li, serialized);
                    ln.addChild(li);
                } else {
                    ln.addChild(new ListItemNode(indent + indentStep, item));
                }
            }
            return ln;
        } else if (value instanceof Map) {
            SectionNode sn = new SectionNode(indent, key);
            @SuppressWarnings("unchecked")
            Map<String, Object> stringMap = (Map<String, Object>) value;
            updateSectionFromMap(sn, stringMap);
            return sn;
        } else if (value != null && (value.getClass().isAnnotationPresent(YamlItem.class) || value.getClass().isAnnotationPresent(YamlConfiguration.class))) {
            SectionNode sn = new SectionNode(indent, key);
            updateNodeFromObject(sn, value);
            return sn;
        } else if (value != null && YamlSerializerRegistry.hasSerializer(value.getClass())) {
            @SuppressWarnings("unchecked")
            YamlSerializerBase<Object> ser = (YamlSerializerBase<Object>) YamlSerializerRegistry.getSerializer(value.getClass());
            Map<String, Object> serialized = ser.serialize(value);
            return createNode(indent, key, serialized);
        } else {
            return new KeyValueNode(indent, key, value);
        }
    }

    private void updateNodeFromObject(YamlNode parent, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                setRelative(parent, entry.getKey().toString(), entry.getValue(), null, false, false);
            }
            return;
        }

        Class<?> clazz = value.getClass();
        if (parent instanceof DocumentNode) {
            if (clazz.isAnnotationPresent(Comment.class)) {
                String[] lines = clazz.getAnnotation(Comment.class).value();
                String commentText = String.join("\n", lines);
                
                // Check if the banner is already there to avoid duplication if root wasn't cleared
                boolean alreadyHasBanner = false;
                if (!parent.getChildren().isEmpty() && parent.getChildren().get(0) instanceof CommentNode cn) {
                    if (cn.render(RenderOptions.defaultOptions()).contains(lines[0])) {
                        alreadyHasBanner = true;
                    }
                }
                
                if (!alreadyHasBanner) {
                    parent.getChildren().add(0, new CommentNode(0, commentText));
                    parent.getChildren().add(1, new BlankLineNode(0));
                }
            }
        }

        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isAnnotationPresent(YamlId.class) || field.getName().startsWith("__vinject_yaml")) continue;
                field.setAccessible(true);
                try {
                    Object val = field.get(value);
                    String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value() : field.getName();
                    String commentText = null;
                    if (field.isAnnotationPresent(Comment.class)) {
                        String[] lines = field.getAnnotation(Comment.class).value();
                        commentText = String.join("\n", lines);
                    }
                    boolean newLineBefore = field.isAnnotationPresent(NewLineBefore.class);
                    boolean newLineAfter = field.isAnnotationPresent(NewLineAfter.class);
                    setRelative(parent, key, val, commentText, newLineBefore, newLineAfter);
                } catch (Exception ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }



    @Override
    public boolean contains(String path) {
        return pathIndex.containsKey(path);
    }

    @Override
    public boolean isString(String path) {
        KeyedNode node = getNode(path);
        return node instanceof KeyValueNode kv && kv.getValue() instanceof String;
    }

    @Override
    public boolean isInt(String path) {
        KeyedNode node = getNode(path);
        return node instanceof KeyValueNode kv && kv.getValue() instanceof Number n && n.doubleValue() == n.intValue();
    }

    @Override
    public boolean isLong(String path) {
        KeyedNode node = getNode(path);
        return node instanceof KeyValueNode kv && kv.getValue() instanceof Number n && n.doubleValue() == n.longValue();
    }

    @Override
    public boolean isDouble(String path) {
        KeyedNode node = getNode(path);
        return node instanceof KeyValueNode kv && kv.getValue() instanceof Number;
    }

    @Override
    public boolean isBoolean(String path) {
        KeyedNode node = getNode(path);
        return node instanceof KeyValueNode kv && kv.getValue() instanceof Boolean;
    }

    @Override
    public boolean isList(String path) {
        KeyedNode node = getNode(path);
        return node instanceof ListNode;
    }

    @Override
    public boolean isSection(String path) {
        KeyedNode node = getNode(path);
        return node instanceof SectionNode;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new LinkedHashSet<>();
        for (String path : pathIndex.keySet()) {
            if (!deep && path.contains(".")) continue;
            keys.add(path);
        }
        return keys;
    }

    @Override
    public ConfigurationSection getSection(String path) {
        if (path == null || path.isEmpty()) return this;
        return new YamlConfigSubSection(this, path);
    }

    @Override
    public void save() {
        save(RenderOptions.defaultOptions());
    }

    public void save(RenderOptions options) {
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), render(options), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save YAML config to " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }


    private String getPathPrefix(String[] parts, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(".");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object value, Class<?> type) {
        if (value == null) return null;
        
        YamlSerializerBase<?> ser = YamlSerializerRegistry.getSerializer(type);
        if (ser != null && value instanceof Map) {
            return ser.deserialize((Map<String, Object>) value);
        }
        
        String str = value.toString();
        if (type == String.class) return str;
        if (type == int.class || type == Integer.class) return Integer.parseInt(str);
        if (type == long.class || type == Long.class) return Long.parseLong(str);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(str);
        if (type == double.class || type == Double.class) return Double.parseDouble(str);
        return value;
    }

    private static class YamlConfigSubSection implements ConfigurationSection {
        private final YamlConfig root;
        private final String basePath;

        public YamlConfigSubSection(YamlConfig root, String basePath) {
            this.root = root;
            this.basePath = basePath;
        }

        private String fullPath(String path) {
            if (path == null || path.isEmpty()) return basePath;
            return basePath + "." + path;
        }

        @Override
        public ConfigurationSection getConfigurationSection() {
            return this;
        }

        @Override
        public <T> T get(String path) {
            return root.get(fullPath(path));
        }

        @Override
        public <T> T get(String path, Class<T> type) {
            return root.get(fullPath(path), type);
        }

        @Override
        public <T> T get(String path, T defaultValue) {
            return root.get(fullPath(path), defaultValue);
        }

        @Override
        public void set(String path, Object value) {
            root.set(fullPath(path), value);
        }

        @Override
        public boolean contains(String path) {
            return root.contains(fullPath(path));
        }

        @Override
        public boolean isString(String path) {
            return root.isString(fullPath(path));
        }

        @Override
        public boolean isInt(String path) {
            return root.isInt(fullPath(path));
        }

        @Override
        public boolean isLong(String path) {
            return root.isLong(fullPath(path));
        }

        @Override
        public boolean isDouble(String path) {
            return root.isDouble(fullPath(path));
        }

        @Override
        public boolean isBoolean(String path) {
            return root.isBoolean(fullPath(path));
        }

        @Override
        public boolean isList(String path) {
            return root.isList(fullPath(path));
        }

        @Override
        public boolean isSection(String path) {
            return root.isSection(fullPath(path));
        }

        @Override
        public Set<String> getKeys(boolean deep) {
            Set<String> keys = new LinkedHashSet<>();
            String prefix = basePath + ".";
            for (String path : root.pathIndex.keySet()) {
                if (path.startsWith(prefix)) {
                    String rel = path.substring(prefix.length());
                    if (!rel.isEmpty() && (!deep && rel.contains("."))) continue; // Ensure rel is not empty before checking contains
                    keys.add(rel);
                }
            }
            return keys;
        }

        @Override
        public ConfigurationSection getSection(String path) {
            return new YamlConfigSubSection(root, fullPath(path));
        }

        @Override
        public ConfigurationSection createSection(String path) {
            String fPath = fullPath(path);
            if (!root.contains(fPath)) {
                root.set(fPath, new java.util.HashMap<>());
            }
            return getSection(path);
        }

        @Override
        public void save() {
            root.save();
        }

        @Override
        public boolean isDirty() {
            return root.isDirty();
        }
    }
}
