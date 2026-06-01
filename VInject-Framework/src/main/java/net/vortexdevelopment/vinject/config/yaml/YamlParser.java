package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlParser {
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^\\s*([^:#]+?)\\s*:\\s*(.*)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*-\\s*(.*)$");
    private static final Pattern BLOCK_SCALAR_PATTERN = Pattern.compile("^([>|])([-+]?)(\\d+)?$");

    private int discoveredIndentWidth = -1;

    public DocumentNode parse(String content) {
        if (content == null) {
            return new DocumentNode();
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        DocumentNode root = new DocumentNode();
        this.discoveredIndentWidth = -1; // Reset for discovery on each parse
        String[] lines = content.split("\r?\n");
        Deque<YamlNode> stack = new ArrayDeque<>();
        stack.push(root);

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
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
                Object value = parseValue(fullValue);


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
                String key = normalizeMappingKey(kvMatcher.group(1).trim());
                String fullValue = kvMatcher.group(2).trim();
                BlockScalarSpec blockSpec = parseBlockScalarSpec(fullValue);
                Object value;
                if (blockSpec != null) {
                    BlockScalarParseResult blockResult = parseBlockScalar(lines, lineIndex, indent, blockSpec);
                    value = blockResult.content();
                    lineIndex = blockResult.nextLineIndex() - 1;
                } else {
                    value = parseValue(kvMatcher.group(2));
                }

                YamlNode node;
                if (value == null && blockSpec == null) {
                    node = new SectionNode(indent, key);
                } else if (value instanceof List<?> list) {
                    node = createListNode(indent, key, list);
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

    /**
     * Strip YAML single-/double-quoted mapping keys so {@code 'false':} yields {@code false}, not {@code 'false'}.
     * Vinject's line-based parser captures quotes literally; real YAML treats them as syntax only.
     */
    static String normalizeMappingKey(String key) {
        if (key == null || key.length() < 2) {
            return key;
        }
        char first = key.charAt(0);
        char last = key.charAt(key.length() - 1);
        if (first == '\'' && last == '\'') {
            return key.substring(1, key.length() - 1).replace("''", "'");
        }
        if (first == '"' && last == '"') {
            return unescapeDoubleQuotedKeyInner(key.substring(1, key.length() - 1));
        }
        return key;
    }

    private static String unescapeDoubleQuotedKeyInner(String inner) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                if (c == 'n') {
                    sb.append('\n');
                } else if (c == 'r') {
                    sb.append('\r');
                } else if (c == 't') {
                    sb.append('\t');
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static BlockScalarSpec parseBlockScalarSpec(String trimmedValue) {
        if (trimmedValue == null || trimmedValue.isEmpty()) {
            return null;
        }
        Matcher matcher = BLOCK_SCALAR_PATTERN.matcher(trimmedValue);
        if (!matcher.matches()) {
            return null;
        }
        BlockScalarStyle style = matcher.group(1).charAt(0) == '>' ? BlockScalarStyle.FOLDED : BlockScalarStyle.LITERAL;
        String chompToken = matcher.group(2);
        BlockScalarChomping chomping = switch (chompToken) {
            case "-" -> BlockScalarChomping.STRIP;
            case "+" -> BlockScalarChomping.KEEP;
            default -> BlockScalarChomping.CLIP;
        };
        int explicitIndent = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : -1;
        return new BlockScalarSpec(style, chomping, explicitIndent);
    }

    private BlockScalarParseResult parseBlockScalar(String[] lines, int keyLineIndex, int keyIndent, BlockScalarSpec spec) {
        int contentIndent = spec.explicitIndent() >= 0 ? keyIndent + spec.explicitIndent() : -1;
        StringBuilder raw = new StringBuilder();
        boolean sawContent = false;

        for (int i = keyLineIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            int lineIndent = getIndentation(line);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (!sawContent && contentIndent < 0) {
                    continue;
                }
                if (sawContent) {
                    raw.append('\n');
                }
                continue;
            }

            if (lineIndent <= keyIndent) {
                return finishBlockScalar(raw, spec, i);
            }

            if (contentIndent < 0) {
                contentIndent = lineIndent;
            } else if (lineIndent < contentIndent) {
                return finishBlockScalar(raw, spec, i);
            }

            if (raw.length() > 0) {
                raw.append('\n');
            }
            raw.append(stripContentIndent(line, contentIndent));
            sawContent = true;
        }

        return finishBlockScalar(raw, spec, lines.length);
    }

    private static String stripContentIndent(String line, int contentIndent) {
        if (line.length() <= contentIndent) {
            return line.trim();
        }
        return line.substring(contentIndent);
    }

    private static BlockScalarParseResult finishBlockScalar(StringBuilder raw, BlockScalarSpec spec, int nextLineIndex) {
        String content = applyChomping(formatBlockContent(raw.toString(), spec.style()), spec.chomping());
        return new BlockScalarParseResult(content, nextLineIndex);
    }

    private static String formatBlockContent(String raw, BlockScalarStyle style) {
        if (style == BlockScalarStyle.LITERAL) {
            return raw;
        }
        return foldBlockContent(raw);
    }

    /**
     * Folded block: join non-blank lines with spaces; blank lines become paragraph breaks.
     */
    private static String foldBlockContent(String raw) {
        if (raw.isEmpty()) {
            return "";
        }
        String[] lines = raw.split("\n", -1);
        StringBuilder out = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();

        Runnable flushParagraph = () -> {
            if (paragraph.isEmpty()) {
                return;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(paragraph.toString().trim());
            paragraph.setLength(0);
        };

        for (String line : lines) {
            if (line.isBlank()) {
                flushParagraph.run();
                if (out.length() > 0 && !out.toString().endsWith("\n\n")) {
                    out.append('\n');
                }
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append(' ');
                }
                paragraph.append(line.trim());
            }
        }
        flushParagraph.run();
        return out.toString();
    }

    private static String applyChomping(String content, BlockScalarChomping chomping) {
        return switch (chomping) {
            case STRIP -> content.replaceAll("\\n+$", "");
            case KEEP -> content;
            case CLIP -> content.replaceFirst("\\n$", "");
        };
    }

    private record BlockScalarSpec(BlockScalarStyle style, BlockScalarChomping chomping, int explicitIndent) {}

    private record BlockScalarParseResult(String content, int nextLineIndex) {}

    private static ListNode createListNode(int indent, String key, List<?> values) {
        ListNode listNode = new ListNode(indent, key);
        for (Object item : values) {
            listNode.addChild(new ListItemNode(indent, item));
        }
        return listNode;
    }

    private Object parseValue(String fullValue) {
        String trimmed = fullValue.trim();
        if (trimmed.isEmpty()) return null;

        // Unquoted: handle comments
        int hashIdx = -1;
        if (trimmed.startsWith("#")) {
            hashIdx = 0;
        } else {
            hashIdx = trimmed.indexOf(" #");
        }
        
        if (hashIdx != -1) {
            trimmed = trimmed.substring(0, hashIdx).trim();
        }
        
        return YamlValueFormatter.deserialize(trimmed);
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
