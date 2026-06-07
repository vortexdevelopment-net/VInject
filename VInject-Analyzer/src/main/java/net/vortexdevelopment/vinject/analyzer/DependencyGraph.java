package net.vortexdevelopment.vinject.analyzer;

import java.util.List;

public record DependencyGraph(List<DependencyEdge> edges) {

    public DependencyGraph(List<DependencyEdge> edges) {
        this.edges = List.copyOf(edges);
    }
}
