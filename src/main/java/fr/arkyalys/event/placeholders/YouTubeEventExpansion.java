package fr.arkyalys.event.placeholders;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameState;
import fr.arkyalys.event.game.games.TNTLiveGame;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion pour YouTubeEvent
 * Placeholders disponibles:
 * - %youtubeevent_likes% - Nombre de likes
 * - %youtubeevent_likes_next% - Prochain palier de likes
 * - %youtubeevent_likes_remaining% - Likes restants avant prochain palier
 * - %youtubeevent_kills% - Kills du streamer
 * - %youtubeevent_kills_next% - Prochain palier de kills
 * - %youtubeevent_kills_remaining% - Kills restants avant prochain palier
 * - %youtubeevent_participants% - Nombre de participants en vie
 * - %youtubeevent_participants_total% - Nombre total de participants
 * - %youtubeevent_arrows_given% - Flèches totales données
 * - %youtubeevent_streamer% - Nom du streamer
 * - %youtubeevent_status% - Statut du jeu
 * - %youtubeevent_game% - Nom du jeu
 */
public class YouTubeEventExpansion extends PlaceholderExpansion {

    private final YouTubeEventPlugin plugin;

    // Milestones pour likes et kills
    private static final int[] LIKE_MILESTONES = {10, 25, 50, 100, 200, 500, 1000};
    private static final int[] KILL_MILESTONES = {5, 10, 25, 50, 100};

    public YouTubeEventExpansion(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "youtubeevent";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arkyalys";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        // Récupérer le jeu actif
        GameEvent currentGame = plugin.getGameManager().getCurrentGame();

        // Vérifier si c'est un TNTLiveGame
        TNTLiveGame tntGame = null;
        if (currentGame instanceof TNTLiveGame) {
            tntGame = (TNTLiveGame) currentGame;
        }

        switch (params.toLowerCase()) {
            // ==================== LIKES ====================
            case "likes":
                return tntGame != null ? String.valueOf(tntGame.getTotalLikes()) : "0";

            case "likes_next":
                if (tntGame == null) return "10";
                return String.valueOf(getNextMilestone(tntGame.getTotalLikes(), LIKE_MILESTONES));

            case "likes_remaining":
                if (tntGame == null) return "10";
                int currentLikes = tntGame.getTotalLikes();
                int nextLikeMilestone = getNextMilestone(currentLikes, LIKE_MILESTONES);
                return String.valueOf(nextLikeMilestone - currentLikes);

            case "likes_progress":
                if (tntGame == null) return "0";
                return String.valueOf(getMilestoneProgress(tntGame.getTotalLikes(), LIKE_MILESTONES));

            // ==================== KILLS ====================
            case "kills":
                return tntGame != null ? String.valueOf(tntGame.getStreamerKills()) : "0";

            case "kills_next":
                if (tntGame == null) return "5";
                return String.valueOf(getNextMilestone(tntGame.getStreamerKills(), KILL_MILESTONES));

            case "kills_remaining":
                if (tntGame == null) return "5";
                int currentKills = tntGame.getStreamerKills();
                int nextKillMilestone = getNextMilestone(currentKills, KILL_MILESTONES);
                return String.valueOf(nextKillMilestone - currentKills);

            case "kills_progress":
                if (tntGame == null) return "0";
                return String.valueOf(getMilestoneProgress(tntGame.getStreamerKills(), KILL_MILESTONES));

            // ==================== PARTICIPANTS ====================
            case "participants":
                return tntGame != null ? String.valueOf(tntGame.getAliveParticipants()) : "0";

            case "participants_total":
                return tntGame != null ? String.valueOf(tntGame.getTotalParticipants()) : "0";

            case "participants_dead":
                if (tntGame == null) return "0";
                return String.valueOf(tntGame.getTotalParticipants() - tntGame.getAliveParticipants());

            // ==================== AUTRES ====================
            case "arrows_given":
                return tntGame != null ? String.valueOf(tntGame.getTotalArrowsGiven()) : "0";

            case "streamer":
                return tntGame != null ? tntGame.getStreamerName() : "Aucun";

            case "status":
                if (currentGame == null) return "Inactif";
                switch (currentGame.getState()) {
                    case WAITING: return "Inactif";
                    case OPEN: return "Ouvert";
                    case RUNNING: return "En cours";
                    default: return "Inconnu";
                }

            case "game":
                return currentGame != null ? currentGame.getDisplayName() : "Aucun";

            case "game_raw":
                return currentGame != null ? currentGame.getName() : "none";

            default:
                return null;
        }
    }

    /**
     * Trouve le prochain milestone à atteindre
     */
    private int getNextMilestone(int current, int[] milestones) {
        for (int milestone : milestones) {
            if (current < milestone) {
                return milestone;
            }
        }
        // Si tous les milestones sont atteints, retourner le dernier + 100
        return milestones[milestones.length - 1] + 100;
    }

    /**
     * Calcule le pourcentage de progression vers le prochain milestone
     */
    private int getMilestoneProgress(int current, int[] milestones) {
        int previousMilestone = 0;
        for (int milestone : milestones) {
            if (current < milestone) {
                int range = milestone - previousMilestone;
                int progress = current - previousMilestone;
                return (int) ((progress / (double) range) * 100);
            }
            previousMilestone = milestone;
        }
        return 100;
    }
}
