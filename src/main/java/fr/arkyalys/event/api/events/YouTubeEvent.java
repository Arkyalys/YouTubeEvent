package fr.arkyalys.event.api.events;

import org.bukkit.event.Event;

/**
 * Classe de base pour tous les événements YouTube
 * Les autres plugins peuvent écouter ces événements via Bukkit Events
 */
public abstract class YouTubeEvent extends Event {

    private final String liveId;

    public YouTubeEvent(String liveId) {
        super(true); // Async event
        this.liveId = liveId;
    }

    public YouTubeEvent(String liveId, boolean isAsync) {
        super(isAsync);
        this.liveId = liveId;
    }

    /**
     * Récupère l'ID du live YouTube actuel
     * @return L'ID de la vidéo live
     */
    public String getLiveId() {
        return liveId;
    }
}
