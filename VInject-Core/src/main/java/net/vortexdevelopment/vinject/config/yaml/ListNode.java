package net.vortexdevelopment.vinject.config.yaml;

public class ListNode extends KeyedNode {
    public ListNode(int indentation, String key) {
        super(indentation, key);
    }

    @Override
    public String render(RenderOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(getIndentation())).append(YamlValueFormatter.serializeKey(getKey())).append(":");
        if (getChildren().stream().noneMatch(ListItemNode.class::isInstance)) {
            sb.append(" []");
        }
        for (YamlNode child : getChildren()) {
            String rendered = child.render(options);
            if (!rendered.isEmpty() || child instanceof BlankLineNode) {
                sb.append("\n").append(rendered);
            }
        }
        return sb.toString();
    }
}
