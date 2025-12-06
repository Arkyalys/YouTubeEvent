package fr.arkyalys.event.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Traque les likes sur le live YouTube et déclenche des événements
 */
public class LikeTracker {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final YouTubeEventPlugin plugin;
    private final OkHttpClient httpClient;

    private BukkitTask trackTask;
    private String currentVideoId;
    private long lastLikeCount = -1;
    private long lastViewCount = -1;
    private boolean running = false;

    // Anti-abus : on garde le MAX de likes jamais atteint
    // comme ça si quelqu'un like/unlike, on ne recompense pas
    private long maxLikeCountEver = -1;
    private long maxViewCountEver = -1;

    // Stats
    private long totalNewLikes = 0;
    private long totalNewViews = 0;

    public LikeTracker(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Démarre le tracking pour une vidéo
     */
    public void start(String videoId) {
        if (running) {
            stop();
        }

        this.currentVideoId = videoId;
        this.lastLikeCount = -1;
        this.lastViewCount = -1;
        this.maxLikeCountEver = -1;
        this.maxViewCountEver = -1;
        this.totalNewLikes = 0;
        this.totalNewViews = 0;
        this.running = true;

        int intervalTicks = plugin.getConfigManager().getLikeCheckInterval() * 20;

        plugin.getLogger().info("Demarrage du tracking des likes/vues pour: " + videoId);

        trackTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running || currentVideoId == null) return;

            try {
                checkStats();
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur tracking likes: " + e.getMessage());
            }
        }, 40L, intervalTicks); // Premier check après 2 secondes
    }

    /**
     * Arrête le tracking
     */
    public void stop() {
        running = false;
        if (trackTask != null) {
            trackTask.cancel();
            trackTask = null;
        }
        currentVideoId = null;
    }

    /**
     * Vérifie les statistiques de la vidéo
     * Utilise l'API YouTube en priorité (plus précis), sinon scraping
     */
    private void checkStats() {
        // Essayer d'abord l'API (plus précis pour les vues en temps réel)
        long[] stats = getStatsAPI();

        if (stats == null) {
            // Fallback sur le scraping si l'API échoue
            stats = getStatsFree();
        }

        if (stats == null) return;

        long likeCount = stats[0];
        long viewCount = stats[1];

        // Premier check - initialiser les compteurs
        if (maxLikeCountEver == -1) {
            maxLikeCountEver = likeCount;
            maxViewCountEver = viewCount;
            lastLikeCount = likeCount;
            lastViewCount = viewCount;
            plugin.getLogger().info("Stats initiales - Likes: " + likeCount + ", Vues: " + viewCount);
            return;
        }

        // ANTI-ABUS : On ne recompense que si on DEPASSE le maximum jamais atteint
        // Comme ca, like/unlike ne donne rien
        if (likeCount > maxLikeCountEver) {
            long newLikes = likeCount - maxLikeCountEver;
            totalNewLikes += newLikes;
            maxLikeCountEver = likeCount; // Mettre a jour le max

            Bukkit.getScheduler().runTask(plugin, () -> {
                handleNewLikes(newLikes, likeCount);
            });
        }

        // Pareil pour les vues - seulement si on depasse le max
        if (viewCount > maxViewCountEver) {
            long newViews = viewCount - maxViewCountEver;
            totalNewViews += newViews;

            // Déclencher un événement tous les X vues
            int viewMilestone = plugin.getConfigManager().getViewMilestone();
            if (viewMilestone > 0 && viewCount / viewMilestone > maxViewCountEver / viewMilestone) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handleViewMilestone(viewCount);
                });
            }

            maxViewCountEver = viewCount; // Mettre a jour le max
        }

        lastLikeCount = likeCount;
        lastViewCount = viewCount;
    }

    /**
     * Méthode GRATUITE - Scrape les stats depuis la page YouTube
     * @return [likeCount, viewCount] ou null si échec
     */
    private long[] getStatsFree() {
        String url = "https://www.youtube.com/watch?v=" + currentVideoId;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Cookie", "CONSENT=YES+cb.20210420-15-p1.en-GB+FX+634; GPS=1")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String html = response.body().string();
                long likeCount = -1;
                long viewCount = -1;

                // Méthode 1: Chercher dans ytInitialData
                // Les likes sont souvent dans: "accessibilityText":"X likes"
                // ou dans toggleButtonRenderer avec likeCount

                // Chercher le nombre de likes avec regex
                // Pattern: "likeCount":{"simpleText":"X"} ou "likeCount":"X"
                int likeStart = html.indexOf("\"likeCount\"");
                if (likeStart != -1) {
                    // Chercher le nombre après
                    int numStart = html.indexOf("\"", likeStart + 12);
                    if (numStart != -1) {
                        numStart++; // Passer le "
                        int numEnd = html.indexOf("\"", numStart);
                        if (numEnd != -1) {
                            String likeStr = html.substring(numStart, numEnd);
                            // Peut être un nombre ou un texte comme "1,234"
                            likeStr = likeStr.replaceAll("[^0-9]", "");
                            if (!likeStr.isEmpty()) {
                                try {
                                    likeCount = Long.parseLong(likeStr);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }

                // Alternative: chercher "likes" avec un nombre devant
                if (likeCount == -1) {
                    // Pattern: "X likes" dans accessibilityText
                    String searchPattern = " likes\"";
                    int likesIdx = html.indexOf(searchPattern);
                    if (likesIdx != -1) {
                        // Chercher le nombre avant
                        int startSearch = Math.max(0, likesIdx - 30);
                        String before = html.substring(startSearch, likesIdx);
                        // Extraire les chiffres
                        String nums = before.replaceAll("[^0-9,]", "").replace(",", "");
                        // Prendre les derniers chiffres consécutifs
                        StringBuilder numBuilder = new StringBuilder();
                        for (int i = nums.length() - 1; i >= 0; i--) {
                            char c = nums.charAt(i);
                            if (Character.isDigit(c)) {
                                numBuilder.insert(0, c);
                            } else if (numBuilder.length() > 0) {
                                break;
                            }
                        }
                        if (numBuilder.length() > 0) {
                            try {
                                likeCount = Long.parseLong(numBuilder.toString());
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                // Chercher le nombre de vues
                // Pattern: "viewCount":"123456"
                int viewStart = html.indexOf("\"viewCount\":\"");
                if (viewStart != -1) {
                    viewStart += 13;
                    int viewEnd = html.indexOf("\"", viewStart);
                    if (viewEnd != -1) {
                        String viewStr = html.substring(viewStart, viewEnd);
                        try {
                            viewCount = Long.parseLong(viewStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // Si on a trouvé au moins les vues, c'est un succès
                if (viewCount >= 0) {
                    // Si pas de likes trouvés, mettre 0
                    if (likeCount < 0) likeCount = 0;
                    return new long[]{likeCount, viewCount};
                }

                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Méthode API - Utilise l'API YouTube (coûte 1 unité)
     * @return [likeCount, viewCount] ou null si échec
     */
    private long[] getStatsAPI() {
        String apiKey = plugin.getConfigManager().getApiKey();
        String url = BASE_URL + "/videos?part=statistics&id=" + currentVideoId + "&key=" + apiKey;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                JsonArray items = json.getAsJsonArray("items");
                if (items == null || items.size() == 0) return null;

                JsonObject stats = items.get(0).getAsJsonObject()
                        .getAsJsonObject("statistics");

                long likeCount = stats.has("likeCount")
                        ? stats.get("likeCount").getAsLong() : 0;
                long viewCount = stats.has("viewCount")
                        ? stats.get("viewCount").getAsLong() : 0;

                return new long[]{likeCount, viewCount};
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Gère les nouveaux likes
     */
    private void handleNewLikes(long newLikes, long totalLikes) {
        plugin.getLogger().info("+" + newLikes + " like(s)! Total: " + totalLikes);

        // Créer un faux message pour le système d'événements
        ChatMessage likeEvent = ChatMessage.normalMessage(
                "like-" + System.currentTimeMillis(),
                "youtube-like",
                "Viewer",
                "",
                "+" + newLikes + " like(s)",
                System.currentTimeMillis(),
                false, false, false // isModerator, isOwner, isSponsor
        );

        // Passer au gestionnaire d'événements avec un type spécial
        plugin.getEventManager().handleLike(newLikes, totalLikes);
    }

    /**
     * Gère les paliers de vues
     */
    private void handleViewMilestone(long viewCount) {
        plugin.getLogger().info("Nouveau palier de vues atteint: " + viewCount);
        plugin.getEventManager().handleViewMilestone(viewCount);
    }

    // Getters
    public boolean isRunning() {
        return running;
    }

    public long getLastLikeCount() {
        return lastLikeCount;
    }

    public long getLastViewCount() {
        return lastViewCount;
    }

    public long getTotalNewLikes() {
        return totalNewLikes;
    }

    public long getTotalNewViews() {
        return totalNewViews;
    }
}
