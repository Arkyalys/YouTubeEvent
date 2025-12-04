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
 * Provider InnerTube - Récupère les messages chat SANS utiliser de quota API
 * Utilise l'API interne de YouTube (comme un navigateur)
 *
 * Avantages:
 * - 0 quota API
 * - 0 API key nécessaire
 * - Accès aux SuperChats, membres, etc.
 */
public class InnerTubeProvider implements ChatProvider {

    private static final String INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat";
    private static final String INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"; // Clé publique InnerTube

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final YouTubeEventPlugin plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private String continuation;
    private String videoId;
    private boolean connected = false;
    private int pollingInterval = 3000; // 3 secondes par défaut

    // Pour éviter les doublons
    private final Set<String> processedMessageIds = new HashSet<>();

    public InnerTubeProvider(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    @Override
    public String getName() {
        return "InnerTube (Sans Quota)";
    }

    @Override
    public boolean connect(String videoId) {
        this.videoId = videoId;
        this.processedMessageIds.clear();

        plugin.getLogger().info("[InnerTube] Connexion au live: " + videoId);

        // Récupérer le continuation token initial depuis la page YouTube
        String initialContinuation = fetchInitialContinuation(videoId);
        if (initialContinuation == null) {
            plugin.getLogger().warning("[InnerTube] Impossible de recuperer le token initial");
            return false;
        }

        this.continuation = initialContinuation;
        this.connected = true;
        plugin.getLogger().info("[InnerTube] Connecte avec succes!");
        return true;
    }

    @Override
    public void disconnect() {
        this.connected = false;
        this.continuation = null;
        this.videoId = null;
        this.processedMessageIds.clear();
        plugin.getLogger().info("[InnerTube] Deconnecte");
    }

    @Override
    public List<ChatMessage> pollMessages() {
        List<ChatMessage> messages = new ArrayList<>();

        if (!connected || continuation == null) {
            return messages;
        }

        try {
            // Construire la requête InnerTube
            JsonObject requestBody = buildInnerTubeRequest(continuation);

            Request request = new Request.Builder()
                    .url(INNERTUBE_API_URL + "?key=" + INNERTUBE_API_KEY)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", "https://www.youtube.com")
                    .header("Referer", "https://www.youtube.com/watch?v=" + videoId)
                    .post(RequestBody.create(gson.toJson(requestBody), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("[InnerTube] Erreur: " + response.code());
                    return messages;
                }

                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                // Extraire les messages et le nouveau continuation token
                messages = parseInnerTubeResponse(json);

                // Mettre à jour le continuation token pour la prochaine requête
                String newContinuation = extractContinuation(json);
                if (newContinuation != null) {
                    this.continuation = newContinuation;
                }

                // Extraire l'intervalle de polling si disponible
                int newInterval = extractPollingInterval(json);
                if (newInterval > 0) {
                    this.pollingInterval = newInterval;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[InnerTube] Erreur de connexion: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("[InnerTube] Erreur parsing: " + e.getMessage());
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
        return false; // InnerTube n'utilise PAS de quota!
    }

    /**
     * Récupère le continuation token initial depuis la page YouTube
     */
    private String fetchInitialContinuation(String videoId) {
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
                    return null;
                }

                String html = response.body().string();
                return extractContinuationFromHtml(html);
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extrait le continuation token depuis le HTML de la page
     */
    private String extractContinuationFromHtml(String html) {
        // Chercher ytInitialData
        String searchPattern = "var ytInitialData = ";
        int dataStart = html.indexOf(searchPattern);
        if (dataStart == -1) {
            searchPattern = "window[\"ytInitialData\"] = ";
            dataStart = html.indexOf(searchPattern);
        }

        if (dataStart == -1) {
            return null;
        }

        dataStart += searchPattern.length();
        int dataEnd = html.indexOf(";</script>", dataStart);
        if (dataEnd == -1) {
            dataEnd = html.indexOf(";\n", dataStart);
        }

        if (dataEnd == -1 || dataEnd <= dataStart) {
            return null;
        }

        try {
            String jsonStr = html.substring(dataStart, dataEnd);
            JsonObject ytData = JsonParser.parseString(jsonStr).getAsJsonObject();

            // Essayer plusieurs chemins pour trouver le continuation token
            // Path 1: contents.twoColumnWatchNextResults.conversationBar.liveChatRenderer
            try {
                JsonObject contents = ytData.getAsJsonObject("contents");
                JsonObject twoColumn = contents.getAsJsonObject("twoColumnWatchNextResults");
                JsonObject conversationBar = twoColumn.getAsJsonObject("conversationBar");
                JsonObject liveChatRenderer = conversationBar.getAsJsonObject("liveChatRenderer");

                if (liveChatRenderer != null && liveChatRenderer.has("continuations")) {
                    JsonArray continuations = liveChatRenderer.getAsJsonArray("continuations");
                    if (continuations.size() > 0) {
                        JsonObject firstCont = continuations.get(0).getAsJsonObject();

                        if (firstCont.has("reloadContinuationData")) {
                            return firstCont.getAsJsonObject("reloadContinuationData")
                                    .get("continuation").getAsString();
                        }
                        if (firstCont.has("invalidationContinuationData")) {
                            return firstCont.getAsJsonObject("invalidationContinuationData")
                                    .get("continuation").getAsString();
                        }
                        if (firstCont.has("timedContinuationData")) {
                            return firstCont.getAsJsonObject("timedContinuationData")
                                    .get("continuation").getAsString();
                        }
                    }
                }
            } catch (Exception ignored) {}

            // Path 2: Recherche directe dans le JSON
            String contSearch = "\"continuation\":\"";
            int contStart = jsonStr.indexOf(contSearch);
            if (contStart != -1) {
                contStart += contSearch.length();
                int contEnd = jsonStr.indexOf("\"", contStart);
                if (contEnd != -1) {
                    String cont = jsonStr.substring(contStart, contEnd);
                    if (cont.length() > 50) {
                        return cont;
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[InnerTube] Erreur extraction continuation: " + e.getMessage());
        }

        return null;
    }

    /**
     * Construit la requête InnerTube
     */
    private JsonObject buildInnerTubeRequest(String continuation) {
        JsonObject request = new JsonObject();

        // Context InnerTube (simule un client web)
        JsonObject context = new JsonObject();
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", "2.20231219.04.00");
        client.addProperty("hl", "fr");
        client.addProperty("gl", "FR");
        client.addProperty("timeZone", "Europe/Paris");
        context.add("client", client);
        request.add("context", context);

        // Continuation token
        request.addProperty("continuation", continuation);

        return request;
    }

    /**
     * Parse la réponse InnerTube et extrait les messages
     */
    private List<ChatMessage> parseInnerTubeResponse(JsonObject response) {
        List<ChatMessage> messages = new ArrayList<>();

        try {
            // Path: continuationContents.liveChatContinuation.actions
            JsonObject continuationContents = response.getAsJsonObject("continuationContents");
            if (continuationContents == null) return messages;

            JsonObject liveChatContinuation = continuationContents.getAsJsonObject("liveChatContinuation");
            if (liveChatContinuation == null) return messages;

            JsonArray actions = liveChatContinuation.getAsJsonArray("actions");
            if (actions == null) return messages;

            for (JsonElement actionElement : actions) {
                JsonObject action = actionElement.getAsJsonObject();

                // Les messages sont dans replayChatItemAction ou addChatItemAction
                JsonObject chatItemAction = null;
                if (action.has("replayChatItemAction")) {
                    JsonObject replay = action.getAsJsonObject("replayChatItemAction");
                    if (replay.has("actions")) {
                        JsonArray replayActions = replay.getAsJsonArray("actions");
                        if (replayActions.size() > 0) {
                            chatItemAction = replayActions.get(0).getAsJsonObject()
                                    .getAsJsonObject("addChatItemAction");
                        }
                    }
                } else if (action.has("addChatItemAction")) {
                    chatItemAction = action.getAsJsonObject("addChatItemAction");
                }

                if (chatItemAction == null || !chatItemAction.has("item")) continue;

                JsonObject item = chatItemAction.getAsJsonObject("item");
                ChatMessage message = parseInnerTubeChatItem(item);

                if (message != null && !processedMessageIds.contains(message.getMessageId())) {
                    processedMessageIds.add(message.getMessageId());
                    messages.add(message);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[InnerTube] Erreur parsing messages: " + e.getMessage());
        }

        return messages;
    }

    /**
     * Parse un item de chat InnerTube en ChatMessage
     */
    private ChatMessage parseInnerTubeChatItem(JsonObject item) {
        try {
            // Message texte normal
            if (item.has("liveChatTextMessageRenderer")) {
                return parseTextMessage(item.getAsJsonObject("liveChatTextMessageRenderer"));
            }

            // Super Chat
            if (item.has("liveChatPaidMessageRenderer")) {
                return parseSuperChat(item.getAsJsonObject("liveChatPaidMessageRenderer"));
            }

            // Super Sticker
            if (item.has("liveChatPaidStickerRenderer")) {
                return parseSuperSticker(item.getAsJsonObject("liveChatPaidStickerRenderer"));
            }

            // Nouveau membre
            if (item.has("liveChatMembershipItemRenderer")) {
                return parseNewMember(item.getAsJsonObject("liveChatMembershipItemRenderer"));
            }

        } catch (Exception e) {
            // Ignorer les erreurs de parsing individuelles
        }

        return null;
    }

    /**
     * Parse un message texte normal
     */
    private ChatMessage parseTextMessage(JsonObject renderer) {
        String messageId = renderer.get("id").getAsString();
        String authorChannelId = renderer.get("authorExternalChannelId").getAsString();
        String authorName = extractText(renderer.getAsJsonObject("authorName"));
        String profileImage = extractThumbnail(renderer.getAsJsonArray("authorPhoto"));
        String message = extractMessageText(renderer.getAsJsonObject("message"));
        long timestamp = Long.parseLong(renderer.get("timestampUsec").getAsString()) / 1000;

        // Badges auteur
        boolean isModerator = hasBadge(renderer, "MODERATOR");
        boolean isOwner = hasBadge(renderer, "OWNER");
        boolean isSponsor = hasBadge(renderer, "MEMBER") || hasBadge(renderer, "SPONSOR");

        return ChatMessage.normalMessage(messageId, authorChannelId, authorName,
                profileImage, message, timestamp, isModerator, isOwner, isSponsor);
    }

    /**
     * Parse un Super Chat
     */
    private ChatMessage parseSuperChat(JsonObject renderer) {
        String messageId = renderer.get("id").getAsString();
        String authorChannelId = renderer.get("authorExternalChannelId").getAsString();
        String authorName = extractText(renderer.getAsJsonObject("authorName"));
        String profileImage = extractThumbnail(renderer.getAsJsonArray("authorPhoto"));
        String message = renderer.has("message") ? extractMessageText(renderer.getAsJsonObject("message")) : "";
        long timestamp = Long.parseLong(renderer.get("timestampUsec").getAsString()) / 1000;

        String amountDisplay = extractText(renderer.getAsJsonObject("purchaseAmountText"));
        long amountMicros = parseAmountMicros(amountDisplay);
        String currency = extractCurrency(amountDisplay);

        return ChatMessage.superChat(messageId, authorChannelId, authorName,
                profileImage, message, timestamp, amountDisplay, amountMicros, currency,
                false, false, true);
    }

    /**
     * Parse un Super Sticker
     */
    private ChatMessage parseSuperSticker(JsonObject renderer) {
        String messageId = renderer.get("id").getAsString();
        String authorChannelId = renderer.get("authorExternalChannelId").getAsString();
        String authorName = extractText(renderer.getAsJsonObject("authorName"));
        String profileImage = extractThumbnail(renderer.getAsJsonArray("authorPhoto"));
        long timestamp = Long.parseLong(renderer.get("timestampUsec").getAsString()) / 1000;

        String amountDisplay = extractText(renderer.getAsJsonObject("purchaseAmountText"));
        long amountMicros = parseAmountMicros(amountDisplay);
        String currency = extractCurrency(amountDisplay);

        return ChatMessage.superSticker(messageId, authorChannelId, authorName,
                profileImage, timestamp, amountDisplay, amountMicros, currency,
                false, false, true);
    }

    /**
     * Parse un nouveau membre
     */
    private ChatMessage parseNewMember(JsonObject renderer) {
        String messageId = renderer.get("id").getAsString();
        String authorChannelId = renderer.get("authorExternalChannelId").getAsString();
        String authorName = extractText(renderer.getAsJsonObject("authorName"));
        String profileImage = extractThumbnail(renderer.getAsJsonArray("authorPhoto"));
        long timestamp = Long.parseLong(renderer.get("timestampUsec").getAsString()) / 1000;

        return ChatMessage.newMember(messageId, authorChannelId, authorName,
                profileImage, timestamp, false, false, true);
    }

    /**
     * Extrait le texte d'un objet "runs"
     */
    private String extractText(JsonObject textObj) {
        if (textObj == null) return "";

        if (textObj.has("simpleText")) {
            return textObj.get("simpleText").getAsString();
        }

        if (textObj.has("runs")) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement run : textObj.getAsJsonArray("runs")) {
                if (run.getAsJsonObject().has("text")) {
                    sb.append(run.getAsJsonObject().get("text").getAsString());
                }
            }
            return sb.toString();
        }

        return "";
    }

    /**
     * Extrait le texte d'un message (peut contenir des emojis)
     */
    private String extractMessageText(JsonObject messageObj) {
        if (messageObj == null) return "";

        if (messageObj.has("runs")) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement run : messageObj.getAsJsonArray("runs")) {
                JsonObject runObj = run.getAsJsonObject();
                if (runObj.has("text")) {
                    sb.append(runObj.get("text").getAsString());
                } else if (runObj.has("emoji")) {
                    // Emoji - utiliser le shortcut ou un placeholder
                    JsonObject emoji = runObj.getAsJsonObject("emoji");
                    if (emoji.has("shortcuts") && emoji.getAsJsonArray("shortcuts").size() > 0) {
                        sb.append(emoji.getAsJsonArray("shortcuts").get(0).getAsString());
                    } else {
                        sb.append("[emoji]");
                    }
                }
            }
            return sb.toString();
        }

        return extractText(messageObj);
    }

    /**
     * Extrait l'URL de la miniature
     */
    private String extractThumbnail(JsonArray thumbnails) {
        if (thumbnails == null || thumbnails.size() == 0) return "";

        try {
            JsonObject thumb = thumbnails.get(0).getAsJsonObject();
            if (thumb.has("thumbnails")) {
                JsonArray innerThumbs = thumb.getAsJsonArray("thumbnails");
                if (innerThumbs.size() > 0) {
                    return innerThumbs.get(0).getAsJsonObject().get("url").getAsString();
                }
            }
            if (thumb.has("url")) {
                return thumb.get("url").getAsString();
            }
        } catch (Exception ignored) {}

        return "";
    }

    /**
     * Vérifie si l'auteur a un badge spécifique
     */
    private boolean hasBadge(JsonObject renderer, String badgeType) {
        if (!renderer.has("authorBadges")) return false;

        try {
            for (JsonElement badge : renderer.getAsJsonArray("authorBadges")) {
                JsonObject badgeRenderer = badge.getAsJsonObject()
                        .getAsJsonObject("liveChatAuthorBadgeRenderer");
                if (badgeRenderer != null) {
                    // Vérifier l'icône
                    if (badgeRenderer.has("icon")) {
                        String iconType = badgeRenderer.getAsJsonObject("icon")
                                .get("iconType").getAsString();
                        if (iconType.contains(badgeType)) return true;
                    }
                    // Vérifier le tooltip
                    if (badgeRenderer.has("tooltip")) {
                        String tooltip = badgeRenderer.get("tooltip").getAsString().toUpperCase();
                        if (tooltip.contains(badgeType)) return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Extrait le continuation token de la réponse
     */
    private String extractContinuation(JsonObject response) {
        try {
            JsonObject continuationContents = response.getAsJsonObject("continuationContents");
            JsonObject liveChatContinuation = continuationContents.getAsJsonObject("liveChatContinuation");
            JsonArray continuations = liveChatContinuation.getAsJsonArray("continuations");

            if (continuations != null && continuations.size() > 0) {
                JsonObject cont = continuations.get(0).getAsJsonObject();

                if (cont.has("invalidationContinuationData")) {
                    return cont.getAsJsonObject("invalidationContinuationData")
                            .get("continuation").getAsString();
                }
                if (cont.has("timedContinuationData")) {
                    return cont.getAsJsonObject("timedContinuationData")
                            .get("continuation").getAsString();
                }
                if (cont.has("reloadContinuationData")) {
                    return cont.getAsJsonObject("reloadContinuationData")
                            .get("continuation").getAsString();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Extrait l'intervalle de polling de la réponse
     */
    private int extractPollingInterval(JsonObject response) {
        try {
            JsonObject continuationContents = response.getAsJsonObject("continuationContents");
            JsonObject liveChatContinuation = continuationContents.getAsJsonObject("liveChatContinuation");
            JsonArray continuations = liveChatContinuation.getAsJsonArray("continuations");

            if (continuations != null && continuations.size() > 0) {
                JsonObject cont = continuations.get(0).getAsJsonObject();

                if (cont.has("invalidationContinuationData")) {
                    return cont.getAsJsonObject("invalidationContinuationData")
                            .get("timeoutMs").getAsInt();
                }
                if (cont.has("timedContinuationData")) {
                    return cont.getAsJsonObject("timedContinuationData")
                            .get("timeoutMs").getAsInt();
                }
            }
        } catch (Exception ignored) {}

        return -1;
    }

    /**
     * Parse le montant en micros depuis le texte affiché
     */
    private long parseAmountMicros(String amountDisplay) {
        try {
            // Enlever les symboles de devise et parser le nombre
            String cleaned = amountDisplay.replaceAll("[^0-9.,]", "")
                    .replace(",", ".");
            double amount = Double.parseDouble(cleaned);
            return (long) (amount * 1_000_000);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Extrait la devise du texte affiché
     */
    private String extractCurrency(String amountDisplay) {
        if (amountDisplay.contains("€")) return "EUR";
        if (amountDisplay.contains("$")) return "USD";
        if (amountDisplay.contains("£")) return "GBP";
        if (amountDisplay.contains("¥")) return "JPY";
        return "EUR";
    }
}
