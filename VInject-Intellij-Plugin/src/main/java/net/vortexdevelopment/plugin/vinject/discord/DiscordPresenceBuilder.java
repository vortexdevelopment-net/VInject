package net.vortexdevelopment.plugin.vinject.discord;

import java.util.ArrayList;
import java.util.List;

public class DiscordPresenceBuilder {
    private String line1;
    private String line2;
    private String bigImage;
    private String bigImageText;
    private String smallImage;
    private String smallImageText;
    private long startTimestamp;
    private long endTimestamp;
    private List<DiscordButton> buttons;

    public DiscordPresenceBuilder() {
        this.buttons = new ArrayList<>();
        this.startTimestamp = System.currentTimeMillis();
    }

    public DiscordPresenceBuilder setLine1(String line1) {
        this.line1 = line1;
        return this;
    }

    public DiscordPresenceBuilder setLine2(String line2) {
        this.line2 = line2;
        return this;
    }

    public DiscordPresenceBuilder setBigImage(String key) {
        this.bigImage = key;
        return this;
    }

    public DiscordPresenceBuilder setBigImageText(String bigImageText) {
        this.bigImageText = bigImageText;
        return this;
    }

    public DiscordPresenceBuilder setSmallImage(String key) {
        this.smallImage = key;
        return this;
    }

    public DiscordPresenceBuilder setSmallImageText(String smallImageText) {
        this.smallImageText = smallImageText;
        return this;
    }

    public DiscordPresenceBuilder setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
        return this;
    }

    public DiscordPresenceBuilder setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
        return this;
    }

    public DiscordPresenceBuilder addButton(String label, String url) {
        if (this.buttons.size() < 2) { // Discord allows max 2 buttons
            this.buttons.add(new DiscordButton(label, url));
        }
        return this;
    }

    public DiscordPresenceBuilder addButton(DiscordButton button) {
        if (this.buttons.size() < 2) { // Discord allows max 2 buttons
            this.buttons.add(button);
        }
        return this;
    }

    public DiscordPresenceBuilder clearButtons() {
        this.buttons.clear();
        return this;
    }

    // Getters
    public String getLine1() {
        return this.line1;
    }

    public String getLine2() {
        return this.line2;
    }

    public String getBigImage() {
        return this.bigImage;
    }

    public String getBigImageText() {
        return this.bigImageText;
    }

    public String getSmallImage() {
        return this.smallImage;
    }

    public String getSmallImageText() {
        return this.smallImageText;
    }

    public long getStartTimestamp() {
        return this.startTimestamp;
    }

    public long getEndTimestamp() {
        return this.endTimestamp;
    }

    public List<DiscordButton> getButtons() {
        return new ArrayList<>(this.buttons); // Return copy to prevent external modification
    }

    // Utility methods for common presence patterns
    public static DiscordPresenceBuilder createEditing(String fileName, String projectName) {
        return new DiscordPresenceBuilder()
                .setLine1("Editing " + fileName)
                .setLine2("Project: " + projectName)
                .setBigImage("intellij-logo")
                .setBigImageText("VInject Plugin for IntelliJ IDEA")
                .setSmallImage("editing-file-icon")
                .setSmallImageText("Coding with VInject");
    }
    
    public static DiscordPresenceBuilder createCustom(String topLine, String bottomLine) {
        return new DiscordPresenceBuilder()
                .setLine1(topLine)
                .setLine2(bottomLine)
                .setBigImage("intellij-logo")
                .setBigImageText("VInject Plugin")
                .setSmallImage("vinject-icon")
                .setSmallImageText("Dependency Injection");
    }

    public static DiscordPresenceBuilder createIdle(String projectName) {
        return new DiscordPresenceBuilder()
                .setLine1("Browsing code")
                .setLine2("Project: " + projectName)
                .setBigImage("intellij-logo")
                .setBigImageText("IntelliJ IDEA")
                .setSmallImage("idle-icon")
                .setSmallImageText("Idle");
    }

    public static DiscordPresenceBuilder createDebugging(String fileName, String projectName) {
        return new DiscordPresenceBuilder()
                .setLine1("Debugging " + fileName)
                .setLine2("Project: " + projectName)
                .setBigImage("intellij-logo")
                .setBigImageText("IntelliJ IDEA")
                .setSmallImage("debug-icon")
                .setSmallImageText("Debugging");
    }
}
