package fr.arkyalys.event.youtube.provider;

import fr.arkyalys.event.youtube.models.ChatMessage;

import java.util.List;

/**
 * Interface pour les providers de chat YouTube
 * Permet de switcher entre InnerTube (gratuit) et Data API v3 (quota)
 */
public interface ChatProvider {

    /**
     * Nom du provider pour les logs
     */
    String getName();

    /**
     * Initialise la connexion au live
     * @param videoId ID de la vidéo live
     * @return true si connexion réussie
     */
    boolean connect(String videoId);

    /**
     * Déconnecte du live
     */
    void disconnect();

    /**
     * Récupère les nouveaux messages du chat
     * @return Liste des nouveaux messages, ou liste vide si aucun
     */
    List<ChatMessage> pollMessages();

    /**
     * Vérifie si le provider est connecté
     */
    boolean isConnected();

    /**
     * Récupère l'intervalle de polling recommandé (en ms)
     */
    int getPollingInterval();

    /**
     * Vérifie si le provider utilise du quota API
     */
    boolean usesQuota();
}
