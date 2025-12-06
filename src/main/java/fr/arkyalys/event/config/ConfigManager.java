package fr.arkyalys.event.config;

import fr.arkyalys.event.YouTubeEventPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final YouTubeEventPlugin plugin;

    // YouTube settings
    private String apiKey;
    private String channelId;
    private String channelUsername;  // @username (ex: RayniseG)
    private int autoDetectInterval;
    private int pollInterval;
    private int maxMessagesPerPoll;
    private int likeCheckInterval;
    private int viewMilestone;

    // Provider settings (InnerTube vs Data API)
    private boolean preferInnerTube;
    private boolean fallbackToDataAPI;

    // Messages
    private String prefix;
    private String messageConnected;
    private String messageDisconnected;
    private String messageNewMessage;
    private String messageSuperChat;

    // Triggers config
    private Map<String, TriggerConfig> triggers;
    private Map<String, TriggerConfig> keywordTriggers;

    public ConfigManager(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.triggers = new HashMap<>();
        this.keywordTriggers = new HashMap<>();
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        // YouTube settings
        this.apiKey = config.getString("youtube.api-key", "YOUR_API_KEY_HERE");
        this.channelId = config.getString("youtube.channel-id", "");
        this.channelUsername = config.getString("youtube.channel-username", "");  // @username (sans @)
        this.autoDetectInterval = config.getInt("youtube.auto-detect-interval", 30);
        this.pollInterval = config.getInt("youtube.poll-interval", 3);
        this.maxMessagesPerPoll = config.getInt("youtube.max-messages-per-poll", 200);
        this.likeCheckInterval = config.getInt("youtube.like-check-interval", 10);
        this.viewMilestone = config.getInt("youtube.view-milestone", 100);

        // Provider settings
        this.preferInnerTube = config.getBoolean("youtube.prefer-innertube", true);
        this.fallbackToDataAPI = config.getBoolean("youtube.fallback-to-api", true);

        // Messages
        this.prefix = colorize(config.getString("messages.prefix", "&6[&eYouTube&6] &r"));
        this.messageConnected = colorize(config.getString("messages.connected", "&aConnecte au live YouTube!"));
        this.messageDisconnected = colorize(config.getString("messages.disconnected", "&cDeconnecte du live YouTube."));
        this.messageNewMessage = colorize(config.getString("messages.new-message", "&7%viewer%: &f%message%"));
        this.messageSuperChat = colorize(config.getString("messages.super-chat", "&6[SuperChat %amount%] &e%viewer%: &f%message%"));

        // Load triggers
        loadTriggers(config);
        loadKeywordTriggers(config);
    }

    private void loadTriggers(FileConfiguration config) {
        triggers.clear();

        ConfigurationSection triggersSection = config.getConfigurationSection("triggers");
        if (triggersSection == null) return;

        for (String triggerName : triggersSection.getKeys(false)) {
            if (triggerName.equals("keywords")) continue; // Handled separately

            ConfigurationSection section = triggersSection.getConfigurationSection(triggerName);
            if (section == null) continue;

            TriggerConfig trigger = new TriggerConfig();
            trigger.enabled = section.getBoolean("enabled", true);
            trigger.chance = section.getDouble("chance", 100.0);
            trigger.cooldown = section.getInt("cooldown", 0);
            trigger.actions = section.getMapList("actions");

            triggers.put(triggerName, trigger);
        }
    }

    private void loadKeywordTriggers(FileConfiguration config) {
        keywordTriggers.clear();

        ConfigurationSection keywordsSection = config.getConfigurationSection("triggers.keywords");
        if (keywordsSection == null) return;

        for (String keyword : keywordsSection.getKeys(false)) {
            ConfigurationSection section = keywordsSection.getConfigurationSection(keyword);
            if (section == null) continue;

            TriggerConfig trigger = new TriggerConfig();
            trigger.enabled = section.getBoolean("enabled", true);
            trigger.chance = section.getDouble("chance", 100.0);
            trigger.cooldown = section.getInt("cooldown", 0);
            trigger.actions = section.getMapList("actions");

            keywordTriggers.put(keyword.toLowerCase(), trigger);
        }
    }

    private String colorize(String text) {
        if (text == null) return "";
        return text.replace("&", "\u00A7");
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        plugin.getConfig().set("youtube.api-key", apiKey);
        plugin.saveConfig();
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
        plugin.getConfig().set("youtube.channel-id", channelId);
        plugin.saveConfig();
    }

    // Getters
    public String getApiKey() {
        return apiKey;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getChannelUsername() {
        return channelUsername;
    }

    public void setChannelUsername(String username) {
        // Enlever le @ si présent
        this.channelUsername = username.startsWith("@") ? username.substring(1) : username;
        plugin.getConfig().set("youtube.channel-username", this.channelUsername);
        plugin.saveConfig();
    }

    public int getAutoDetectInterval() {
        return autoDetectInterval;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public int getLikeCheckInterval() {
        return likeCheckInterval;
    }

    public int getViewMilestone() {
        return viewMilestone;
    }

    public boolean isPreferInnerTube() {
        return preferInnerTube;
    }

    public boolean isFallbackToDataAPI() {
        return fallbackToDataAPI;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getMessageConnected() {
        return messageConnected;
    }

    public String getMessageDisconnected() {
        return messageDisconnected;
    }

    public String getMessageNewMessage() {
        return messageNewMessage;
    }

    public String getMessageSuperChat() {
        return messageSuperChat;
    }

    public TriggerConfig getTrigger(String name) {
        return triggers.get(name);
    }

    public Map<String, TriggerConfig> getKeywordTriggers() {
        return keywordTriggers;
    }

    /**
     * Configuration d'un trigger
     */
    public static class TriggerConfig {
        public boolean enabled = true;
        public double chance = 100.0;
        public int cooldown = 0;
        public List<Map<?, ?>> actions;
    }
}
