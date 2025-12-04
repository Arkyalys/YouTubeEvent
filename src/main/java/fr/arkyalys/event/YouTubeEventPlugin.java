package fr.arkyalys.event;

import fr.arkyalys.event.api.YouTubeEventAPI;
import fr.arkyalys.event.api.events.YouTubeConnectionEvent;
import fr.arkyalys.event.commands.YouTubeCommand;
import fr.arkyalys.event.config.ConfigManager;
import fr.arkyalys.event.display.YouTubeDisplay;
import fr.arkyalys.event.events.EventManager;
import fr.arkyalys.event.youtube.YouTubeAPI;
import fr.arkyalys.event.youtube.LiveChatPoller;
import fr.arkyalys.event.youtube.LiveAutoDetector;
import fr.arkyalys.event.youtube.LikeTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class YouTubeEventPlugin extends JavaPlugin {

    private static YouTubeEventPlugin instance;

    private ConfigManager configManager;
    private YouTubeAPI youtubeAPI;
    private LiveChatPoller liveChatPoller;
    private LiveAutoDetector autoDetector;
    private LikeTracker likeTracker;
    private EventManager eventManager;
    private YouTubeDisplay display;

    // Le joueur cible des événements (le streamer)
    private UUID targetPlayer;

    @Override
    public void onEnable() {
        instance = this;

        // Sauvegarder la config par défaut
        saveDefaultConfig();

        // Initialiser les managers
        this.configManager = new ConfigManager(this);
        this.eventManager = new EventManager(this);
        this.youtubeAPI = new YouTubeAPI(this);

        // Configurer les préférences de provider
        youtubeAPI.setPreferInnerTube(configManager.isPreferInnerTube());
        youtubeAPI.setFallbackToDataAPI(configManager.isFallbackToDataAPI());

        this.liveChatPoller = new LiveChatPoller(this);
        this.autoDetector = new LiveAutoDetector(this);
        this.likeTracker = new LikeTracker(this);
        this.display = new YouTubeDisplay(this);

        // Enregistrer les commandes
        getCommand("youtube").setExecutor(new YouTubeCommand(this));
        getCommand("yt").setExecutor(new YouTubeCommand(this));

        // Initialiser l'API publique
        YouTubeEventAPI.init(this);

        getLogger().info("========================================");
        getLogger().info("  YouTubeEvent v" + getDescription().getVersion());
        getLogger().info("  Plugin charge avec succes!");
        getLogger().info("----------------------------------------");
        getLogger().info("  Provider: " + (configManager.isPreferInnerTube() ? "InnerTube (0 quota)" : "Data API v3"));
        getLogger().info("  Fallback: " + (configManager.isFallbackToDataAPI() ? "Actif" : "Desactive"));
        getLogger().info("  API: Disponible pour les autres plugins");
        getLogger().info("========================================");

        // Vérifier la configuration
        if (configManager.getApiKey().isEmpty() || configManager.getApiKey().equals("YOUR_API_KEY_HERE")) {
            getLogger().warning("Cle API YouTube non configuree!");
            getLogger().warning("Utilisez /youtube setkey <cle> pour configurer");
        } else {
            // Démarrer la détection automatique si configurée
            if (!configManager.getChannelId().isEmpty()) {
                autoDetector.start();
            }
        }
    }

    @Override
    public void onDisable() {
        // Arrêter la détection automatique
        if (autoDetector != null) {
            autoDetector.stop();
        }

        // Arrêter le polling
        if (liveChatPoller != null) {
            liveChatPoller.stop();
        }

        // Arrêter le tracking des likes
        if (likeTracker != null) {
            likeTracker.stop();
        }

        getLogger().info("YouTubeEvent desactive.");
    }

    /**
     * Recharge la configuration du plugin
     */
    public void reload() {
        reloadConfig();
        configManager.reload();
        eventManager.reload();

        // Reconfigurer les préférences de provider
        youtubeAPI.setPreferInnerTube(configManager.isPreferInnerTube());
        youtubeAPI.setFallbackToDataAPI(configManager.isFallbackToDataAPI());

        // Redémarrer la détection auto si nécessaire
        autoDetector.stop();
        if (!configManager.getChannelId().isEmpty() && configManager.getAutoDetectInterval() > 0) {
            autoDetector.start();
        }

        getLogger().info("Configuration rechargee!");
        getLogger().info("Provider: " + (configManager.isPreferInnerTube() ? "InnerTube (0 quota)" : "Data API v3"));
    }

    /**
     * Démarre la connexion au live YouTube
     * @param liveId L'ID du live ou l'URL
     * @return true si démarré avec succès
     */
    public boolean startLive(String liveId) {
        if (configManager.getApiKey().isEmpty()) {
            return false;
        }

        // Extraire l'ID du live si c'est une URL
        String extractedId = extractLiveId(liveId);

        // Démarrer le tracking des likes/vues
        likeTracker.start(extractedId);

        // Démarrer l'affichage (bossbars + actionbar)
        display.startLive();
        display.showAll();

        return liveChatPoller.start(extractedId);
    }

    /**
     * Arrête la connexion au live YouTube
     */
    public void stopLive() {
        liveChatPoller.stop();
        likeTracker.stop();
        display.stopLive();
    }

    /**
     * Extrait l'ID du live depuis une URL YouTube
     */
    private String extractLiveId(String input) {
        // Si c'est déjà un ID (11 caractères)
        if (input.length() == 11 && !input.contains("/")) {
            return input;
        }

        // youtube.com/watch?v=XXXXXXXXXXX
        if (input.contains("v=")) {
            int start = input.indexOf("v=") + 2;
            int end = input.indexOf("&", start);
            return end == -1 ? input.substring(start) : input.substring(start, end);
        }

        // youtu.be/XXXXXXXXXXX
        if (input.contains("youtu.be/")) {
            int start = input.indexOf("youtu.be/") + 9;
            int end = input.indexOf("?", start);
            return end == -1 ? input.substring(start) : input.substring(start, end);
        }

        // youtube.com/live/XXXXXXXXXXX
        if (input.contains("/live/")) {
            int start = input.indexOf("/live/") + 6;
            int end = input.indexOf("?", start);
            return end == -1 ? input.substring(start) : input.substring(start, end);
        }

        return input;
    }

    /**
     * Définit le joueur cible des événements
     */
    public void setTargetPlayer(Player player) {
        this.targetPlayer = player != null ? player.getUniqueId() : null;
        if (player != null) {
            getLogger().info("Joueur cible defini: " + player.getName());
        }
    }

    /**
     * Récupère le joueur cible des événements
     */
    public Player getTargetPlayer() {
        if (targetPlayer == null) {
            return null;
        }
        return Bukkit.getPlayer(targetPlayer);
    }

    // Getters
    public static YouTubeEventPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public YouTubeAPI getYouTubeAPI() {
        return youtubeAPI;
    }

    public LiveChatPoller getLiveChatPoller() {
        return liveChatPoller;
    }

    public LiveAutoDetector getAutoDetector() {
        return autoDetector;
    }

    public LikeTracker getLikeTracker() {
        return likeTracker;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public YouTubeDisplay getDisplay() {
        return display;
    }

    public boolean isConnected() {
        return liveChatPoller != null && liveChatPoller.isRunning();
    }
}
