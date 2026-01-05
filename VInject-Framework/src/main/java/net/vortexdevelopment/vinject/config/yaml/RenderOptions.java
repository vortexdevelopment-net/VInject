package net.vortexdevelopment.vinject.config.yaml;

/**
 * Options for rendering YAML output.
 */
public class RenderOptions {
    private boolean includeComments = true;
    private int indentStep = 2;

    public static RenderOptions defaultOptions() {
        return new RenderOptions();
    }

    public boolean isIncludeComments() {
        return includeComments;
    }

    public RenderOptions setIncludeComments(boolean includeComments) {
        this.includeComments = includeComments;
        return this;
    }

    public int getIndentStep() {
        return indentStep;
    }

    public RenderOptions setIndentStep(int indentStep) {
        this.indentStep = indentStep;
        return this;
    }
}
