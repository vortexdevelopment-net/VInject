package net.vortexdevelopment.plugin.vinject.discord;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service(Service.Level.APP)
public final class DiscordHook implements Disposable {

    private static final Logger LOG = Logger.getInstance(DiscordHook.class);
    private DiscordBridge bridge;
    private ExecutorService executorService;
    private boolean initialized = false;
    private boolean connected = false;
    private String currentClientId;

    public void init(Project project) {
        LOG.info("DiscordHook.init() called for project: " + project.getName());

        if (initialized) {
            LOG.warn("Discord RPC already initialized, skipping...");
            return; // Already initialized
        }

        try {
            // Get client ID from project settings, fallback to default if empty
            DiscordSettings settings = DiscordSettings.getInstance(project);
            String clientId = settings.getDiscordClientId();
            if (clientId == null || clientId.trim().isEmpty()) {
                clientId = "1387043651288432781"; // Default fallback
            }
            LOG.info("Initializing Discord RPC with client ID: " + clientId);

            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Discord-RPC-Thread");
                thread.setDaemon(true);
                LOG.info("Created Discord RPC thread: " + thread.getName());
                return thread;
            });

            LOG.info("Creating DiscordBridge instance...");
            bridge = new DiscordBridge(clientId);
            currentClientId = clientId;
            initialized = true;

            LOG.info("Discord RPC initialized successfully with client ID: " + clientId);
        } catch (Exception e) {
            LOG.error("Failed to initialize Discord RPC", e);
            // Print stack trace to console for immediate visibility
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> connect() {
        System.out.println("🔗 Attempting to connect to Discord RPC...");
        System.out.println("State - Initialized: " + initialized + ", Connected: " + connected + ", Bridge: "
                + (bridge != null ? "present" : "null"));

        if (!initialized || bridge == null) {
            String error = "Discord RPC not initialized (initialized=" + initialized + ", bridge="
                    + (bridge != null ? "present" : "null") + ")";
            System.out.println("❌ " + error);
            return CompletableFuture.failedFuture(new IllegalStateException(error));
        }

        if (connected) {
            System.out.println("✅ Discord RPC already connected, skipping connection attempt.");
            return CompletableFuture.completedFuture(null);
        }

        System.out.println("🔗 Connecting to Discord RPC with client ID: " + currentClientId);
        return bridge.connect()
                .thenRun(() -> {
                    connected = true;
                    System.out.println("✅ Discord RPC connected successfully!");
                })
                .exceptionally(throwable -> {
                    System.err.println("❌ Failed to connect to Discord RPC: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
    }

    public CompletableFuture<Void> updatePresence(DiscordPresenceBuilder presence) {
        LOG.info("DiscordHook.updatePresence() called");
        LOG.info("State - Initialized: " + initialized + ", Connected: " + connected + ", Bridge: "
                + (bridge != null ? "present" : "null"));

        if (!initialized || !connected || bridge == null) {
            LOG.warn("Cannot update presence - Discord not ready (initialized=" + initialized + ", connected="
                    + connected + ", bridge=" + (bridge != null ? "present" : "null") + ")");
            return CompletableFuture.completedFuture(null); // Silently ignore if not connected
        }

        LOG.info("Updating Discord presence...");
        return bridge.setPresence(presence)
                .thenRun(() -> {
                    LOG.info("Discord presence updated successfully!");
                    System.out.println("✅ Discord presence updated: " + presence.getLine1());
                })
                .exceptionally(throwable -> {
                    LOG.error("Failed to update Discord presence", throwable);
                    System.err.println("❌ Failed to update Discord presence: " + throwable.getMessage());
                    return null;
                });
    }

    public void disconnect() {
        if (bridge != null && connected) {
            try {
                bridge.close();
                connected = false;
                LOG.info("Discord RPC disconnected");
            } catch (Exception e) {
                LOG.warn("Error disconnecting from Discord RPC", e);
            }
        }
    }

    public void shutdown() {
        disconnect();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        initialized = false;
        bridge = null;
        currentClientId = null;
    }

    public boolean isConnected() {
        boolean result = initialized && connected && bridge != null && bridge.isConnected();
        LOG.info("isConnected() check - initialized=" + initialized + ", connected=" + connected +
                ", bridge=" + (bridge != null ? "present" : "null") +
                ", bridgeConnected=" + (bridge != null ? bridge.isConnected() : "N/A") +
                ", result=" + result);
        return result;
    }

    public boolean isEnabled(Project project) {
        try {
            DiscordSettings settings = DiscordSettings.getInstance(project);
            boolean enabled = settings.isDiscordRpcEnabled();
            LOG.info("Discord RPC enabled for project " + project.getName() + ": " + enabled);
            return enabled;
        } catch (Exception e) {
            LOG.error("Error checking if Discord RPC is enabled", e);
            return false;
        }
    }

    /**
     * Test Discord connection with detailed debugging output
     * Call this method to manually test the Discord integration
     */
    public void testConnection(Project project) {
        System.out.println("🧪 === DISCORD CONNECTION TEST START ===");

        try {
            // Step 1: Check if enabled
            System.out.println("Step 1: Checking if Discord RPC is enabled...");
            boolean enabled = isEnabled(project);
            System.out.println("   Result: " + (enabled ? "✅ ENABLED" : "❌ DISABLED"));

            if (!enabled) {
                System.out.println("❌ Test failed: Discord RPC is disabled in settings");
                return;
            }

            // Step 2: Initialize
            System.out.println("Step 2: Initializing Discord RPC...");
            init(project);
            System.out.println("   Result: " + (initialized ? "✅ INITIALIZED" : "❌ FAILED"));

            // Step 3: Check bridge
            System.out.println("Step 3: Checking bridge instance...");
            System.out.println("   Bridge: " + (bridge != null ? "✅ CREATED" : "❌ NULL"));
            System.out.println("   Client ID: " + currentClientId);

            // Step 4: Test connection
            System.out.println("Step 4: Testing Discord connection...");
            connect().thenRun(() -> {
                System.out.println("   Result: ✅ CONNECTION SUCCESSFUL!");

                // Step 5: Test presence
                System.out.println("Step 5: Testing presence update...");
                DiscordPresenceBuilder testPresence = new DiscordPresenceBuilder()
                        .setLine1("Testing VInject Plugin")
                        .setLine2("Connection test successful!");

                updatePresence(testPresence).thenRun(() -> {
                    System.out.println("   Result: ✅ PRESENCE UPDATE SUCCESSFUL!");
                    System.out.println("🎉 === DISCORD CONNECTION TEST COMPLETED SUCCESSFULLY ===");
                }).exceptionally(presenceError -> {
                    System.err.println("   Result: ❌ PRESENCE UPDATE FAILED: " + presenceError.getMessage());
                    System.err.println("🧪 === DISCORD CONNECTION TEST COMPLETED WITH ERRORS ===");
                    return null;
                });

            }).exceptionally(connectionError -> {
                System.err.println("   Result: ❌ CONNECTION FAILED: " + connectionError.getMessage());
                System.err.println("💡 Possible issues:");
                System.err.println("   - Discord is not running");
                System.err.println("   - Discord RPC is disabled in Discord settings");
                System.err.println("   - Firewall blocking connection");
                System.err.println("   - Invalid client ID: " + currentClientId);
                System.err.println("🧪 === DISCORD CONNECTION TEST FAILED ===");
                return null;
            });

        } catch (Exception e) {
            System.err.println("❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.err.println("🧪 === DISCORD CONNECTION TEST FAILED ===");
        }
    }

    public static DiscordHook getInstance() {
        return ApplicationManager.getApplication().getService(DiscordHook.class);
    }

    @Override
    public void dispose() {
        shutdown();
    }

}
