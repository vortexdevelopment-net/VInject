package net.vortexdevelopment.vinject.config.yaml;

public class KeyValueNode extends KeyedNode {
    private String value;

    public KeyValueNode(int indentation, String key, String value) {
        super(indentation, key);
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
        return " ".repeat(getIndentation()) + getKey() + ": " + value;
    }
}
