package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayList;
import java.util.List;

public abstract class KeyedNode extends YamlNode {
    private final String key;

    protected KeyedNode(int indentation, String key) {
        super(indentation);
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getFullDotPath() {
        List<String> parts = new ArrayList<>();
        YamlNode current = this;
        while (current != null) {
            if (current instanceof KeyedNode keyedNode) {
                parts.add(0, keyedNode.getKey());
            }
            current = current.getParent();
        }
        return String.join(".", parts);
    }
}
