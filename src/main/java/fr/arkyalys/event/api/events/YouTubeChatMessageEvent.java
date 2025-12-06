package fr.arkyalys.event.api.events;

import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand un message est reçu dans le chat YouTube
 * Inclut tous les types de messages (normal, super chat, nouveau membre, etc.)
 */
public class YouTubeChatMessageEvent extends YouTubeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final ChatMessage message;

    public YouTubeChatMessageEvent(String liveId, ChatMessage message) {
        super(liveId, false); // Sync event
        this.message = message;
    }

    /**
     * Récupère le message complet
     * @return L'objet ChatMessage avec toutes les informations
     */
    public ChatMessage getMessage() {
        return message;
    }

    /**
     * Récupère le nom de l'auteur du message
     * @return Le nom d'affichage de l'auteur
     */
    public String getAuthorName() {
        return message.getAuthorName();
    }

    /**
     * Récupère le contenu du message
     * @return Le texte du message
     */
    public String getMessageContent() {
        return message.getMessage();
    }

    /**
     * Récupère le type de message
     * @return Le type (TEXT_MESSAGE, SUPER_CHAT, SUPER_STICKER, NEW_MEMBER, etc.)
     */
    public ChatMessage.MessageType getMessageType() {
        return message.getType();
    }

    /**
     * Vérifie si l'auteur est le propriétaire de la chaîne
     */
    public boolean isOwner() {
        return message.isOwner();
    }

    /**
     * Vérifie si l'auteur est modérateur
     */
    public boolean isModerator() {
        return message.isModerator();
    }

    /**
     * Vérifie si l'auteur est membre/sponsor
     */
    public boolean isSponsor() {
        return message.isSponsor();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
