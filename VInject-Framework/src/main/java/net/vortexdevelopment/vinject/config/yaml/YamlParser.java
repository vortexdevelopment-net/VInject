package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlParser {
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^\\s*([^:#]+?)\\s*:\\s*(.*)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*-\\s*(.*)$");

    private int discoveredIndentWidth = -1;

    public DocumentNode parse(String content) {
        DocumentNode root = new DocumentNode();
        this.discoveredIndentWidth = -1; // Reset for discovery on each parse
        String[] lines = content.split("\r?\n");
        Deque<YamlNode> stack = new ArrayDeque<>();
        stack.push(root);

        for (String line : lines) {
            int indent = getIndentation(line);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                YamlNode parent = peekParent(stack, indent, false);
                parent.addChild(new BlankLineNode(indent));
                continue;
            }

            if (trimmed.startsWith("#")) {
                String comment = trimmed.substring(1);
                YamlNode parent = peekParent(stack, indent, false);
                parent.addChild(new CommentNode(indent, comment));
                continue;
            }

            boolean isListItem = LIST_ITEM_PATTERN.matcher(line).find();
            YamlNode parent = findParent(stack, indent, isListItem);

            Matcher liMatcher = LIST_ITEM_PATTERN.matcher(line);
            if (liMatcher.find()) {
                String fullValue = liMatcher.group(1);
                String value = parseValue(fullValue);


                if (parent instanceof SectionNode section) {
                    // Convert SectionNode to ListNode
                    YamlNode grandParent = section.getParent();
                    ListNode listNode = new ListNode(section.getIndentation(), section.getKey());
                    listNode.setParent(grandParent);
                    if (grandParent != null) {
                        int idx = grandParent.getChildren().indexOf(section);
                        if (idx != -1) {
                            grandParent.getChildren().set(idx, listNode);
                        }
                    }
                    stack.pop();
                    stack.push(listNode);
                    parent = listNode;
                }

                ListItemNode liNode = new ListItemNode(indent, value);
                parent.addChild(liNode);
                stack.push(liNode);
                continue;
            }

            Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(line);
            if (kvMatcher.find()) {
                String key = kvMatcher.group(1).trim();
                String fullValue = kvMatcher.group(2);
                String value = parseValue(fullValue);

                YamlNode node;
                if (value.isEmpty()) {
                    node = new SectionNode(indent, key);
                } else {
                    node = new KeyValueNode(indent, key, value);
                }
                parent.addChild(node);
                stack.push(node);
                continue;
            }

            throw new IllegalArgumentException("Unsupported YAML syntax at line: " + line);
        }

        return root;
    }

    private String parseValue(String fullValue) {
        String trimmed = fullValue.trim();
        if (trimmed.isEmpty()) return "";

        if (trimmed.startsWith("\"")) {
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = 1; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (escaped) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 't') sb.append('\t');
                    else sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        if (trimmed.startsWith("'")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '\'' && i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                } else if (c == '\'') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        // Unquoted: handle comments
        int hashIdx = trimmed.indexOf(" #");
        if (hashIdx == -1 && trimmed.startsWith("#")) hashIdx = 0;
        if (hashIdx != -1) {
            return trimmed.substring(0, hashIdx).trim();
        }
        return trimmed;
    }

    private int getIndentation(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        String trimmed = line.trim();
        // Skip empty lines, comments, and list items for discovery
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) return count;

        // Lock discoveredIndentWidth on the first valid indented line
        if (count > 0 && count < 10 && discoveredIndentWidth == -1) {
            discoveredIndentWidth = count;
        }
        return count;
    }

    public int getDiscoveredIndentWidth() {
        return discoveredIndentWidth == -1 ? 2 : discoveredIndentWidth;
    }

    private YamlNode findParent(Deque<YamlNode> stack, int indent, boolean isListItem) {
        while (stack.size() > 1) {
            YamlNode top = stack.peek();

            if (isListItem) {
                // List items can be children of the previous key even if at the same indentation level
                if (top.getIndentation() <= indent && (top instanceof SectionNode || top instanceof ListNode)) {
                    return top;
                }
            }

            if (top.getIndentation() < indent) {
                return top;
            }
            stack.pop();
        }
        return stack.peek();
    }

    private YamlNode peekParent(Deque<YamlNode> stack, int indent, boolean isListItem) {
        for (YamlNode node : stack) {
            if (node == stack.getLast()) break; // DocumentNode is the ultimate parent

            if (isListItem) {
                if (node.getIndentation() <= indent && (node instanceof SectionNode || node instanceof ListNode)) {
                    return node;
                }
            }

            if (node.getIndentation() < indent) {
                return node;
            }
        }
        return stack.getLast();
    }
}
