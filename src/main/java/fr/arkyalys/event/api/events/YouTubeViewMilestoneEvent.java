package fr.arkyalys.event.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand un palier de vues est atteint
 * Ex: tous les 100 vues
 */
public class YouTubeViewMilestoneEvent extends YouTubeEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final long viewCount;
    private final int milestone;

    public YouTubeViewMilestoneEvent(String liveId, long viewCount, int milestone) {
        super(liveId);
        this.viewCount = viewCount;
        this.milestone = milestone;
    }

    /**
     * Récupère le nombre actuel de vues
     */
    public long getViewCount() {
        return viewCount;
    }

    /**
     * Récupère le palier configuré (ex: 100 = event tous les 100 vues)
     */
    public int getMilestone() {
        return milestone;
    }

    /**
     * Récupère le numéro du palier atteint (ex: 300 vues / 100 = palier 3)
     */
    public int getMilestoneNumber() {
        return (int) (viewCount / milestone);
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
