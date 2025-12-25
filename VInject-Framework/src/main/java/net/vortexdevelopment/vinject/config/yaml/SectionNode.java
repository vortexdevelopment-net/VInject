package net.vortexdevelopment.vinject.config.yaml;

public class SectionNode extends KeyedNode {
    public SectionNode(int indentation, String key) {
        super(indentation, key);
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(getIndentation())).append(getKey()).append(":");
        for (YamlNode child : getChildren()) {
            sb.append("\n").append(child.render());
        }
        return sb.toString();
    }
}
