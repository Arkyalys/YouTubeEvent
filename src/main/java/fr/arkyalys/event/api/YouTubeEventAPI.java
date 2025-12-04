package fr.arkyalys.event.api;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.LikeTracker;
import fr.arkyalys.event.youtube.LiveChatPoller;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * API publique pour le plugin YouTubeEvent
 *
 * Cette API permet aux autres plugins de:
 * - Vérifier l'état de la connexion au live YouTube
 * - Récupérer les statistiques du live (likes, vues, viewers)
 * - Accéder au joueur cible des événements
 * - Écouter les événements YouTube via Bukkit Events
 *
 * Usage:
 * <pre>
 * YouTubeEventAPI api = YouTubeEventAPI.getInstance();
 * if (api != null && api.isConnected()) {
 *     long likes = api.getLikeCount();
 *     long views = api.getViewCount();
 *     Player target = api.getTargetPlayer();
 * }
 * </pre>
 *
 * Pour écouter les événements YouTube:
 * <pre>
 * public class MyListener implements Listener {
 *     @EventHandler
 *     public void onSuperChat(YouTubeSuperChatEvent event) {
 *         double amount = event.getAmountValue();
 *         String viewer = event.getAuthorName();
 *         // Réagir au super chat...
 *     }
 * }
 * </pre>
 */
public class YouTubeEventAPI {

    private static YouTubeEventAPI instance;
    private final YouTubeEventPlugin plugin;

    /**
     * Constructeur privé - utilisez getInstance()
     */
    private YouTubeEventAPI(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialise l'API (appelé par le plugin principal)
     */
    public static void init(YouTubeEventPlugin plugin) {
        instance = new YouTubeEventAPI(plugin);
    }

    /**
     * Récupère l'instance de l'API
     * @return L'instance de l'API ou null si le plugin n'est pas chargé
     */
    public static YouTubeEventAPI getInstance() {
        if (instance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("YouTubeEvent");
            if (plugin instanceof YouTubeEventPlugin) {
                init((YouTubeEventPlugin) plugin);
            }
        }
        return instance;
    }

    /**
     * Vérifie si le plugin YouTubeEvent est disponible
     */
    public static boolean isAvailable() {
        return getInstance() != null;
    }

    // ==================== CONNEXION ====================

    /**
     * Vérifie si on est connecté à un live YouTube
     */
    public boolean isConnected() {
        return plugin.isConnected();
    }

    /**
     * Récupère l'ID du live actuel
     * @return L'ID de la vidéo ou null si non connecté
     */
    public String getCurrentLiveId() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null ? poller.getCurrentLiveId() : null;
    }

    /**
     * Récupère le nom du provider utilisé
     * @return "InnerTube" (gratuit) ou "Data API v3" (quota)
     */
    public String getProviderName() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null ? poller.getProviderName() : "Non connecté";
    }

    /**
     * Vérifie si le mode sans quota est actif (InnerTube)
     */
    public boolean isQuotaFree() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null && poller.isQuotaFree();
    }

    // ==================== STATISTIQUES ====================

    /**
     * Récupère le nombre total de likes sur le live
     * @return Le nombre de likes ou -1 si non disponible
     */
    public long getLikeCount() {
        LikeTracker tracker = plugin.getLikeTracker();
        return tracker != null ? tracker.getLastLikeCount() : -1;
    }

    /**
     * Récupère le nombre total de vues sur le live
     * @return Le nombre de vues ou -1 si non disponible
     */
    public long getViewCount() {
        LikeTracker tracker = plugin.getLikeTracker();
        return tracker != null ? tracker.getLastViewCount() : -1;
    }

    /**
     * Récupère le nombre de nouveaux likes depuis la connexion
     */
    public long getNewLikesCount() {
        LikeTracker tracker = plugin.getLikeTracker();
        return tracker != null ? tracker.getTotalNewLikes() : 0;
    }

    /**
     * Récupère le nombre de messages reçus depuis la connexion
     */
    public int getTotalMessagesReceived() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null ? poller.getTotalMessagesReceived() : 0;
    }

    /**
     * Récupère le temps de connexion en millisecondes
     */
    public long getUptime() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null ? poller.getUptime() : 0;
    }

    /**
     * Récupère le temps de connexion formaté (ex: "1h 30m 45s")
     */
    public String getUptimeFormatted() {
        LiveChatPoller poller = plugin.getLiveChatPoller();
        return poller != null ? poller.getUptimeFormatted() : "0s";
    }

    // ==================== JOUEUR CIBLE ====================

    /**
     * Récupère le joueur cible des événements (le streamer)
     * @return Le joueur cible ou null si non défini/hors ligne
     */
    public Player getTargetPlayer() {
        return plugin.getTargetPlayer();
    }

    /**
     * Vérifie si un joueur cible est défini et en ligne
     */
    public boolean hasTargetPlayer() {
        return plugin.getTargetPlayer() != null;
    }

    // ==================== CONTRÔLE ====================

    /**
     * Démarre la connexion à un live YouTube
     * @param liveId L'ID de la vidéo ou l'URL complète
     * @return true si la connexion a été initiée
     */
    public boolean startLive(String liveId) {
        return plugin.startLive(liveId);
    }

    /**
     * Arrête la connexion au live YouTube
     */
    public void stopLive() {
        plugin.stopLive();
    }

    /**
     * Définit le joueur cible des événements
     * @param player Le joueur (streamer) ou null pour retirer
     */
    public void setTargetPlayer(Player player) {
        plugin.setTargetPlayer(player);
    }

    // ==================== UTILITAIRES ====================

    /**
     * Récupère l'instance du plugin principal
     * Utile pour les opérations avancées
     */
    public YouTubeEventPlugin getPlugin() {
        return plugin;
    }

    /**
     * Récupère la version du plugin
     */
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
