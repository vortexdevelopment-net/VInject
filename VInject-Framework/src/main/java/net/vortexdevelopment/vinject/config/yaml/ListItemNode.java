package net.vortexdevelopment.vinject.config.yaml;

public class ListItemNode extends YamlNode {
    private String value;

    public ListItemNode(int indentation, String value) {
        super(indentation);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(getIndentation())).append("- ").append(value);
        for (YamlNode child : getChildren()) {
            sb.append("\n").append(child.render());
        }
        return sb.toString();
    }
}
