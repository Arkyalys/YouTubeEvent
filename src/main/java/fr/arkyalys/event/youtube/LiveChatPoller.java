package fr.arkyalys.event.youtube;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Gère le polling du chat YouTube Live
 * Utilise automatiquement InnerTube (0 quota) ou Data API (fallback)
 */
public class LiveChatPoller {

    private final YouTubeEventPlugin plugin;
    private final YouTubeAPI api;

    private BukkitTask pollTask;
    private String currentLiveId;
    private boolean running = false;

    // Stats
    private int totalMessagesReceived = 0;
    private long connectionTime = 0;

    public LiveChatPoller(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getYouTubeAPI();
    }

    /**
     * Démarre le polling pour un live YouTube
     * @param liveId L'ID de la vidéo live
     * @return true si démarré avec succès
     */
    public boolean start(String liveId) {
        if (running) {
            plugin.getLogger().warning("Deja connecte a un live. Utilisez /youtube stop d'abord.");
            return false;
        }

        this.currentLiveId = liveId;
        plugin.getLogger().info("Connexion au live YouTube: " + liveId);

        // Connecter au chat de manière asynchrone
        CompletableFuture.supplyAsync(() -> api.connectToLiveChat(liveId))
                .thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!success) {
                            plugin.getLogger().warning("Impossible de se connecter au chat live.");
                            return;
                        }

                        this.running = true;
                        this.connectionTime = System.currentTimeMillis();
                        this.totalMessagesReceived = 0;

                        // Afficher le provider utilisé
                        String providerName = api.getActiveProvider() != null
                                ? api.getActiveProvider().getName()
                                : "Inconnu";
                        plugin.getLogger().info("Connecte via: " + providerName);

                        if (!api.isUsingQuota()) {
                            plugin.getLogger().info("Mode sans quota actif (InnerTube)");
                        } else {
                            plugin.getLogger().warning("Mode quota actif (Data API) - Attention a la limite!");
                        }

                        // Démarrer le polling
                        startPolling();

                        // Notifier le joueur cible
                        if (plugin.getTargetPlayer() != null) {
                            plugin.getTargetPlayer().sendMessage(
                                    plugin.getConfigManager().getPrefix() +
                                    plugin.getConfigManager().getMessageConnected()
                            );
                        }
                    });
                });

        return true;
    }

    /**
     * Arrête le polling
     */
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }

        // Déconnecter l'API
        api.disconnect();

        running = false;

        plugin.getLogger().info("Deconnecte du live YouTube.");

        // Notifier le joueur cible
        if (plugin.getTargetPlayer() != null) {
            plugin.getTargetPlayer().sendMessage(
                    plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessageDisconnected()
            );
        }
    }

    /**
     * Démarre la boucle de polling
     */
    private void startPolling() {
        // L'intervalle est déterminé par le provider
        final int intervalTicks = Math.max(api.getPollingInterval() / 50, 20); // Minimum 1 seconde

        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running || !api.isConnected()) {
                return;
            }

            try {
                // Récupérer les nouveaux messages via le provider actif
                List<ChatMessage> messages = api.pollMessages();

                if (!messages.isEmpty()) {
                    totalMessagesReceived += messages.size();

                    // Traiter les messages sur le thread principal
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (ChatMessage message : messages) {
                            processMessage(message);
                        }
                    });
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Erreur pendant le polling: " + e.getMessage());
            }
        }, 20L, intervalTicks);
    }

    /**
     * Traite un message reçu
     */
    private void processMessage(ChatMessage message) {
        // Log le message
        plugin.getLogger().info("YouTube: " + message.toString());

        // Déclencher les événements via l'EventManager
        plugin.getEventManager().handleMessage(message);
    }

    // Getters
    public boolean isRunning() {
        return running;
    }

    public String getCurrentLiveId() {
        return currentLiveId;
    }

    public int getTotalMessagesReceived() {
        return totalMessagesReceived;
    }

    public long getConnectionTime() {
        return connectionTime;
    }

    public long getUptime() {
        if (!running || connectionTime == 0) return 0;
        return System.currentTimeMillis() - connectionTime;
    }

    public String getUptimeFormatted() {
        long uptime = getUptime();
        if (uptime == 0) return "0s";

        long seconds = (uptime / 1000) % 60;
        long minutes = (uptime / (1000 * 60)) % 60;
        long hours = (uptime / (1000 * 60 * 60));

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Indique si le mode sans quota est actif
     */
    public boolean isQuotaFree() {
        return api.getActiveProvider() != null && !api.isUsingQuota();
    }

    /**
     * Récupère le nom du provider actif
     */
    public String getProviderName() {
        return api.getActiveProvider() != null
                ? api.getActiveProvider().getName()
                : "Non connecté";
    }
}
