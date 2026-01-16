package net.vortexdevelopment.vinject.examples;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.util.EnableDebug;
import net.vortexdevelopment.vinject.annotation.util.EnableDebugFor;
import net.vortexdevelopment.vinject.annotation.util.SetSystemProperty;
import net.vortexdevelopment.vinject.debug.DebugLogger;

/**
 * Example demonstrating the debug logging system usage.
 */
public class DebugLoggingExample {
    
    // Example 1: Enable debug for THIS class
    @EnableDebug
    @Component
    public static class UserService {
        public void createUser(String name) {
            DebugLogger.log("Creating user: %s", name);  // Auto-detects UserService
            // ... user creation logic
            DebugLogger.log("User created successfully");
        }
    }
    
    // Example 2: Enable debug for OTHER classes
    @EnableDebugFor({UserService.class, OrderService.class})
    @SetSystemProperty(name = "app.mode", value = "development")
    @Component
    public static class TestApplication {
        public void run() {
            DebugLogger.log("Application started");  // Won't print (not enabled for TestApplication)
            
            UserService service = new UserService();
            service.createUser("John"); // WILL print debug messages
        }
    }
    
    // Example 3: Multiple system properties
    @SetSystemProperty(name = "app.mode", value = "dev")
    @SetSystemProperty(name = "feature.new_ui", value = "true")
    @Component
    public static class Configuration {
    }
    
    // Example 4: No annotations - debug disabled
    @Component
    public static class OrderService {
        public void processOrder() {
            DebugLogger.log("Processing order");  // Won't print unless enabled elsewhere
        }
    }
    
    // Example 5: Enable ALL debug via system property
    // Run with: java -Dvinject.debug.all=true
    public static void main(String[] args) {
        // All debug messages will print if -Dvinject.debug.all=true is set
        DebugLogger.log("Global debug enabled!");
    }
}
