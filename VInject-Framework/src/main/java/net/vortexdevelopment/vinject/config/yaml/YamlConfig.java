package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigurationSection;

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
        return root.render();
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
            setRelative(parent, entry.getKey(), entry.getValue(), null);
        }
    }

    private String serializeSimple(Object val) {
        if (val == null) return "~"; // Standard YAML null representation
        if (val instanceof String s) {
            // Check if string needs quoting (contains special characters, starts with special chars, or is multiline)
            if (s.contains("\n") || s.contains(":") || s.startsWith("-") || s.startsWith("#") || s.startsWith(" ") || s.endsWith(" ")) {
                return "\"" + s.replace("\"", "\\\"") + "\"";
            }
            return s;
        }
        return val.toString();
    }

    public void inject(String path, Object value, String comment) {
        setInternal(path, value, comment);
    }

    @Override
    public void set(String path, Object value) {
        setInternal(path, value, null);
    }

    private void setInternal(String path, Object value, String comment) {
        if (path == null || path.isEmpty()) return;

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
                    YamlNode newNode = createNode(nextIndent, part, value);
                    if (comment != null && !comment.isEmpty()) {
                        current.addChild(new CommentNode(nextIndent, comment));
                    }
                    current.addChild(newNode);
                    if (newNode instanceof KeyedNode kn) pathIndex.put(currentPath, kn);
                } else {
                    SectionNode newNode = new SectionNode(nextIndent, part);
                    current.addChild(newNode);
                    pathIndex.put(currentPath, newNode);
                    current = newNode;
                }
                dirty = true;
            } else {
                if (i == parts.length - 1) {
                    updateExistingNode(existing, currentPath, value, comment);
                    dirty = true;
                } else {
                    if (!(existing instanceof SectionNode)) {
                        YamlNode parent = existing.getParent();
                        if (parent != null) {
                            int idx = parent.getChildren().indexOf(existing);
                            SectionNode sn = new SectionNode(existing.getIndentation(), part);
                            parent.getChildren().set(idx, sn);
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

    private void setRelative(YamlNode parent, String relativePath, Object value, String comment) {
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
                    YamlNode newNode = createNode(nextIndent, part, value);
                    if (comment != null && !comment.isEmpty()) {
                        current.addChild(new CommentNode(nextIndent, comment));
                    }
                    current.addChild(newNode);
                } else { // Not last part, create a section node
                    SectionNode newNode = new SectionNode(nextIndent, part);
                    current.addChild(newNode);
                    current = newNode;
                }
            } else {
                if (i == parts.length - 1) { // Last part, update existing node
                    if (child instanceof KeyedNode keyed) {
                        updateExistingNodeQuiet(keyed, value, comment);
                    }
                } else { // Not last part, navigate to existing section
                    if (!(child instanceof SectionNode)) {
                        // Replace KV or List with Section if it's not already a section
                        int idx = current.getChildren().indexOf(child);
                        SectionNode sn = new SectionNode(nextIndent, part); // Use nextIndent for the new section
                        current.getChildren().set(idx, sn);
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
        if (value instanceof Map || value instanceof List || (value != null && value.getClass().isAnnotationPresent(YamlItem.class)) || !(existing instanceof KeyValueNode)) {
            YamlNode parent = existing.getParent();
            if (parent != null) {
                int idx = parent.getChildren().indexOf(existing);
                newNode = createNode(existing.getIndentation(), existing.getKey(), value);
                parent.getChildren().set(idx, newNode);
            }
        } else if (existing instanceof KeyValueNode kv) {
            // If it's a simple value and existing is a KeyValueNode, just update its value.
            kv.setValue(serializeSimple(value));
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
            parent.getChildren().add(idx, new CommentNode(node.getIndentation(), comment));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        KeyedNode node = pathIndex.get(path);
        if (node == null) return null; // Handle null case explicitly

        switch (node) {
            case KeyValueNode kv -> {
                return (T) kv.getValue();
            }
            case ListNode ln -> {
                return (T) listToObjects(ln);
            }
            case SectionNode sectionNode -> {
                return (T) nodeToMap(sectionNode);
            }
            case null, default -> { 
                return null;
            }
        }
    }

    private Object getFromNode(YamlNode node) {
        return switch (node) {
            case KeyValueNode kv -> kv.getValue();
            case ListNode ln -> listToObjects(ln);
            case SectionNode sn -> nodeToMap(sn);
            default -> null;
        };
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
                if (item instanceof Map || (item != null && item.getClass().isAnnotationPresent(YamlItem.class))) {
                    ListItemNode li = new ListItemNode(indent + indentStep, "");
                    updateNodeFromObject(li, item);
                    ln.addChild(li);
                } else {
                    ln.addChild(new ListItemNode(indent + indentStep, serializeSimple(item)));
                }
            }
            return ln;
        } else if (value instanceof Map) {
            SectionNode sn = new SectionNode(indent, key);
            @SuppressWarnings("unchecked")
            Map<String, Object> stringMap = (Map<String, Object>) value;
            updateSectionFromMap(sn, stringMap);
            return sn;
        } else if (value != null && value.getClass().isAnnotationPresent(YamlItem.class)) {
            SectionNode sn = new SectionNode(indent, key);
            updateNodeFromObject(sn, value);
            return sn;
        } else {
            return new KeyValueNode(indent, key, serializeSimple(value));
        }
    }

    private void updateNodeFromObject(YamlNode parent, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                setRelative(parent, entry.getKey().toString(), entry.getValue(), null);
            }
            return;
        }

        for (Field field : value.getClass().getDeclaredFields()) {
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
                setRelative(parent, key, val, commentText);
            } catch (Exception ignored) {
            }
        }
    }


    @Override
    public boolean contains(String path) {
        return pathIndex.containsKey(path);
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
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), render(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    private Object convertValue(Object value, Class<?> type) {
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
