package net.vortexdevelopment.vinject.analyzer.model;

import java.util.Objects;

public record DependencyEdge(Class<?> source, Class<?> target,
                             DependencyEdgeKind kind,
                             String sourceMember,
                             boolean required,
                             boolean hard) {

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

}
