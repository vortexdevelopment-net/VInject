package net.vortexdevelopment.vinject.analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record VinjectAnalysisResult(ApplicationModel applicationModel,
                                    DependencyGraph dependencyGraph,
                                    DependencyLoadPlan loadPlan,
                                    List<Diagnostic> diagnostics) {

    public VinjectAnalysisResult(ApplicationModel applicationModel, DependencyGraph dependencyGraph, DependencyLoadPlan loadPlan, List<Diagnostic> diagnostics) {
        this.applicationModel = applicationModel;
        this.dependencyGraph = dependencyGraph;
        this.loadPlan = loadPlan;
        this.diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
    }
}
