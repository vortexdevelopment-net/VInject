package net.vortexdevelopment.vinject.config.yaml;

public class DocumentNode extends YamlNode {
    public DocumentNode() {
        super(0);
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (YamlNode child : getChildren()) {
            sb.append(child.render()).append("\n");
        }
        return sb.toString();
    }
}
