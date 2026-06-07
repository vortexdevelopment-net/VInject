package net.vortexdevelopment.vinject.analyzer;

import java.util.Objects;

public final class Diagnostic {

    private final String code;
    private final DiagnosticSeverity severity;
    private final String message;
    private final DiagnosticLocation location;
    private final String suggestedFix;

    public Diagnostic(String code, DiagnosticSeverity severity, String message, DiagnosticLocation location, String suggestedFix) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.location = location;
        this.suggestedFix = suggestedFix;
    }

    public static Diagnostic error(String code, String message, DiagnosticLocation location) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, message, location, null);
    }

    public static Diagnostic warning(String code, String message, DiagnosticLocation location) {
        return new Diagnostic(code, DiagnosticSeverity.WARNING, message, location, null);
    }

    public String getCode() {
        return code;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public DiagnosticLocation getLocation() {
        return location;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    @Override
    public String toString() {
        return severity + " " + code + " " + location + " - " + message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Diagnostic that)) return false;
        return Objects.equals(code, that.code)
                && severity == that.severity
                && Objects.equals(message, that.message)
                && Objects.equals(location, that.location)
                && Objects.equals(suggestedFix, that.suggestedFix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, severity, message, location, suggestedFix);
    }
}
