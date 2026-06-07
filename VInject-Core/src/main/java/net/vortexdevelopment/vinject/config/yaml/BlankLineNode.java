package net.vortexdevelopment.vinject.config.yaml;

public class BlankLineNode extends YamlNode {
    public BlankLineNode(int indentation) {
        super(indentation);
    }

    @Override
    public String render(RenderOptions options) {
        return "";
    }
}
