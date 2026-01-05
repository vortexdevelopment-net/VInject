package net.vortexdevelopment.vinject.config.yaml;

public class KeyValueNode extends KeyedNode {
    private Object value;

    public KeyValueNode(int indentation, String key, Object value) {
        super(indentation, key);
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
        return " ".repeat(getIndentation()) + getKey() + ": " + YamlValueFormatter.serialize(value);
    }
}
