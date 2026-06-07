package net.vortexdevelopment.vinject.analyzer;

import net.vortexdevelopment.vinject.analyzer.diagnostic.Diagnostic;
import net.vortexdevelopment.vinject.analyzer.diagnostic.DiagnosticSeverity;
import net.vortexdevelopment.vinject.analyzer.model.ApplicationModel;
import net.vortexdevelopment.vinject.analyzer.model.DependencyGraph;
import net.vortexdevelopment.vinject.analyzer.model.DependencyLoadPlan;

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
