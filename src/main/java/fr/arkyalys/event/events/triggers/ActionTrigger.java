package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Interface pour les triggers d'actions
 */
public interface ActionTrigger {

    /**
     * Exécute l'action
     * @param config La configuration de l'action
     * @param message Le message YouTube qui a déclenché l'action
     * @param target Le joueur cible (streamer)
     */
    void execute(Map<?, ?> config, ChatMessage message, Player target);

    /**
     * Remplace les placeholders dans une chaîne
     * Placeholders disponibles:
     * - %viewer% : Nom de l'auteur YouTube
     * - %message% : Contenu du message
     * - %player% : Nom du joueur cible
     * - %amount% : Montant affiché (Super Chat)
     * - %amount_value% : Valeur numérique du montant
     * - %author_role% : Rôle principal (OWNER, MODERATOR, SPONSOR, VIEWER)
     * - %author_roles% : Tous les rôles séparés par virgule
     * - %is_owner% : true/false si propriétaire de la chaîne
     * - %is_moderator% : true/false si modérateur
     * - %is_sponsor% : true/false si membre/sponsor
     */
    default String replacePlaceholders(String text, ChatMessage message, Player target) {
        if (text == null) return "";

        return text
                .replace("%viewer%", message.getAuthorName())
                .replace("%message%", message.getMessage() != null ? message.getMessage() : "")
                .replace("%player%", target != null ? target.getName() : "")
                .replace("%amount%", message.getAmountDisplay() != null ? message.getAmountDisplay() : "0")
                .replace("%amount_value%", String.format("%.2f", message.getAmountValue()))
                // Nouveaux placeholders pour les infos auteur
                .replace("%author_role%", message.getAuthorRole())
                .replace("%author_roles%", message.getAuthorRoles())
                .replace("%is_owner%", String.valueOf(message.isOwner()))
                .replace("%is_moderator%", String.valueOf(message.isModerator()))
                .replace("%is_sponsor%", String.valueOf(message.isSponsor()));
    }

    /**
     * Récupère une valeur String de la config
     */
    default String getString(Map<?, ?> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Récupère une valeur int de la config
     */
    default int getInt(Map<?, ?> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Récupère une valeur double de la config
     */
    default double getDouble(Map<?, ?> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Récupère une valeur boolean de la config
     */
    default boolean getBoolean(Map<?, ?> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
