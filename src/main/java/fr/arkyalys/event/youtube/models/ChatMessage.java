package fr.arkyalys.event.youtube.models;

/**
 * Représente un message du chat YouTube Live
 */
public class ChatMessage {

    private final String messageId;
    private final String authorChannelId;
    private final String authorName;
    private final String authorProfileImage;
    private final String message;
    private final long publishedAt;
    private final MessageType type;

    // Pour les Super Chats
    private final String amountDisplay;
    private final long amountMicros;
    private final String currency;

    // Infos auteur (depuis YouTube API)
    private final boolean isModerator;
    private final boolean isOwner;
    private final boolean isSponsor;

    public ChatMessage(String messageId, String authorChannelId, String authorName,
                       String authorProfileImage, String message, long publishedAt,
                       MessageType type, String amountDisplay, long amountMicros, String currency,
                       boolean isModerator, boolean isOwner, boolean isSponsor) {
        this.messageId = messageId;
        this.authorChannelId = authorChannelId;
        this.authorName = authorName;
        this.authorProfileImage = authorProfileImage;
        this.message = message;
        this.publishedAt = publishedAt;
        this.type = type;
        this.amountDisplay = amountDisplay;
        this.amountMicros = amountMicros;
        this.currency = currency;
        this.isModerator = isModerator;
        this.isOwner = isOwner;
        this.isSponsor = isSponsor;
    }

    // Builder pour message normal
    public static ChatMessage normalMessage(String messageId, String authorChannelId,
                                            String authorName, String authorProfileImage,
                                            String message, long publishedAt,
                                            boolean isModerator, boolean isOwner, boolean isSponsor) {
        return new ChatMessage(messageId, authorChannelId, authorName, authorProfileImage,
                               message, publishedAt, MessageType.TEXT_MESSAGE, null, 0, null,
                               isModerator, isOwner, isSponsor);
    }

    // Builder pour Super Chat
    public static ChatMessage superChat(String messageId, String authorChannelId,
                                        String authorName, String authorProfileImage,
                                        String message, long publishedAt,
                                        String amountDisplay, long amountMicros, String currency,
                                        boolean isModerator, boolean isOwner, boolean isSponsor) {
        return new ChatMessage(messageId, authorChannelId, authorName, authorProfileImage,
                               message, publishedAt, MessageType.SUPER_CHAT,
                               amountDisplay, amountMicros, currency,
                               isModerator, isOwner, isSponsor);
    }

    // Builder pour Super Sticker
    public static ChatMessage superSticker(String messageId, String authorChannelId,
                                           String authorName, String authorProfileImage,
                                           long publishedAt, String amountDisplay,
                                           long amountMicros, String currency,
                                           boolean isModerator, boolean isOwner, boolean isSponsor) {
        return new ChatMessage(messageId, authorChannelId, authorName, authorProfileImage,
                               "", publishedAt, MessageType.SUPER_STICKER,
                               amountDisplay, amountMicros, currency,
                               isModerator, isOwner, isSponsor);
    }

    // Builder pour nouveau membre
    public static ChatMessage newMember(String messageId, String authorChannelId,
                                        String authorName, String authorProfileImage,
                                        long publishedAt,
                                        boolean isModerator, boolean isOwner, boolean isSponsor) {
        return new ChatMessage(messageId, authorChannelId, authorName, authorProfileImage,
                               "", publishedAt, MessageType.NEW_MEMBER, null, 0, null,
                               isModerator, isOwner, isSponsor);
    }

    /**
     * Récupère le montant en valeur décimale (pour Super Chat)
     * @return le montant ou 0 si pas de montant
     */
    public double getAmountValue() {
        if (amountMicros <= 0) return 0;
        return amountMicros / 1_000_000.0;
    }

    // Getters
    public String getMessageId() { return messageId; }
    public String getAuthorChannelId() { return authorChannelId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorProfileImage() { return authorProfileImage; }
    public String getMessage() { return message; }
    public long getPublishedAt() { return publishedAt; }
    public MessageType getType() { return type; }
    public String getAmountDisplay() { return amountDisplay; }
    public long getAmountMicros() { return amountMicros; }
    public String getCurrency() { return currency; }

    public boolean isSuperChat() { return type == MessageType.SUPER_CHAT; }
    public boolean isSuperSticker() { return type == MessageType.SUPER_STICKER; }
    public boolean isNewMember() { return type == MessageType.NEW_MEMBER; }

    // Getters pour infos auteur
    public boolean isModerator() { return isModerator; }
    public boolean isOwner() { return isOwner; }
    public boolean isSponsor() { return isSponsor; }

    /**
     * Retourne le rôle de l'auteur sous forme de texte
     * @return "OWNER", "MODERATOR", "SPONSOR", ou "VIEWER"
     */
    public String getAuthorRole() {
        if (isOwner) return "OWNER";
        if (isModerator) return "MODERATOR";
        if (isSponsor) return "SPONSOR";
        return "VIEWER";
    }

    /**
     * Retourne tous les rôles de l'auteur séparés par des virgules
     * @return ex: "OWNER, MODERATOR" ou "VIEWER"
     */
    public String getAuthorRoles() {
        StringBuilder roles = new StringBuilder();
        if (isOwner) roles.append("OWNER");
        if (isModerator) {
            if (roles.length() > 0) roles.append(", ");
            roles.append("MODERATOR");
        }
        if (isSponsor) {
            if (roles.length() > 0) roles.append(", ");
            roles.append("SPONSOR");
        }
        if (roles.length() == 0) return "VIEWER";
        return roles.toString();
    }

    @Override
    public String toString() {
        if (type == MessageType.SUPER_CHAT) {
            return String.format("[SuperChat %s] %s: %s", amountDisplay, authorName, message);
        } else if (type == MessageType.SUPER_STICKER) {
            return String.format("[SuperSticker %s] %s", amountDisplay, authorName);
        } else if (type == MessageType.NEW_MEMBER) {
            return String.format("[Nouveau Membre] %s", authorName);
        }
        return String.format("%s: %s", authorName, message);
    }

    public enum MessageType {
        TEXT_MESSAGE,
        SUPER_CHAT,
        SUPER_STICKER,
        NEW_MEMBER,
        MEMBERSHIP_GIFT
    }
}
