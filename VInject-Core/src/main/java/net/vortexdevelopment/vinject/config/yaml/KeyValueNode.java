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
        String path = getFullDotPath();
        int indentStep = options != null ? options.getIndentStep() : 2;
        return " ".repeat(getIndentation()) + YamlPaths.formatMappingKey(getKey()) + ": "
                + YamlValueFormatter.serialize(value, path, getIndentation(), indentStep);
    }
}
