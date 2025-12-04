package fr.arkyalys.event.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand de nouveaux likes sont détectés
 * Note: YouTube ne permet pas de savoir qui a liké, seulement le nombre
 */
public class YouTubeLikeEvent extends YouTubeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final long newLikes;
    private final long totalLikes;

    public YouTubeLikeEvent(String liveId, long newLikes, long totalLikes) {
        super(liveId);
        this.newLikes = newLikes;
        this.totalLikes = totalLikes;
    }

    /**
     * Récupère le nombre de nouveaux likes depuis la dernière vérification
     */
    public long getNewLikes() {
        return newLikes;
    }

    /**
     * Récupère le nombre total de likes sur la vidéo
     */
    public long getTotalLikes() {
        return totalLikes;
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
