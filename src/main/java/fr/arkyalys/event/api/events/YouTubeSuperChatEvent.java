package fr.arkyalys.event.api.events;

import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand un Super Chat ou Super Sticker est reçu
 */
public class YouTubeSuperChatEvent extends YouTubeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final ChatMessage message;

    public YouTubeSuperChatEvent(String liveId, ChatMessage message) {
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
     * Récupère le nom de l'auteur
     */
    public String getAuthorName() {
        return message.getAuthorName();
    }

    /**
     * Récupère le texte du message (peut être vide pour Super Sticker)
     */
    public String getMessageContent() {
        return message.getMessage();
    }

    /**
     * Récupère le montant affiché (ex: "5,00 EUR")
     */
    public String getAmountDisplay() {
        return message.getAmountDisplay();
    }

    /**
     * Récupère le montant en valeur numérique
     * @return Le montant en unité de devise (ex: 5.00)
     */
    public double getAmountValue() {
        return message.getAmountValue();
    }

    /**
     * Récupère la devise
     * @return Le code de devise (ex: "EUR", "USD")
     */
    public String getCurrency() {
        return message.getCurrency();
    }

    /**
     * Vérifie si c'est un Super Sticker
     */
    public boolean isSuperSticker() {
        return message.isSuperSticker();
    }

    /**
     * Vérifie si c'est un Super Chat
     */
    public boolean isSuperChat() {
        return message.isSuperChat();
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
