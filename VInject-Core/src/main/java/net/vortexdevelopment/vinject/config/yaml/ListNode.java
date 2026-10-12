package net.vortexdevelopment.vinject.config.yaml;

public class ListNode extends KeyedNode {
    public ListNode(int indentation, String key) {
        super(indentation, key);
    }

    @Override
    public String render(RenderOptions options) {
        StringBuilder sb = new StringBuilder();
        String key = YamlPaths.formatMappingKey(getKey());
        sb.append(" ".repeat(getIndentation())).append(key).append(":");
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
