package net.vortexdevelopment.plugin.vinject.discord;

import ai.grazie.utils.json.JSONObject;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class DiscordBridge {

    private static final Logger LOG = Logger.getInstance(DiscordBridge.class);
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Discord-IPC-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private IPCClient client;
    private final long clientId;
    private volatile boolean connected = false;

    public DiscordBridge(String clientId) {
        this.clientId = Long.parseLong(clientId);
        this.client = new IPCClient(this.clientId);
        LOG.info("DiscordBridge initialized with client ID: " + clientId);
    }

    public boolean isConnected() {
        LOG.info("Discord connection status: " + connected);
        return connected;
    }

    public CompletableFuture<Void> connect() {
        System.out.println("Connecting to Discord with client ID: " + clientId);

        return CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Starting Discord connection in background thread...");
                System.out.println("Thread: " + Thread.currentThread().getName());

                client.setListener(new IPCListener() {
                    @Override
                    public void onPacketSent(IPCClient client, Packet packet) {

                    }

                    @Override
                    public void onPacketReceived(IPCClient client, Packet packet) {

                    }

                    @Override
                    public void onActivityJoin(IPCClient client, String secret) {

                    }

                    @Override
                    public void onActivitySpectate(IPCClient client, String secret) {

                    }

                    @Override
                    public void onActivityJoinRequest(IPCClient client, String secret, User user) {

                    }

                    @Override
                    public void onReady(IPCClient client) {
                        System.out.println("✅ Discord RPC Ready!");
                        connected = true;
                    }

                    @Override
                    public void onClose(IPCClient client, JsonObject json) {
                        System.out.println("ℹ️ Discord RPC connection closed: " + json);
                        connected = false;
                    }

                    @Override
                    public void onDisconnect(IPCClient client, Throwable t) {
                        System.out.println("⚠️ Discord RPC disconnected");
                        if (t != null) {
                            System.err.println("Disconnect reason: " + t.getMessage());
                        }
                        connected = false;
                    }
                });

                System.out.println("About to call client.connect()...");
                client.connect();

                // Wait for connection to be established
                int attempts = 0;
                while (!connected && attempts < 50) { // 5 second timeout
                    Thread.sleep(100);
                    attempts++;
                }

                if (!connected) {
                    throw new RuntimeException("Discord connection timeout - Discord not running or RPC disabled");
                }

                System.out.println("✅ Discord connected successfully!");
                System.out.println("Connection status: " + connected);
            } catch (Exception e) {
                System.err.println(
                        "❌ Failed to connect to Discord: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Discord connection failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setPresence(DiscordPresenceBuilder presence) {
        System.out.println(
                "Setting Discord presence: line1='" + presence.getLine1() + "', line2='" + presence.getLine2() + "'");

        return CompletableFuture.runAsync(() -> {
            try {
                RichPresence.Builder builder = new RichPresence.Builder();

                // Set basic text
                if (presence.getLine1() != null) {
                    builder.setState(presence.getLine1());
                }
                if (presence.getLine2() != null) {
                    builder.setDetails(presence.getLine2());
                }
                builder.setActivityType(ActivityType.Playing);

                // Set timestamps
                if (presence.getStartTimestamp() > 0) {
                    builder.setStartTimestamp(presence.getStartTimestamp());
                }

                if (presence.getEndTimestamp() > 0) {
                    builder.setEndTimestamp(presence.getEndTimestamp());
                }

                // Set large image
                if (presence.getBigImage() != null) {
                    LOG.info("Setting large image: " + presence.getBigImage() + " with text: "
                            + presence.getBigImageText());
                    builder.setLargeImage(presence.getBigImage(), presence.getBigImageText());
                }

                // Set small image
                if (presence.getSmallImage() != null) {
                    LOG.info("Setting small image: " + presence.getSmallImage() + " with text: "
                            + presence.getSmallImageText());
                    builder.setSmallImage(presence.getSmallImage(), presence.getSmallImageText());
                }

                // Note about buttons
                if (!presence.getButtons().isEmpty()) {
                    LOG.info("Note: Buttons are not directly supported in jagrosh/DiscordIPC library");
                    // The jagrosh library doesn't support buttons in the same way
                    // Would need to implement via join/spectate secrets if needed
                }

                RichPresence richPresence = builder.build();
                client.sendRichPresence(richPresence);
                LOG.info("Discord presence set successfully!");

            } catch (Exception e) {
                LOG.error("Failed to set Discord presence", e);
                throw new RuntimeException("Set presence failed", e);
            }
        }, executor);
    }

    public void close() {
        try {
            LOG.info("Disconnecting from Discord...");
            if (client != null) {
                client.close();
            }
            connected = false;
            LOG.info("Discord disconnected successfully");
        } catch (Exception e) {
            LOG.error("Error disconnecting from Discord", e);
        }
    }
}