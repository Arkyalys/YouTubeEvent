package fr.arkyalys.event.youtube.provider;

import com.google.gson.*;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Provider YouTube Data API v3 - Utilise le quota API
 * À utiliser en fallback si InnerTube échoue
 *
 * ATTENTION: Consomme du quota API (10,000 unités/jour)
 */
public class DataAPIProvider implements ChatProvider {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final YouTubeEventPlugin plugin;
    private final OkHttpClient httpClient;

    private String liveChatId;
    private String pageToken;
    private String videoId;
    private boolean connected = false;
    private int pollingInterval = 3000;

    private final Set<String> processedMessageIds = new HashSet<>();

    public DataAPIProvider(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "YouTube Data API v3 (Quota)";
    }

    @Override
    public boolean connect(String videoId) {
        this.videoId = videoId;
        this.processedMessageIds.clear();

        plugin.getLogger().info("[DataAPI] Connexion au live: " + videoId);

        // Récupérer le liveChatId
        String chatId = fetchLiveChatId(videoId);
        if (chatId == null) {
            plugin.getLogger().warning("[DataAPI] Impossible de recuperer le liveChatId");
            return false;
        }

        this.liveChatId = chatId;
        this.connected = true;
        plugin.getLogger().info("[DataAPI] Connecte avec succes!");
        return true;
    }

    @Override
    public void disconnect() {
        this.connected = false;
        this.liveChatId = null;
        this.pageToken = null;
        this.videoId = null;
        this.processedMessageIds.clear();
        plugin.getLogger().info("[DataAPI] Deconnecte");
    }

    @Override
    public List<ChatMessage> pollMessages() {
        List<ChatMessage> messages = new ArrayList<>();

        if (!connected || liveChatId == null) {
            return messages;
        }

        String apiKey = plugin.getConfigManager().getApiKey();
        int maxResults = plugin.getConfigManager().getMaxMessagesPerPoll();

        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("/liveChat/messages?part=snippet,authorDetails");
        urlBuilder.append("&liveChatId=").append(liveChatId);
        urlBuilder.append("&maxResults=").append(maxResults);
        urlBuilder.append("&key=").append(apiKey);

        if (pageToken != null && !pageToken.isEmpty()) {
            urlBuilder.append("&pageToken=").append(pageToken);
        }

        try {
            Request request = new Request.Builder()
                    .url(urlBuilder.toString())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("[DataAPI] Erreur: " + response.code());
                    return messages;
                }

                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                // Mettre à jour le pageToken
                if (json.has("nextPageToken")) {
                    this.pageToken = json.get("nextPageToken").getAsString();
                }

                // Mettre à jour l'intervalle de polling
                if (json.has("pollingIntervalMillis")) {
                    this.pollingInterval = json.get("pollingIntervalMillis").getAsInt();
                }

                // Parser les messages
                JsonArray items = json.getAsJsonArray("items");
                if (items != null) {
                    for (JsonElement item : items) {
                        ChatMessage message = parseMessage(item.getAsJsonObject());
                        if (message != null && !processedMessageIds.contains(message.getMessageId())) {
                            processedMessageIds.add(message.getMessageId());
                            messages.add(message);
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[DataAPI] Erreur de connexion: " + e.getMessage());
        }

        return messages;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public int getPollingInterval() {
        return pollingInterval;
    }

    @Override
    public boolean usesQuota() {
        return true; // Data API utilise du quota
    }

    /**
     * Récupère le liveChatId depuis l'API
     */
    private String fetchLiveChatId(String videoId) {
        String apiKey = plugin.getConfigManager().getApiKey();
        String url = BASE_URL + "/videos?part=liveStreamingDetails&id=" + videoId + "&key=" + apiKey;

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
                if (items == null || items.size() == 0) {
                    return null;
                }

                JsonObject video = items.get(0).getAsJsonObject();
                JsonObject liveDetails = video.getAsJsonObject("liveStreamingDetails");

                if (liveDetails == null || !liveDetails.has("activeLiveChatId")) {
                    return null;
                }

                return liveDetails.get("activeLiveChatId").getAsString();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parse un message de l'API
     */
    private ChatMessage parseMessage(JsonObject item) {
        try {
            String messageId = item.get("id").getAsString();
            JsonObject snippet = item.getAsJsonObject("snippet");
            JsonObject authorDetails = item.getAsJsonObject("authorDetails");

            String type = snippet.get("type").getAsString();
            String authorChannelId = authorDetails.get("channelId").getAsString();
            String authorName = authorDetails.get("displayName").getAsString();
            String profileImage = authorDetails.has("profileImageUrl")
                    ? authorDetails.get("profileImageUrl").getAsString() : "";
            long publishedAt = parseTimestamp(snippet.get("publishedAt").getAsString());

            boolean isModerator = getBooleanOrFalse(authorDetails, "isChatModerator");
            boolean isOwner = getBooleanOrFalse(authorDetails, "isChatOwner");
            boolean isSponsor = getBooleanOrFalse(authorDetails, "isChatSponsor");

            switch (type) {
                case "textMessageEvent":
                    JsonObject textDetails = snippet.getAsJsonObject("textMessageDetails");
                    String message = textDetails.get("messageText").getAsString();
                    return ChatMessage.normalMessage(messageId, authorChannelId, authorName,
                            profileImage, message, publishedAt, isModerator, isOwner, isSponsor);

                case "superChatEvent":
                    JsonObject superChatDetails = snippet.getAsJsonObject("superChatDetails");
                    String scMessage = getStringOrNull(superChatDetails, "userComment");
                    String amountDisplay = superChatDetails.get("amountDisplayString").getAsString();
                    long amountMicros = superChatDetails.get("amountMicros").getAsLong();
                    String currency = superChatDetails.get("currency").getAsString();
                    return ChatMessage.superChat(messageId, authorChannelId, authorName,
                            profileImage, scMessage != null ? scMessage : "", publishedAt,
                            amountDisplay, amountMicros, currency, isModerator, isOwner, isSponsor);

                case "superStickerEvent":
                    JsonObject stickerDetails = snippet.getAsJsonObject("superStickerDetails");
                    String stickerAmount = stickerDetails.get("amountDisplayString").getAsString();
                    long stickerMicros = stickerDetails.get("amountMicros").getAsLong();
                    String stickerCurrency = stickerDetails.get("currency").getAsString();
                    return ChatMessage.superSticker(messageId, authorChannelId, authorName,
                            profileImage, publishedAt, stickerAmount, stickerMicros, stickerCurrency,
                            isModerator, isOwner, isSponsor);

                case "newSponsorEvent":
                case "memberMilestoneChatEvent":
                    return ChatMessage.newMember(messageId, authorChannelId, authorName,
                            profileImage, publishedAt, isModerator, isOwner, isSponsor);

                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean getBooleanOrFalse(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsBoolean();
        }
        return false;
    }

    private String getStringOrNull(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
