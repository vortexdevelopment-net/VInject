package net.vortexdevelopment.vinject.analyzer.diagnostic;

import java.util.Objects;

public record DiagnosticLocation(String className, String memberName) {

    public static DiagnosticLocation unknown() {
        return new DiagnosticLocation(null, null);
    }

    public static DiagnosticLocation classLocation(Class<?> clazz) {
        return new DiagnosticLocation(clazz != null ? clazz.getName() : null, null);
    }

    public static DiagnosticLocation memberLocation(Class<?> clazz, String memberName) {
        return new DiagnosticLocation(clazz != null ? clazz.getName() : null, memberName);
    }

    @Override
    public String toString() {
        if (className == null) {
            return "<unknown>";
        }
        return memberName == null ? className : className + "#" + memberName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagnosticLocation that)) return false;
        return Objects.equals(className, that.className) && Objects.equals(memberName, that.memberName);
    }

}
