package fr.arkyalys.event.api.events;

import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand un nouveau membre rejoint la chaîne
 */
public class YouTubeNewMemberEvent extends YouTubeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final ChatMessage message;

    public YouTubeNewMemberEvent(String liveId, ChatMessage message) {
        super(liveId);
        this.message = message;
    }

    /**
     * Récupère le message complet
     */
    public ChatMessage getMessage() {
        return message;
    }

    /**
     * Récupère le nom du nouveau membre
     */
    public String getMemberName() {
        return message.getAuthorName();
    }

    /**
     * Récupère l'ID de la chaîne du nouveau membre
     */
    public String getMemberChannelId() {
        return message.getAuthorChannelId();
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
