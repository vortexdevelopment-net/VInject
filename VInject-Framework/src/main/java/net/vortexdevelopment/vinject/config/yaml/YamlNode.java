package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayList;
import java.util.List;

public abstract class YamlNode {
    private final int indentation;
    private YamlNode parent;
    private final List<YamlNode> children = new ArrayList<>();

    protected YamlNode(int indentation) {
        this.indentation = indentation;
    }

    public int getIndentation() {
        return indentation;
    }

    public YamlNode getParent() {
        return parent;
    }

    public void setParent(YamlNode parent) {
        this.parent = parent;
    }

    public List<YamlNode> getChildren() {
        return children;
    }

    public void addChild(YamlNode child) {
        child.setParent(this);
        children.add(child);
    }

    public abstract String render();
}
