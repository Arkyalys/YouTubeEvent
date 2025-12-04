package fr.arkyalys.event.api.events;

import org.bukkit.event.HandlerList;

/**
 * Event déclenché quand la connexion au live change d'état
 */
public class YouTubeConnectionEvent extends YouTubeEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ConnectionState state;
    private final String providerName;

    public YouTubeConnectionEvent(String liveId, ConnectionState state, String providerName) {
        super(liveId, false); // Sync event car appelé sur le main thread
        this.state = state;
        this.providerName = providerName;
    }

    /**
     * Récupère l'état de la connexion
     */
    public ConnectionState getState() {
        return state;
    }

    /**
     * Vérifie si on est connecté
     */
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }

    /**
     * Récupère le nom du provider utilisé
     * @return "InnerTube" ou "Data API v3"
     */
    public String getProviderName() {
        return providerName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        ERROR
    }
}
