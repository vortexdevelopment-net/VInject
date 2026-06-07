package net.vortexdevelopment.vinject.config.yaml;

public class DocumentNode extends YamlNode {
    public DocumentNode() {
        super(0);
    }

    @Override
    public String render(RenderOptions options) {
        StringBuilder sb = new StringBuilder();
        for (YamlNode child : getChildren()) {
            String rendered = child.render(options);
            if (!rendered.isEmpty() || child instanceof BlankLineNode) {
                sb.append(rendered).append("\n");
            }
        }
        return sb.toString();
    }
}
