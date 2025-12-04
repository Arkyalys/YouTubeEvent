package fr.arkyalys.event.game;

/**
 * États possibles d'un event
 */
public enum GameState {

    /**
     * Pas d'event en cours
     */
    WAITING,

    /**
     * Event ouvert - Les joueurs peuvent rejoindre
     */
    OPEN,

    /**
     * Event en cours - Plus possible de rejoindre
     */
    RUNNING,

    /**
     * Event terminé
     */
    ENDED
}
