package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the reader to real YAML semantics.
 *
 * <p>Every expected value in this class is what a spec compliant reader (SnakeYAML, which
 * Bukkit's configuration classes use) produces for the same document. Each test that used to
 * fail before the reader was fixed is marked with the shape it covers, so a regression shows
 * up as a value change instead of a silently different configuration.</p>
 */
class YamlParserConformanceTest {

    private static Object value(String yaml, String path) {
        return YamlConfig.load(yaml).get(path);
    }

    private static IllegalArgumentException rejected(String yaml) {
        return assertThrows(IllegalArgumentException.class, () -> YamlConfig.load(yaml));
    }

    @Test
    void plainScalarFoldedOverSeveralLinesIsJoined() {
        assertEquals("broadcast a b c d!", value("Command: broadcast a b\n  c d!\n", "Command"));
    }

    @Test
    void plainScalarFoldedOverBlankLineKeepsLineBreak() {
        assertEquals("first\nsecond", value("Command: first\n\n  second\n", "Command"));
    }

    @Test
    void literalBlockScalarsAreSupported() {
        assertEquals("line1\nline2\n", value("Command: |\n  line1\n  line2\n", "Command"));
        assertEquals("line1\nline2", value("Command: |-\n  line1\n  line2\n", "Command"));
        assertEquals("line1\nline2\n", value("Command: |\n  line1\n  line2", "Command"));
    }

    @Test
    void foldedBlockScalarsAreSupported() {
        assertEquals("line1 line2\n", value("Command: >\n  line1\n  line2\n", "Command"));
        assertEquals("line1 line2", value("Command: >-\n  line1\n  line2\n", "Command"));
    }

    @Test
    void blockScalarsKeepIndentationBeyondTheDetectedBlockIndent() {
        assertEquals("line1\n  indented\n", value("Command: |\n  line1\n    indented\n", "Command"));
    }

    @Test
    void quotedScalarsFoldedOverSeveralLinesAreJoined() {
        assertEquals("line1 line2", value("Command: \"line1\n  line2\"\n", "Command"));
        assertEquals("line1 line2", value("Command: 'line1\n  line2'\n", "Command"));
    }

    @Test
    void hashInsideQuotedScalarIsNotAComment() {
        assertEquals("echo #1 done", value("Command: 'echo #1 done'\n", "Command"));
        assertEquals("echo #1 done", value("Command: \"echo #1 done\"\n", "Command"));
    }

    @Test
    void hashOutsideQuotesIsStillAComment() {
        assertEquals("echo", value("Command: echo #1 done\n", "Command"));
        assertEquals("echo#nospace", value("Command: echo#nospace # trailing\n", "Command"));
    }

    @Test
    void listOfMappingsWrittenInlineIsSupported() {
        assertEquals(List.of(Map.of("name", "x", "value", "y")),
                value("Items:\n  - name: x\n    value: y\n", "Items"));
        assertEquals(List.of(Map.of("name", "x")),
                value("Items:\n  - name: x\n", "Items"));
    }

    @Test
    void nestedSequencesAreSupported() {
        assertEquals(List.of(List.of("a")), value("Items:\n  - - a\n", "Items"));
    }

    @Test
    void flowCollectionsAreSupported() {
        assertEquals(List.of("a", "b"), value("Items: [a, b]\n", "Items"));
        assertEquals(List.of(List.of("a"), List.of("b")), value("Items: [[a], [b]]\n", "Items"));

        Map<?, ?> mapping = (Map<?, ?>) value("Value: {a: [1, 2], b: text}\n", "Value");
        assertEquals(List.of(1, 2), mapping.get("a"));
        assertEquals("text", mapping.get("b"));
    }

    @Test
    void anchorsAndAliasesAreSupported() {
        String yaml = "Base: &b 5\nValue: *b\n";
        assertEquals(5, value(yaml, "Base"));
        assertEquals(5, value(yaml, "Value"));
    }

    @Test
    void anchoredMappingsCanBeAliased() {
        String yaml = "Base: &b\n  a: 1\nCopy: *b\n";
        assertEquals(Map.of("a", 1), value(yaml, "Copy"));
    }

    @Test
    void mergeKeysAreAppliedAndExplicitKeysWin() {
        String yaml = "Base: &base\n  a: 1\n  b: 2\nChild:\n  <<: *base\n  b: 3\n";
        Map<?, ?> child = (Map<?, ?>) value(yaml, "Child");
        assertEquals(1, child.get("a"));
        assertEquals(3, child.get("b"));
    }

    @Test
    void coreScalarTagsAreApplied() {
        assertEquals("5", value("Value: !!str 5\n", "Value"));
        assertEquals(5, value("Value: !!int '5'\n", "Value"));
        assertEquals(1.5, value("Value: !!float '1.5'\n", "Value"));
        assertEquals(Boolean.TRUE, value("Value: !!bool yes\n", "Value"));
        assertNull(value("Value: !!null nothing\n", "Value"));
    }

    @Test
    void yaml11BooleansAreResolved() {
        assertEquals(Boolean.TRUE, value("Value: yes\n", "Value"));
        assertEquals(Boolean.TRUE, value("Value: on\n", "Value"));
        assertEquals(Boolean.TRUE, value("Value: TRUE\n", "Value"));
        assertEquals(Boolean.FALSE, value("Value: no\n", "Value"));
        assertEquals(Boolean.FALSE, value("Value: off\n", "Value"));
        assertEquals("yes", value("Value: 'yes'\n", "Value"));
    }

    @Test
    void numericFormatsAreResolved() {
        assertEquals(31, value("Value: 0x1F\n", "Value"));
        assertEquals(1000, value("Value: 1_000\n", "Value"));
        assertEquals(1000.0, value("Value: 1e3\n", "Value"));
        assertEquals(1.5, value("Value: 1.5\n", "Value"));
        assertEquals(Double.POSITIVE_INFINITY, value("Value: .inf\n", "Value"));
        assertEquals("5", value("Value: '5'\n", "Value"));
    }

    @Test
    void nullFormsAreResolved() {
        assertNull(value("Value: ~\n", "Value"));
        assertNull(value("Value: null\n", "Value"));
        assertNull(value("Value: NULL\n", "Value"));
        assertNull(value("Value:\n", "Value"));
        assertEquals(Map.of("a", 1), value("Value:\n  a: 1\n", "Value"));
    }

    @Test
    void quotedScalarsKeepTheirText() {
        assertEquals("true", value("Value: 'true'\n", "Value"));
        assertEquals("5", value("Value: \"5\"\n", "Value"));
        assertEquals("a:b", value("Value: \"a:b\"\n", "Value"));
    }

    @Test
    void escapeSequencesAreSupported() {
        assertEquals("tab\there", value("Value: \"tab\\there\"\n", "Value"));
        assertEquals("line\nbreak", value("Value: \"line\\nbreak\"\n", "Value"));
        assertEquals("C:\\temp", value("Value: \"C:\\\\temp\"\n", "Value"));
        assertEquals("\u00e4", value("Value: \"\\u00e4\"\n", "Value"));
    }

    @Test
    void documentMarkersAndDirectivesAreAccepted() {
        assertEquals(1, value("---\nValue: 1\n", "Value"));
        assertEquals(1, value("%YAML 1.1\n---\nValue: 1\n", "Value"));
        assertEquals(1, value("Value: 1\n...\n", "Value"));
    }

    @Test
    void explicitScalarKeysAreSupported() {
        assertEquals("value", value("? key\n: value\n", "key"));
    }

    @Test
    void duplicateKeysKeepTheLastValue() {
        assertEquals(2, value("Value: 1\nValue: 2\n", "Value"));
    }

    @Test
    void namespacedKeysAreStillParsed() {
        assertEquals(5, value("vortexskyblock:member: 5\n", "vortexskyblock:member"));
    }

    @Test
    void dashFollowedByScalarIsNotASequenceEntry() {
        assertEquals(-5, value("Offset:\n  -5\n", "Offset"));
    }

    @Test
    void scalarOnTheLineBelowItsKeyIsParsed() {
        assertEquals("some text", value("Value:\n  some text\n", "Value"));
    }

    @Test
    void complexKeysAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("? [a, b]\n: 1\n");
        assertTrue(error.getMessage().contains("complex"), error.getMessage());
    }

    @Test
    void unknownTagsAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("Value: !custom 5\n");
        assertTrue(error.getMessage().contains("tag"), error.getMessage());
    }

    @Test
    void customTagDirectivesAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("%TAG !e! tag:example.com,2000:app/\n---\nValue: 1\n");
        assertTrue(error.getMessage().contains("%TAG"), error.getMessage());
    }

    @Test
    void multipleDocumentsAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("---\nValue: 1\n---\nValue: 2\n");
        assertTrue(error.getMessage().contains("multiple YAML documents"), error.getMessage());
    }

    @Test
    void invalidEscapesAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("Value: \"\\q\"\n");
        assertTrue(error.getMessage().contains("escape"), error.getMessage());
    }

    @Test
    void unterminatedQuotedScalarsAreRejectedWithAClearMessage() {
        IllegalArgumentException error = rejected("Value: \"unterminated\n");
        assertTrue(error.getMessage().contains("unterminated"), error.getMessage());
    }

    @Test
    void documentsWithoutUnsupportedConstructsKeepTheirLayout() {
        String original = "# Header comment\n"
                + "Version: 1.0\n"
                + "\n"
                + "Settings:\n"
                + "  # Comment above key\n"
                + "  Delay: 20\n"
                + "  Enabled: true\n"
                + "\n"
                + "Messages:\n"
                + "  - \"Hello\"\n"
                + "  - \"World\"";

        YamlConfig config = YamlConfig.load(original);
        assertEquals(original.trim(), config.render().trim());
    }

    @Test
    void commentTextAndAlignmentSurviveARenderAndReload() {
        String original = "#     ASCII art banner\n"
                + "#    |  aligned  |\n"
                + "\n"
                + "Settings:\n"
                + "  #     nested banner\n"
                + "  Key: 1";

        YamlConfig config = YamlConfig.load(original);
        String rendered = config.render();

        assertTrue(rendered.contains("#     ASCII art banner"), rendered);
        assertTrue(rendered.contains("#    |  aligned  |"), rendered);
        assertTrue(rendered.contains("  #     nested banner"), rendered);
        assertEquals(original, rendered.trim());
    }

    @Test
    void supportedConstructsSurviveARenderAndReload() {
        String yaml = "Command: |\n  line1\n  line2\nItems: [a, b]\nValue: {a: 1}\nEmpty:\n";
        YamlConfig first = YamlConfig.load(yaml);

        YamlConfig second = YamlConfig.load(first.render());
        Object command = first.get("Command");
        Object items = first.get("Items");
        Object value = first.get("Value");
        assertEquals(command, second.get("Command"));
        assertEquals(items, second.get("Items"));
        assertEquals(value, second.get("Value"));
        assertNull(second.get("Empty"));
    }
}
