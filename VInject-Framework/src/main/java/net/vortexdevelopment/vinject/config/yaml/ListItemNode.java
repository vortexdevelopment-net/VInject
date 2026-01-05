package net.vortexdevelopment.vinject.config.yaml;

public class ListItemNode extends YamlNode {
    private Object value;

    public ListItemNode(int indentation, Object value) {
        super(indentation);
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String render(RenderOptions options) {
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(getIndentation())).append("-");
        String serialized = YamlValueFormatter.serialize(value);
        if (!serialized.equals("~")) {
            sb.append(" ").append(serialized);
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
