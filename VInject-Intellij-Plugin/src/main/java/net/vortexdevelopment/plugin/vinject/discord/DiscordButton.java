package net.vortexdevelopment.plugin.vinject.discord;

public class DiscordButton {
    private final String label;
    private final String url;

    public DiscordButton(String label, String url) {
        this.label = label;
        this.url = url;
    }

    public String getLabel() {
        return this.label;
    }

    public String getUrl() {
        return this.url;
    }
}