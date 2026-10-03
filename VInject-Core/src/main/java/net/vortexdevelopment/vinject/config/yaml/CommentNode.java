package net.vortexdevelopment.vinject.config.yaml;

public class CommentNode extends YamlNode {
    private final String comment;

    public CommentNode(int indentation, String comment) {
        super(indentation);
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public String render(RenderOptions options) {
        if (comment == null || !options.isIncludeComments()) return "";
        StringBuilder sb = new StringBuilder();
        String indentStr = " ".repeat(getIndentation());
        String[] lines = comment.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            sb.append(indentStr).append('#');
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))) {
                sb.append(' ');
            }
            sb.append(line);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }
}
