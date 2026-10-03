package $PACKAGE$.security;

public class UserContext {
    
    // Using Java ScopedValues for request-scoped data
    public static final ScopedValue<String> USER = ScopedValue.newInstance();

    public static String getCurrentUser() {
        return USER.isBound() ? USER.get() : "Anonymous";
    }
}
