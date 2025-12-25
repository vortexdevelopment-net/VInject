package net.vortexdevelopment.vinject.config.yaml;

public class YamlWriter {
    public String write(DocumentNode root) {
        return root.render();
    }
}
