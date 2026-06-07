package net.vortexdevelopment.vinject.analyzer;

import java.util.Objects;

public final class DiagnosticLocation {

    private final String className;
    private final String memberName;

    public DiagnosticLocation(String className, String memberName) {
        this.className = className;
        this.memberName = memberName;
    }

    public static DiagnosticLocation classLocation(Class<?> clazz) {
        return new DiagnosticLocation(clazz != null ? clazz.getName() : null, null);
    }

    public static DiagnosticLocation memberLocation(Class<?> clazz, String memberName) {
        return new DiagnosticLocation(clazz != null ? clazz.getName() : null, memberName);
    }

    public String getClassName() {
        return className;
    }

    public String getMemberName() {
        return memberName;
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

    @Override
    public int hashCode() {
        return Objects.hash(className, memberName);
    }
}
