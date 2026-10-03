package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reader for the YAML documents VInject maps onto configuration beans.
 *
 * <p>The reader keeps comments, blank lines and indentation as {@link YamlNode}s so a loaded
 * document can be rendered again without losing its layout. It accepts the YAML constructs
 * that appear in hand written configuration files: block and flow collections, plain scalars
 * folded over several lines, literal and folded block scalars, multi line quoted scalars,
 * anchors, aliases, merge keys, core scalar tags and document markers.</p>
 *
 * <p>Constructs that cannot be represented in the VInject node model are rejected with a
 * message instead of being read as something else, so a valid file is never silently
 * misinterpreted.</p>
 */
public class YamlParser {

    /**
     * Matches the separator colon after the complete mapping key. Namespaced keys such as
     * {@code vortexskyblock:member:} are valid YAML keys, so the key portion must be allowed
     * to contain colons and backtrack to the colon followed by whitespace (or the end of the
     * line).
     */
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^\\s*(.+?)\\s*:(?=\\s|$)\\s*(.*)$");

    /**
     * A block sequence entry is a dash followed by whitespace or the end of the line. A
     * leading dash followed by a non space character (for example {@code -5}) is a plain
     * scalar, not a sequence entry.
     */
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*-(?:[ \\t]+(.*))?$");
    private static final Pattern ANCHOR_PREFIX_PATTERN = Pattern.compile("^&([^\\s\\[\\]{},]+)(?:[ \\t]+(.*))?$");
    private static final Pattern TAG_PREFIX_PATTERN = Pattern.compile("^(![^\\s]*)(?:[ \\t]+(.*))?$");
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^\\*([^\\s\\[\\]{},]+)$");
    private static final Pattern BLOCK_SCALAR_PATTERN = Pattern.compile("^([|>])([1-9]?)([+-]?)$");
    private static final Pattern EXPLICIT_KEY_PATTERN = Pattern.compile("^\\?[ \\t]+(.*)$");
    private static final Pattern EXPLICIT_VALUE_PATTERN = Pattern.compile("^:[ \\t]*(.*)$");
    private static final Pattern MERGE_KEY_PATTERN = Pattern.compile("^<<$");
    private static final Pattern YAML_DIRECTIVE_PATTERN = Pattern.compile("^%YAML[ \\t]+1\\.[0-9]+$");

    private String[] lines = new String[0];
    private int cursor;
    private int discoveredIndentWidth = -1;
    private int indentStep = 2;
    private final Map<String, YamlNode> anchors = new LinkedHashMap<>();
    private final List<MergeRequest> merges = new ArrayList<>();
    private boolean documentContentParsed;

    /**
     * Parses a YAML document into the VInject node model.
     *
     * @param content The document text
     * @return The parsed document
     * @throws IllegalArgumentException If the document contains unsupported or malformed YAML
     */
    public DocumentNode parse(String content) {
        if (content == null) {
            return new DocumentNode();
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        this.lines = content.split("\r?\n", -1);
        this.cursor = 0;
        this.discoveredIndentWidth = -1;
        this.anchors.clear();
        this.merges.clear();
        this.documentContentParsed = false;

        DocumentNode root = new DocumentNode();
        Deque<YamlNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (this.cursor < this.lines.length) {
            int before = this.cursor;
            String line = this.lines[this.cursor];
            String trimmed = line.trim();
            int indent = getIndentation(line);

            if (trimmed.isEmpty()) {
                peekParent(stack, indent, false).addChild(new BlankLineNode(indent));
                this.cursor++;
                continue;
            }

            if (trimmed.startsWith("#")) {
                peekParent(stack, indent, false).addChild(new CommentNode(indent, trimmed.substring(1)));
                this.cursor++;
                continue;
            }

            if (trimmed.startsWith("%")) {
                handleDirective(trimmed);
                this.cursor++;
                continue;
            }

            if (indent == 0 && isDocumentMarker(trimmed, "---")) {
                handleDocumentStart(trimmed);
                this.cursor++;
                continue;
            }

            if (indent == 0 && isDocumentMarker(trimmed, "...")) {
                break;
            }

            Matcher explicitKey = EXPLICIT_KEY_PATTERN.matcher(line);
            if (explicitKey.matches()) {
                handleExplicitEntry(explicitKey.group(1), indent, stack);
            } else {
                Matcher item = LIST_ITEM_PATTERN.matcher(line);
                boolean isListItem = item.matches();
                YamlNode parent = findParent(stack, indent, isListItem);

                if (isListItem) {
                    handleListItem(item.group(1), indent, parent, stack);
                } else {
                    Matcher entry = KEY_VALUE_PATTERN.matcher(line);
                    if (!entry.matches()) {
                        if (!handlePendingScalar(line, indent, stack)) {
                            throw unsupported(line);
                        }
                        continue;
                    }
                    handleMappingEntry(normalizeMappingKey(entry.group(1).trim()), entry.group(2), indent, parent, stack);
                }
            }

            this.documentContentParsed = true;
            if (this.cursor == before) {
                this.cursor++;
            }
        }

        applyMerges();
        return root;
    }

    /**
     * Returns the indentation width discovered while parsing, used when rendering new keys.
     *
     * @return The discovered indent width, defaulting to 2
     */
    public int getDiscoveredIndentWidth() {
        return discoveredIndentWidth == -1 ? 2 : discoveredIndentWidth;
    }

    /**
     * Strips YAML single or double quoted mapping keys so {@code 'false':} yields {@code false}.
     *
     * @param key The raw key text
     * @return The unquoted key
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
            return YamlValueFormatter.unescapeDoubleQuoted(key);
        }
        return key;
    }

    private void handleDirective(String trimmed) {
        if (YAML_DIRECTIVE_PATTERN.matcher(trimmed).matches()) {
            // Scalar resolution uses the YAML 1.1 core schema, which is what Bukkit's reader uses.
            return;
        }
        if (trimmed.startsWith("%TAG")) {
            throw new IllegalArgumentException("Unsupported YAML syntax: custom tag directives (%TAG) are not supported: " + trimmed);
        }
        throw new IllegalArgumentException("Unsupported YAML syntax: unknown YAML directive: " + trimmed);
    }

    private boolean isDocumentMarker(String trimmed, String marker) {
        return trimmed.equals(marker) || trimmed.startsWith(marker + " ") || trimmed.startsWith(marker + "\t");
    }

    private void handleDocumentStart(String trimmed) {
        String remainder = trimMarker(trimmed, "---");
        if (documentContentParsed) {
            throw new IllegalArgumentException("Unsupported YAML syntax: multiple YAML documents are not supported");
        }
        if (!remainder.isEmpty() && !remainder.startsWith("#")) {
            throw new IllegalArgumentException("Unsupported YAML syntax: a document start marker with inline content is not supported: " + trimmed);
        }
    }

    private String trimMarker(String trimmed, String marker) {
        return trimmed.substring(marker.length()).trim();
    }

    private void handleExplicitEntry(String keyText, int indent, Deque<YamlNode> stack) {
        int keyLine = this.cursor;
        String key = normalizeMappingKey(stripComment(keyText).trim());
        if (key.isEmpty() || key.startsWith("[") || key.startsWith("{")) {
            throw new IllegalArgumentException("Unsupported YAML syntax at line " + (keyLine + 1)
                    + ": a complex (non scalar) mapping key is not supported");
        }

        int valueLine = keyLine + 1;
        while (valueLine < this.lines.length && this.lines[valueLine].trim().isEmpty()) {
            valueLine++;
        }
        if (valueLine >= this.lines.length) {
            throw new IllegalArgumentException("Unsupported YAML syntax at line " + (keyLine + 1)
                    + ": explicit key '" + key + "' has no value");
        }

        Matcher value = EXPLICIT_VALUE_PATTERN.matcher(this.lines[valueLine]);
        if (!value.matches()) {
            throw new IllegalArgumentException("Unsupported YAML syntax at line " + (valueLine + 1)
                    + ": a complex (non scalar) mapping key is not supported");
        }

        this.cursor = valueLine;
        handleMappingEntry(key, value.group(1), indent, findParent(stack, indent, false), stack);
        this.documentContentParsed = true;
    }

    private void handleMappingEntry(String key, String rawValue, int indent, YamlNode parent, Deque<YamlNode> stack) {
        String valueText = stripComment(rawValue);

        if (MERGE_KEY_PATTERN.matcher(key).matches()) {
            handleMergeEntry(valueText, parent);
            this.cursor++;
            return;
        }

        Modifiers modifiers = parseModifiers(valueText);

        if (modifiers.remainder().isEmpty()) {
            if (hasNestedContent(indent)) {
                SectionNode section = new SectionNode(indent, key);
                parent.addChild(section);
                registerAnchor(modifiers.anchor(), section);
                stack.push(section);
            } else {
                KeyValueNode node = new KeyValueNode(indent, key, null);
                parent.addChild(node);
                registerAnchor(modifiers.anchor(), node);
                stack.push(node);
            }
            this.cursor++;
            return;
        }

        Matcher alias = ALIAS_PATTERN.matcher(modifiers.remainder());
        if (alias.matches()) {
            KeyedNode node = copyAliasedNode(alias.group(1), key, indent);
            parent.addChild(node);
            registerAnchor(modifiers.anchor(), node);
            stack.push(node);
            this.cursor++;
            return;
        }

        Object value = parseValue(modifiers, indent);
        KeyValueNode node = new KeyValueNode(indent, key, value);
        parent.addChild(node);
        registerAnchor(modifiers.anchor(), node);
        stack.push(node);
        this.cursor++;
    }

    private void handleListItem(String rawItem, int indent, YamlNode parent, Deque<YamlNode> stack) {
        YamlNode listParent = convertSectionToList(parent, stack);

        ListItemNode node = new ListItemNode(indent, null);
        listParent.addChild(node);
        stack.push(node);

        String content = rawItem == null ? "" : stripComment(rawItem);
        if (content.isEmpty()) {
            this.cursor++;
            return;
        }

        Modifiers modifiers = parseModifiers(content);
        String remainder = modifiers.remainder();
        if (remainder.isEmpty()) {
            this.cursor++;
            return;
        }

        Matcher alias = ALIAS_PATTERN.matcher(remainder);
        if (alias.matches()) {
            node.setValue(resolveAlias(alias.group(1)));
            this.cursor++;
            return;
        }

        Matcher nestedItem = LIST_ITEM_PATTERN.matcher(remainder);
        if (nestedItem.matches()) {
            node.setValue(parseInlineSequence(remainder, indent));
            this.cursor++;
            return;
        }

        Matcher entry = KEY_VALUE_PATTERN.matcher(remainder);
        if (entry.matches()) {
            String key = normalizeMappingKey(entry.group(1).trim());
            handleMappingEntry(key, entry.group(2), indent + this.indentStep, node, stack);
            return;
        }

        node.setValue(parseValue(modifiers, indent));
        this.cursor++;
    }

    /**
     * Handles a plain scalar written on the line below its key, which YAML allows for block
     * context scalars:
     *
     * <pre>
     * Offset:
     *   -5
     * </pre>
     *
     * @param line The current line
     * @param indent The indentation of the current line
     * @param stack The current node stack
     * @return True when the line was consumed as a value
     */
    private boolean handlePendingScalar(String line, int indent, Deque<YamlNode> stack) {
        if (!(stack.peek() instanceof KeyValueNode node) || node.getValue() != null) {
            return false;
        }
        if (indent <= node.getIndentation()) {
            return false;
        }

        Modifiers modifiers = parseModifiers(stripComment(line));
        if (modifiers.remainder().isEmpty()) {
            return false;
        }

        node.setValue(parseValue(modifiers, node.getIndentation()));
        this.cursor++;
        return true;
    }

    private YamlNode convertSectionToList(YamlNode parent, Deque<YamlNode> stack) {
        if (!(parent instanceof SectionNode section)) {
            return parent;
        }

        YamlNode grandParent = section.getParent();
        ListNode listNode = new ListNode(section.getIndentation(), section.getKey());
        if (grandParent != null) {
            int index = grandParent.getChildren().indexOf(section);
            if (index != -1) {
                grandParent.getChildren().set(index, listNode);
                listNode.setParent(grandParent);
            }
        }
        stack.pop();
        stack.push(listNode);
        return listNode;
    }

    private void handleMergeEntry(String valueText, YamlNode target) {
        Modifiers modifiers = parseModifiers(valueText);
        String remainder = modifiers.remainder();
        List<String> names = new ArrayList<>();

        Matcher alias = ALIAS_PATTERN.matcher(remainder);
        if (alias.matches()) {
            names.add(alias.group(1));
        } else if (remainder.startsWith("[") && remainder.endsWith("]")) {
            for (Object item : YamlValueFormatter.deserializeInlineSequence(remainder.substring(1, remainder.length() - 1))) {
                if (!(item instanceof String text) || !text.startsWith("*")) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: merge key entries must be aliases: " + remainder);
                }
                names.add(text.substring(1));
            }
        } else {
            throw new IllegalArgumentException("Unsupported YAML syntax: a merge key requires an alias or a list of aliases: " + valueText);
        }

        this.merges.add(new MergeRequest(target, names));
    }

    private void applyMerges() {
        for (MergeRequest merge : this.merges) {
            for (String name : merge.anchors()) {
                YamlNode source = this.anchors.get(name);
                if (source == null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: merge key references unknown alias '*" + name + "'");
                }
                for (YamlNode child : new ArrayList<>(source.getChildren())) {
                    if (!(child instanceof KeyedNode keyed) || child instanceof CommentNode) {
                        continue;
                    }
                    if (containsKey(merge.target(), keyed.getKey())) {
                        continue;
                    }
                    merge.target().addChild(copyNode(child, merge.target().getIndentation() + this.indentStep));
                }
            }
        }
    }

    private boolean containsKey(YamlNode parent, String key) {
        for (YamlNode child : parent.getChildren()) {
            if (child instanceof KeyedNode keyed && keyed.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private Object parseValue(Modifiers modifiers, int parentIndent) {
        String text = modifiers.remainder();

        Matcher blockScalar = BLOCK_SCALAR_PATTERN.matcher(text);
        if (blockScalar.matches()) {
            String value = consumeBlockScalar(blockScalar, parentIndent);
            return applyTag(modifiers.tag(), value, value, true);
        }

        char quote = text.charAt(0);
        if (quote == '"' || quote == '\'') {
            String raw = consumeQuoted(text);
            return applyTag(modifiers.tag(), raw, YamlValueFormatter.deserialize(raw), true);
        }

        if (text.startsWith("[") || text.startsWith("{")) {
            if (!isFlowClosed(text)) {
                throw new IllegalArgumentException("Unsupported YAML syntax: a flow collection must be closed on the same line: " + text);
            }
            return applyTag(modifiers.tag(), text, YamlValueFormatter.deserialize(text), false);
        }

        String plain = consumePlain(text, parentIndent);
        return applyTag(modifiers.tag(), plain, YamlValueFormatter.deserialize(plain), false);
    }

    private Object applyTag(String tag, String rawText, Object resolved, boolean quoted) {
        if (tag == null || tag.isEmpty() || tag.equals("!")) {
            return resolved;
        }

        String literal = quoted ? String.valueOf(resolved) : rawText;
        return switch (tag) {
            case "!!str", "!<tag:yaml.org,2002:str>" -> literal;
            case "!!int", "!<tag:yaml.org,2002:int>" -> {
                Object value = YamlValueFormatter.resolveInteger(literal.trim());
                if (value == null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: '" + literal + "' is not an integer for tag " + tag);
                }
                yield value;
            }
            case "!!float", "!<tag:yaml.org,2002:float>" -> {
                Double value = YamlValueFormatter.resolveFloat(literal.trim());
                if (value == null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: '" + literal + "' is not a float for tag " + tag);
                }
                yield value;
            }
            case "!!bool", "!<tag:yaml.org,2002:bool>" -> {
                Boolean value = YamlValueFormatter.resolveBoolean(literal.trim());
                if (value == null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: '" + literal + "' is not a boolean for tag " + tag);
                }
                yield value;
            }
            case "!!null", "!<tag:yaml.org,2002:null>" -> null;
            case "!!seq", "!<tag:yaml.org,2002:seq>" -> {
                if (!(resolved instanceof List<?>)) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: tag " + tag + " requires a sequence");
                }
                yield resolved;
            }
            case "!!map", "!<tag:yaml.org,2002:map>" -> {
                if (!(resolved instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: tag " + tag + " requires a mapping");
                }
                yield resolved;
            }
            default -> throw new IllegalArgumentException("Unsupported YAML syntax: unsupported tag '" + tag + "'");
        };
    }

    private String consumePlain(String text, int parentIndent) {
        StringBuilder value = new StringBuilder(text);
        boolean pendingBlankLine = false;

        while (true) {
            int next = this.cursor + 1;
            while (next < this.lines.length && this.lines[next].trim().isEmpty()) {
                pendingBlankLine = true;
                next++;
            }
            if (next >= this.lines.length) {
                break;
            }

            String line = this.lines[next];
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("...")) {
                break;
            }
            if (getIndentation(line) <= parentIndent) {
                break;
            }
            if (KEY_VALUE_PATTERN.matcher(line).matches()
                    || LIST_ITEM_PATTERN.matcher(line).matches()
                    || EXPLICIT_KEY_PATTERN.matcher(line).matches()) {
                break;
            }

            value.append(pendingBlankLine ? '\n' : ' ').append(trimmed);
            pendingBlankLine = false;
            this.cursor = next;
        }

        return value.toString();
    }

    private String consumeQuoted(String text) {
        char quote = text.charAt(0);
        StringBuilder raw = new StringBuilder(text);

        while (!isQuotedScalarClosed(raw, quote)) {
            this.cursor++;
            if (this.cursor >= this.lines.length) {
                throw new IllegalArgumentException("Unsupported YAML syntax: unterminated quoted scalar: " + text);
            }
            if (!(quote == '"' && endsWithUnescapedBackslash(raw))) {
                raw.append(' ');
            }
            raw.append(this.lines[this.cursor].trim());
        }
        return raw.toString();
    }

    private boolean isQuotedScalarClosed(StringBuilder raw, char quote) {
        boolean escaped = false;
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quote == '"') {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return true;
                }
            } else if (c == '\'') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean endsWithUnescapedBackslash(StringBuilder raw) {
        int backslashes = 0;
        for (int i = raw.length() - 1; i >= 0 && raw.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private String consumeBlockScalar(Matcher indicator, int parentIndent) {
        boolean folded = indicator.group(1).equals(">");
        int explicitIndent = indicator.group(2).isEmpty() ? -1 : Integer.parseInt(indicator.group(2));
        String chomping = indicator.group(3);

        int contentIndent = explicitIndent > 0 ? parentIndent + explicitIndent : -1;
        List<String> content = new ArrayList<>();
        int index = this.cursor + 1;

        while (index < this.lines.length) {
            String line = this.lines[index];
            if (line.trim().isEmpty()) {
                content.add("");
                index++;
                continue;
            }

            int lineIndent = getIndentation(line);
            if (contentIndent < 0) {
                if (lineIndent <= parentIndent) {
                    break;
                }
                contentIndent = lineIndent;
            }
            if (lineIndent < contentIndent) {
                break;
            }
            content.add(line.substring(Math.min(contentIndent, line.length())));
            index++;
        }

        this.cursor = index - 1;
        return assembleBlockScalar(content, folded, chomping);
    }

    private String assembleBlockScalar(List<String> content, boolean folded, String chomping) {
        int size = content.size();
        while (size > 0 && content.get(size - 1).isEmpty()) {
            size--;
        }
        int trailingBreaks = content.size() - size;

        if (size == 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        for (int i = 0; i < size; i++) {
            String line = content.get(i);
            value.append(line);
            if (i == size - 1) {
                continue;
            }

            String next = content.get(i + 1);
            boolean fold = folded
                    && !line.isEmpty()
                    && !next.isEmpty()
                    && !line.startsWith(" ")
                    && !next.startsWith(" ");
            value.append(fold ? ' ' : '\n');
        }

        if ("-".equals(chomping)) {
            return value.toString();
        }
        if ("+".equals(chomping)) {
            value.append("\n".repeat(trailingBreaks + 1));
            return value.toString();
        }
        value.append('\n');
        return value.toString();
    }

    private List<Object> parseInlineSequence(String text, int indent) {
        List<Object> items = new ArrayList<>();
        String remainder = text;

        while (true) {
            Matcher item = LIST_ITEM_PATTERN.matcher(remainder);
            if (!item.matches()) {
                break;
            }
            String value = item.group(1);
            if (value == null || stripComment(value).isEmpty()) {
                break;
            }

            Modifiers modifiers = parseModifiers(stripComment(value));
            if (LIST_ITEM_PATTERN.matcher(modifiers.remainder()).matches()) {
                items.add(parseInlineSequence(modifiers.remainder(), indent));
            } else {
                items.add(parseValue(modifiers, indent));
            }
            break;
        }

        return items;
    }

    private Modifiers parseModifiers(String text) {
        String tag = null;
        String anchor = null;
        String remainder = text;
        boolean matched = true;

        while (matched && !remainder.isEmpty()) {
            matched = false;

            Matcher tagMatcher = TAG_PREFIX_PATTERN.matcher(remainder);
            if (tagMatcher.matches()) {
                if (tag != null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: a node cannot carry multiple tags: " + text);
                }
                tag = tagMatcher.group(1);
                remainder = tagMatcher.group(2) == null ? "" : tagMatcher.group(2).trim();
                matched = true;
            }

            Matcher anchorMatcher = ANCHOR_PREFIX_PATTERN.matcher(remainder);
            if (anchorMatcher.matches()) {
                if (anchor != null) {
                    throw new IllegalArgumentException("Unsupported YAML syntax: a node cannot carry multiple anchors: " + text);
                }
                anchor = anchorMatcher.group(1);
                remainder = anchorMatcher.group(2) == null ? "" : anchorMatcher.group(2).trim();
                matched = true;
            }
        }

        return new Modifiers(tag, anchor, remainder);
    }

    private void registerAnchor(String anchor, YamlNode node) {
        if (anchor != null) {
            this.anchors.put(anchor, node);
        }
    }

    private Object resolveAlias(String name) {
        YamlNode node = this.anchors.get(name);
        if (node == null) {
            throw new IllegalArgumentException("Unsupported YAML syntax: unknown alias '*" + name + "'");
        }
        return nodeToValue(node);
    }

    private KeyedNode copyAliasedNode(String name, String key, int indent) {
        YamlNode source = this.anchors.get(name);
        if (source == null) {
            throw new IllegalArgumentException("Unsupported YAML syntax: unknown alias '*" + name + "'");
        }

        KeyedNode copy;
        if (source instanceof SectionNode) {
            copy = new SectionNode(indent, key);
        } else if (source instanceof ListNode) {
            copy = new ListNode(indent, key);
        } else if (source instanceof KeyValueNode valueNode) {
            return new KeyValueNode(indent, key, deepCopyValue(valueNode.getValue()));
        } else {
            throw new IllegalArgumentException("Unsupported YAML syntax: alias '*" + name + "' cannot be used as a mapping value");
        }

        copyChildren(source, copy);
        return copy;
    }

    private void copyChildren(YamlNode source, YamlNode target) {
        for (YamlNode child : source.getChildren()) {
            target.addChild(copyNode(child, target.getIndentation() + this.indentStep));
        }
    }

    private YamlNode copyNode(YamlNode source, int indent) {
        YamlNode copy;
        if (source instanceof SectionNode) {
            copy = new SectionNode(indent, ((KeyedNode) source).getKey());
        } else if (source instanceof ListNode) {
            copy = new ListNode(indent, ((KeyedNode) source).getKey());
        } else if (source instanceof KeyValueNode valueNode) {
            copy = new KeyValueNode(indent, valueNode.getKey(), deepCopyValue(valueNode.getValue()));
        } else if (source instanceof ListItemNode itemNode) {
            copy = new ListItemNode(indent, deepCopyValue(itemNode.getValue()));
        } else if (source instanceof CommentNode commentNode) {
            copy = new CommentNode(indent, commentNode.getComment());
        } else {
            copy = new BlankLineNode(indent);
        }

        for (YamlNode child : source.getChildren()) {
            copy.addChild(copyNode(child, indent + this.indentStep));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private Object nodeToValue(YamlNode node) {
        if (node instanceof KeyValueNode valueNode) {
            return valueNode.getValue();
        }
        if (node instanceof ListItemNode itemNode) {
            return itemNode.getChildren().isEmpty() ? itemNode.getValue() : nodeToMap(itemNode);
        }
        if (node instanceof ListNode || node instanceof DocumentNode) {
            List<Object> list = new ArrayList<>();
            for (YamlNode child : node.getChildren()) {
                if (child instanceof ListItemNode) {
                    list.add(nodeToValue(child));
                }
            }
            return list;
        }
        return nodeToMap(node);
    }

    private Map<String, Object> nodeToMap(YamlNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (YamlNode child : node.getChildren()) {
            if (child instanceof KeyedNode keyed) {
                map.put(keyed.getKey(), nodeToValue(keyed));
            }
        }
        return map;
    }

    private boolean hasNestedContent(int indent) {
        for (int index = this.cursor + 1; index < this.lines.length; index++) {
            String line = this.lines[index];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("---") || trimmed.startsWith("...")) {
                return false;
            }

            int nextIndent = getIndentation(line);
            boolean nestedEntry = KEY_VALUE_PATTERN.matcher(line).matches()
                    || LIST_ITEM_PATTERN.matcher(line).matches()
                    || EXPLICIT_KEY_PATTERN.matcher(line).matches();
            if (nextIndent > indent) {
                return nestedEntry;
            }
            return nextIndent == indent && LIST_ITEM_PATTERN.matcher(line).matches();
        }
        return false;
    }

    private boolean isFlowClosed(String text) {
        char open = text.charAt(0);
        char close = open == '[' ? ']' : '}';
        char quote = 0;
        boolean escaped = false;
        int nesting = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\' && quote == '"') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[' || c == '{') {
                nesting++;
            } else if (c == ']' || c == '}') {
                nesting--;
                if (nesting == 0 && c == close) {
                    return i == text.length() - 1;
                }
            }
        }
        return false;
    }

    /**
     * Removes a trailing comment from a value, ignoring hash characters inside quoted scalars
     * or inside flow collections.
     *
     * @param text The raw value text
     * @return The value text without its comment
     */
    private String stripComment(String text) {
        char quote = 0;
        boolean escaped = false;
        int nesting = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\' && quote == '"') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }

            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[' || c == '{') {
                nesting++;
            } else if (c == ']' || c == '}') {
                nesting--;
            } else if (c == '#' && nesting == 0 && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                return text.substring(0, i).trim();
            }
        }
        return text.trim();
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

    private IllegalArgumentException unsupported(String line) {
        return new IllegalArgumentException("Unsupported YAML syntax at line " + (this.cursor + 1) + ": " + line);
    }

    private record Modifiers(String tag, String anchor, String remainder) {
    }

    private record MergeRequest(YamlNode target, List<String> anchors) {
    }
}
