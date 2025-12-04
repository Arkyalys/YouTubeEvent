package fr.arkyalys.event.youtube;

import fr.arkyalys.event.YouTubeEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

/**
 * Détecte automatiquement quand la chaîne YouTube est en live
 */
public class LiveAutoDetector {

    private final YouTubeEventPlugin plugin;
    private BukkitTask detectTask;
    private boolean running = false;
    private String lastDetectedLiveId = null;

    // Gestion du quota exceeded - ne réessaye l'API qu'après 24h (reset à 9h FR)
    private boolean quotaExceeded = false;
    private long quotaExceededTime = 0;
    private boolean quotaMessageSent = false; // Pour ne logger qu'une fois
    private static final long QUOTA_BACKOFF_DURATION = 24 * 60 * 60 * 1000; // 24 heures

    public LiveAutoDetector(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre la détection automatique
     */
    public void start() {
        String channelId = plugin.getConfigManager().getChannelId();
        int interval = plugin.getConfigManager().getAutoDetectInterval();

        if (channelId == null || channelId.isEmpty()) {
            plugin.getLogger().warning("Channel ID non configure. Detection auto desactivee.");
            return;
        }

        if (interval <= 0) {
            plugin.getLogger().info("Detection automatique desactivee (interval = 0)");
            return;
        }

        if (running) {
            return;
        }

        running = true;
        int intervalTicks = interval * 20;

        plugin.getLogger().info("Detection automatique activee (toutes les " + interval + "s)");

        detectTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running) return;

            checkForLive(channelId);
        }, 20L, intervalTicks); // Première vérification après 1 seconde
    }

    /**
     * Arrête la détection automatique
     */
    public void stop() {
        running = false;
        if (detectTask != null) {
            detectTask.cancel();
            detectTask = null;
        }
    }

    /**
     * Vérifie si la chaîne est en live
     */
    private void checkForLive(String channelId) {
        // Si quota exceeded, vérifier si on peut réactiver l'API (après 24h)
        if (quotaExceeded) {
            long elapsed = System.currentTimeMillis() - quotaExceededTime;
            if (elapsed >= QUOTA_BACKOFF_DURATION) {
                // Backoff terminé (24h passées), réactiver l'API
                quotaExceeded = false;
                quotaMessageSent = false;
                plugin.getYouTubeAPI().setFallbackToDataAPI(true);
                plugin.getLogger().info("Reactivation de l'API YouTube (nouveau jour, quota reset)");
            }
            // Continue avec la méthode gratuite même si quota exceeded
        }

        try {
            String liveId = plugin.getYouTubeAPI().findActiveLive(channelId);

            // Vérifier si quota exceeded (retourne "QUOTA_EXCEEDED")
            if ("QUOTA_EXCEEDED".equals(liveId)) {
                if (!quotaExceeded) {
                    // Première fois qu'on détecte le quota exceeded
                    quotaExceeded = true;
                    quotaExceededTime = System.currentTimeMillis();
                    quotaMessageSent = true;
                    // Désactiver l'API pour ne plus consommer de quota
                    plugin.getYouTubeAPI().setFallbackToDataAPI(false);
                    plugin.getLogger().warning("Quota YouTube depasse! Utilisation de la methode gratuite uniquement (jusqu'a demain 9h).");
                    if (plugin.getTargetPlayer() != null) {
                        plugin.getTargetPlayer().sendMessage(
                            plugin.getConfigManager().getPrefix() +
                            "\u00A7eQuota YouTube depasse. Detection via methode gratuite."
                        );
                    }
                }
                // Ne pas return, continuer avec la méthode gratuite
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (liveId != null) {
                    // Un live est en cours
                    if (!plugin.isConnected()) {
                        // Pas encore connecté, on se connecte
                        plugin.getLogger().info("Live detecte automatiquement: " + liveId);

                        if (plugin.getTargetPlayer() != null) {
                            plugin.getTargetPlayer().sendMessage(
                                plugin.getConfigManager().getPrefix() +
                                "\u00A7aLive detecte! Connexion automatique..."
                            );
                        }

                        plugin.startLive(liveId);
                        lastDetectedLiveId = liveId;
                    } else if (!liveId.equals(lastDetectedLiveId)) {
                        // Nouveau live différent
                        plugin.getLogger().info("Nouveau live detecte: " + liveId);
                        plugin.stopLive();
                        plugin.startLive(liveId);
                        lastDetectedLiveId = liveId;
                    }
                } else {
                    // Pas de live en cours
                    if (plugin.isConnected() && lastDetectedLiveId != null) {
                        // Le live s'est terminé
                        plugin.getLogger().info("Live termine, deconnexion...");

                        if (plugin.getTargetPlayer() != null) {
                            plugin.getTargetPlayer().sendMessage(
                                plugin.getConfigManager().getPrefix() +
                                "\u00A7eLive termine. Deconnexion automatique."
                            );
                        }

                        plugin.stopLive();
                        lastDetectedLiveId = null;
                    }
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la detection du live: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }
}
