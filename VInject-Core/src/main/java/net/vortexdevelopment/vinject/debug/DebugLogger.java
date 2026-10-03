package net.vortexdevelopment.vinject.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug logger utility that auto-detects the calling class.
 * Classes can be enabled for debug logging via @EnableDebug or @EnableDebugFor annotations.
 */
public class DebugLogger {
    
    private static final Set<String> enabledClasses = ConcurrentHashMap.newKeySet();
    private static final boolean GLOBAL_DEBUG = Boolean.getBoolean("vinject.debug.all");
    
    /**
     * Enable debug logging for a specific class.
     * Called by the lifecycle manager when processing @EnableDebug annotations.
     */
    public static void enableDebugFor(Class<?> clazz) {
        enabledClasses.add(clazz.getName());
    }
    
    /**
     * Enable debug logging for multiple classes.
     * Called by the lifecycle manager when processing @EnableDebugFor annotations.
     */
    public static void enableDebugFor(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            enabledClasses.add(clazz.getName());
        }
    }
    
    /**
     * Check if debug logging is enabled for a class.
     */
    public static boolean isEnabled(Class<?> clazz) {
        return GLOBAL_DEBUG || enabledClasses.contains(clazz.getName());
    }
    
    /**
     * Log a debug message. Automatically detects the calling class.
     * 
     * @param message the debug message
     */
    public static void log(String message) {
        Class<?> callerClass = getCallerClass();
        if (callerClass != null && isEnabled(callerClass)) {
            System.out.println("[DEBUG:" + callerClass.getSimpleName() + "] " + message);
        }
    }
    
    /**
     * Log a debug message with formatted arguments. Automatically detects the calling class.
     * 
     * @param format the format string
     * @param args the arguments
     */
    public static void log(String format, Object... args) {
        Class<?> callerClass = getCallerClass();
        if (callerClass != null && isEnabled(callerClass)) {
            String message = String.format(format, args);
            System.out.println("[DEBUG:" + callerClass.getSimpleName() + "] " + message);
        }
    }
    
    /**
     * Log a debug message for a specific class (fallback when auto-detection fails).
     * 
     * @param clazz the class to log for
     * @param message the debug message
     */
    public static void log(Class<?> clazz, String message) {
        if (isEnabled(clazz)) {
            System.out.println("[DEBUG:" + clazz.getSimpleName() + "] " + message);
        }
    }
    
    /**
     * Log a formatted debug message for a specific class.
     * 
     * @param clazz the class to log for
     * @param format the format string
     * @param args the arguments
     */
    public static void log(Class<?> clazz, String format, Object... args) {
        if (isEnabled(clazz)) {
            String message = String.format(format, args);
            System.out.println("[DEBUG:" + clazz.getSimpleName() + "] " + message);
        }
    }
    
    /**
     * Auto-detect the calling class using StackWalker (Java 9+).
     */
    private static Class<?> getCallerClass() {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames
                            .skip(2) // Skip DebugLogger.log() and DebugLogger.getCallerClass()
                            .findFirst()
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .orElse(null));
        } catch (Exception e) {
            // Fallback if StackWalker fails
            return null;
        }
    }
    
    /**
     * Clear all enabled debug classes (for testing).
     */
    public static void clearAll() {
        enabledClasses.clear();
    }
}
