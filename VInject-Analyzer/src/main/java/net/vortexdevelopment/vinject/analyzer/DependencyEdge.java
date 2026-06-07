package net.vortexdevelopment.vinject.analyzer;

import java.util.Objects;

public final class DependencyEdge {

    private final Class<?> source;
    private final Class<?> target;
    private final DependencyEdgeKind kind;
    private final String sourceMember;
    private final boolean required;
    private final boolean hard;

    public DependencyEdge(Class<?> source, Class<?> target, DependencyEdgeKind kind, String sourceMember, boolean required, boolean hard) {
        this.source = source;
        this.target = target;
        this.kind = kind;
        this.sourceMember = sourceMember;
        this.required = required;
        this.hard = hard;
    }

    public Class<?> getSource() {
        return source;
    }

    public Class<?> getTarget() {
        return target;
    }

    public DependencyEdgeKind getKind() {
        return kind;
    }

    public String getSourceMember() {
        return sourceMember;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isHard() {
        return hard;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DependencyEdge that)) return false;
        return required == that.required
                && hard == that.hard
                && Objects.equals(source, that.source)
                && Objects.equals(target, that.target)
                && kind == that.kind
                && Objects.equals(sourceMember, that.sourceMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, kind, sourceMember, required, hard);
    }
}
