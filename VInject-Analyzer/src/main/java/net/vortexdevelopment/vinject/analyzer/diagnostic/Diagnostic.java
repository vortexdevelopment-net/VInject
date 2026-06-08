package net.vortexdevelopment.vinject.analyzer.diagnostic;

import java.util.Objects;

public record Diagnostic(String code,
                         DiagnosticSeverity severity,
                         String message,
                         DiagnosticLocation location,
                         String suggestedFix) {

    public static Diagnostic error(String code, String message, DiagnosticLocation location) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, message, location, null);
    }

    public static Diagnostic error(DiagnosticCode code) {
        return error(code, DiagnosticLocation.unknown());
    }

    public static Diagnostic error(DiagnosticCode code, Object... messageArgs) {
        return error(code, DiagnosticLocation.unknown(), messageArgs);
    }

    public static Diagnostic error(DiagnosticCode code, String message, DiagnosticLocation location) {
        return error(code.code(), message, location);
    }

    public static Diagnostic error(DiagnosticCode code, DiagnosticLocation location, Object... messageArgs) {
        return error(code.code(), code.format(messageArgs), location);
    }

    public static Diagnostic warning(String code, String message, DiagnosticLocation location) {
        return new Diagnostic(code, DiagnosticSeverity.WARNING, message, location, null);
    }

    public static Diagnostic warning(DiagnosticCode code) {
        return warning(code, DiagnosticLocation.unknown());
    }

    public static Diagnostic warning(DiagnosticCode code, Object... messageArgs) {
        return warning(code, DiagnosticLocation.unknown(), messageArgs);
    }

    public static Diagnostic warning(DiagnosticCode code, String message, DiagnosticLocation location) {
        return warning(code.code(), message, location);
    }

    public static Diagnostic warning(DiagnosticCode code, DiagnosticLocation location, Object... messageArgs) {
        return warning(code.code(), code.format(messageArgs), location);
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

}
