package fr.arkyalys.event.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import fr.arkyalys.event.youtube.provider.ChatProvider;
import fr.arkyalys.event.youtube.provider.DataAPIProvider;
import fr.arkyalys.event.youtube.provider.InnerTubeProvider;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gère les appels à YouTube (InnerTube + Data API v3)
 *
 * Architecture:
 * - InnerTube (par défaut): 0 quota, simule un navigateur
 * - Data API v3 (fallback): Utilise le quota si InnerTube échoue
 */
public class YouTubeAPI {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final YouTubeEventPlugin plugin;
    private final OkHttpClient httpClient;

    // Providers
    private ChatProvider activeProvider;
    private final InnerTubeProvider innerTubeProvider;
    private final DataAPIProvider dataAPIProvider;

    // Configuration
    private boolean preferInnerTube = true;
    private boolean fallbackToDataAPI = true;

    // Anti-spam pour les erreurs de quota
    private boolean quotaErrorLogged = false;

    public YouTubeAPI(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        // Initialiser les providers
        this.innerTubeProvider = new InnerTubeProvider(plugin);
        this.dataAPIProvider = new DataAPIProvider(plugin);
    }

    /**
     * Configure les préférences de provider
     */
    public void setPreferInnerTube(boolean prefer) {
        this.preferInnerTube = prefer;
    }

    public void setFallbackToDataAPI(boolean fallback) {
        this.fallbackToDataAPI = fallback;
    }

    /**
     * Récupère le provider actif
     */
    public ChatProvider getActiveProvider() {
        return activeProvider;
    }

    /**
     * Connecte au chat d'un live
     * @param videoId ID de la vidéo live
     * @return true si connexion réussie
     */
    public boolean connectToLiveChat(String videoId) {
        // Essayer InnerTube en premier si préféré
        if (preferInnerTube) {
            plugin.getLogger().info("Tentative de connexion via InnerTube (sans quota)...");
            if (innerTubeProvider.connect(videoId)) {
                this.activeProvider = innerTubeProvider;
                plugin.getLogger().info("Connecte via " + activeProvider.getName());
                return true;
            }
            plugin.getLogger().warning("InnerTube a echoue, tentative avec Data API...");
        }

        // Fallback sur Data API
        if (fallbackToDataAPI || !preferInnerTube) {
            plugin.getLogger().info("Tentative de connexion via Data API v3...");
            if (dataAPIProvider.connect(videoId)) {
                this.activeProvider = dataAPIProvider;
                plugin.getLogger().info("Connecte via " + activeProvider.getName());
                return true;
            }
        }

        plugin.getLogger().severe("Impossible de se connecter au chat live!");
        return false;
    }

    /**
     * Déconnecte du chat
     */
    public void disconnect() {
        if (activeProvider != null) {
            activeProvider.disconnect();
            activeProvider = null;
        }
    }

    /**
     * Récupère les nouveaux messages du chat
     */
    public List<ChatMessage> pollMessages() {
        if (activeProvider == null || !activeProvider.isConnected()) {
            return new ArrayList<>();
        }
        return activeProvider.pollMessages();
    }

    /**
     * Vérifie si connecté au chat
     */
    public boolean isConnected() {
        return activeProvider != null && activeProvider.isConnected();
    }

    /**
     * Récupère l'intervalle de polling recommandé
     */
    public int getPollingInterval() {
        if (activeProvider != null) {
            return activeProvider.getPollingInterval();
        }
        return plugin.getConfigManager().getPollInterval() * 1000;
    }

    /**
     * Indique si le provider actuel utilise du quota
     */
    public boolean isUsingQuota() {
        return activeProvider != null && activeProvider.usesQuota();
    }

    // ============================================================
    //                    DÉTECTION DE LIVE
    // ============================================================

    /**
     * Cherche le live actif d'une chaîne YouTube
     * Utilise d'abord la méthode gratuite (scraping), puis l'API en fallback
     */
    public String findActiveLive(String channelId) {
        // 1. Essayer avec le @username si configuré (méthode la plus fiable)
        String username = plugin.getConfigManager().getChannelUsername();
        if (username != null && !username.isEmpty()) {
            String liveId = findActiveLiveByUsername(username);
            if (liveId != null) {
                return liveId;
            }
        }

        // 2. Essayer avec le channel ID (méthode /live)
        String liveId = findActiveLiveFree(channelId);
        if (liveId != null) {
            return liveId;
        }

        // 3. Fallback sur l'API si activée
        if (fallbackToDataAPI) {
            return findActiveLiveAPI(channelId);
        }

        return null;
    }

    /**
     * Méthode GRATUITE avec @username - Comme le bot Discord
     * Vérifie si "text":"LIVE" est présent sur la page de la chaîne
     */
    private String findActiveLiveByUsername(String username) {
        String url = "https://www.youtube.com/@" + username;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Cookie", "CONSENT=YES+cb.20210420-15-p1.en-GB+FX+634")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String html = response.body().string();

                // Méthode du bot Discord: chercher "text":"LIVE"
                boolean isLive = html.contains("\"text\":\"LIVE\"");
                if (!isLive) {
                    return null;
                }

                // Extraire le videoId
                // Méthode 1: Chercher dans le JSON
                int videoIdStart = html.indexOf("\"videoId\":\"");
                if (videoIdStart != -1) {
                    int idStart = videoIdStart + 11;
                    int idEnd = html.indexOf("\"", idStart);
                    if (idEnd != -1 && idEnd - idStart == 11) {
                        String videoId = html.substring(idStart, idEnd);
                        plugin.getLogger().info("Live detecte via @" + username + ": " + videoId);
                        return videoId;
                    }
                }

                // Méthode 2: Chercher le canonical URL
                int canonicalStart = html.indexOf("<link rel=\"canonical\"");
                if (canonicalStart != -1) {
                    int hrefStart = html.indexOf("href=\"", canonicalStart);
                    if (hrefStart != -1) {
                        int hrefEnd = html.indexOf("\"", hrefStart + 6);
                        if (hrefEnd != -1) {
                            String canonicalUrl = html.substring(hrefStart + 6, hrefEnd);
                            if (canonicalUrl.contains("watch?v=")) {
                                String videoId = canonicalUrl.substring(canonicalUrl.indexOf("watch?v=") + 8);
                                if (videoId.contains("&")) {
                                    videoId = videoId.substring(0, videoId.indexOf("&"));
                                }
                                plugin.getLogger().info("Live detecte via @" + username + " (canonical): " + videoId);
                                return videoId;
                            }
                        }
                    }
                }

                // On sait qu'il est live mais on n'a pas trouvé le videoId
                plugin.getLogger().warning("Live detecte sur @" + username + " mais videoId introuvable");
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Méthode GRATUITE - Scrape la page YouTube pour détecter un live
     */
    private String findActiveLiveFree(String channelId) {
        String url = "https://www.youtube.com/channel/" + channelId + "/live";

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cookie", "CONSENT=YES+cb.20210420-15-p1.en-GB+FX+634")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String html = response.body().string();

                // Méthode 1: Chercher le canonical URL
                int canonicalStart = html.indexOf("<link rel=\"canonical\"");
                if (canonicalStart != -1) {
                    int hrefStart = html.indexOf("href=\"", canonicalStart);
                    if (hrefStart != -1) {
                        int hrefEnd = html.indexOf("\"", hrefStart + 6);
                        if (hrefEnd != -1) {
                            String canonicalUrl = html.substring(hrefStart + 6, hrefEnd);
                            if (canonicalUrl.contains("watch?v=")) {
                                String videoId = canonicalUrl.substring(canonicalUrl.indexOf("watch?v=") + 8);
                                if (videoId.contains("&")) {
                                    videoId = videoId.substring(0, videoId.indexOf("&"));
                                }
                                plugin.getLogger().info("Live detecte (methode gratuite): " + videoId);
                                return videoId;
                            }
                        }
                    }
                }

                // Méthode 2: Chercher hqdefault_live.jpg
                if (html.contains("hqdefault_live.jpg")) {
                    int videoIdStart = html.indexOf("\"videoId\":\"");
                    if (videoIdStart != -1) {
                        int idStart = videoIdStart + 11;
                        int idEnd = html.indexOf("\"", idStart);
                        if (idEnd != -1 && idEnd - idStart == 11) {
                            String videoId = html.substring(idStart, idEnd);
                            plugin.getLogger().info("Live detecte (methode gratuite v2): " + videoId);
                            return videoId;
                        }
                    }
                }

                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Méthode API - Utilise l'API YouTube (coûte 100 unités de quota)
     */
    private String findActiveLiveAPI(String channelId) {
        String apiKey = plugin.getConfigManager().getApiKey();

        String url = BASE_URL + "/search?part=snippet&channelId=" + channelId +
                "&type=video&eventType=live&key=" + apiKey;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body().string();

                if (!response.isSuccessful()) {
                    handleApiError(body);
                    return null;
                }

                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonArray items = json.getAsJsonArray("items");

                if (items == null || items.size() == 0) {
                    return null;
                }

                JsonObject firstItem = items.get(0).getAsJsonObject();
                JsonObject id = firstItem.getAsJsonObject("id");
                return id.get("videoId").getAsString();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur de connexion a l'API YouTube: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gère les erreurs API (anti-spam pour quota)
     */
    private void handleApiError(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject error = json.getAsJsonObject("error");
            if (error != null) {
                if (error.has("errors")) {
                    for (JsonElement e : error.getAsJsonArray("errors")) {
                        JsonObject err = e.getAsJsonObject();
                        String reason = err.get("reason").getAsString();
                        if ("quotaExceeded".equals(reason) || "dailyLimitExceeded".equals(reason)) {
                            // Ne logger qu'une seule fois
                            if (!quotaErrorLogged) {
                                quotaErrorLogged = true;
                                plugin.getLogger().warning("Quota API YouTube epuise! Le plugin utilisera InnerTube uniquement.");
                                plugin.getLogger().warning("Le quota se reset a 9h (heure FR). Desactivez auto-detect si besoin.");
                                // Desactiver le fallback pour eviter de re-essayer l'API
                                fallbackToDataAPI = false;
                            }
                            return;
                        }
                    }
                }
                // Autres erreurs (non quota) - logger normalement
                String message = error.get("message").getAsString();
                plugin.getLogger().warning("Erreur API: " + message);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Reset le flag d'erreur de quota (appelé quand le quota est reset)
     */
    public void resetQuotaError() {
        quotaErrorLogged = false;
    }

    // ============================================================
    //                    STATS (LIKES/VUES)
    // ============================================================

    /**
     * Récupère les stats d'une vidéo (likes, vues) - GRATUIT
     */
    public long[] getVideoStats(String videoId) {
        return getStatsFree(videoId);
    }

    /**
     * Méthode gratuite pour récupérer likes et vues
     */
    private long[] getStatsFree(String videoId) {
        String url = "https://www.youtube.com/watch?v=" + videoId;

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cookie", "CONSENT=YES+cb.20210420-15-p1.en-GB+FX+634")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return new long[]{-1, -1};
                }

                String html = response.body().string();
                long likes = -1;
                long views = -1;

                // Extraire les likes depuis ytInitialData
                String likeSearch = "\"defaultText\":{\"accessibility\":{\"accessibilityData\":{\"label\":\"";
                int likeStart = html.indexOf(likeSearch);
                if (likeStart != -1) {
                    likeStart += likeSearch.length();
                    int likeEnd = html.indexOf("\"", likeStart);
                    if (likeEnd != -1) {
                        String likeText = html.substring(likeStart, likeEnd);
                        likes = extractNumber(likeText);
                    }
                }

                // Méthode alternative pour les likes
                if (likes == -1) {
                    String altLikeSearch = "\"likeCount\":";
                    int altStart = html.indexOf(altLikeSearch);
                    if (altStart != -1) {
                        altStart += altLikeSearch.length();
                        int altEnd = html.indexOf(",", altStart);
                        if (altEnd == -1) altEnd = html.indexOf("}", altStart);
                        if (altEnd != -1) {
                            String numStr = html.substring(altStart, altEnd).replaceAll("[^0-9]", "");
                            if (!numStr.isEmpty()) {
                                likes = Long.parseLong(numStr);
                            }
                        }
                    }
                }

                // Extraire les vues
                String viewSearch = "\"viewCount\":\"";
                int viewStart = html.indexOf(viewSearch);
                if (viewStart != -1) {
                    viewStart += viewSearch.length();
                    int viewEnd = html.indexOf("\"", viewStart);
                    if (viewEnd != -1) {
                        String viewStr = html.substring(viewStart, viewEnd);
                        views = Long.parseLong(viewStr);
                    }
                }

                return new long[]{likes, views};
            }
        } catch (Exception e) {
            return new long[]{-1, -1};
        }
    }

    /**
     * Extrait un nombre d'une chaîne
     */
    private long extractNumber(String text) {
        try {
            String cleaned = text.replaceAll("[^0-9]", "");
            if (!cleaned.isEmpty()) {
                return Long.parseLong(cleaned);
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
