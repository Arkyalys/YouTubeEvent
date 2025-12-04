package fr.arkyalys.event.scoreboard;

import fr.arkyalys.event.YouTubeEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * Scoreboard stylé pour afficher les stats du live YouTube
 */
public class YouTubeScoreboard {

    private final YouTubeEventPlugin plugin;
    private BukkitTask updateTask;
    private final Set<Player> viewers = new HashSet<>();

    // Formatters
    private final DecimalFormat numberFormat = new DecimalFormat("#,###");

    // Stats
    private long startTime = 0;
    private String channelName = "YouTube Live";
    private boolean running = false;

    public YouTubeScoreboard(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre le scoreboard pour un joueur
     */
    public void show(Player player) {
        viewers.add(player);
        updateScoreboard(player);

        // Démarrer la tâche de mise à jour si pas déjà lancée
        if (updateTask == null) {
            startUpdateTask();
        }
    }

    /**
     * Cache le scoreboard pour un joueur
     */
    public void hide(Player player) {
        viewers.remove(player);
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        // Arrêter la tâche si plus personne ne regarde
        if (viewers.isEmpty() && updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Démarre le live (reset les stats)
     */
    public void startLive(String channelName) {
        this.channelName = channelName != null ? channelName : "YouTube Live";
        this.startTime = System.currentTimeMillis();
        this.running = true;
    }

    /**
     * Arrête le live
     */
    public void stopLive() {
        this.running = false;
        // Cacher le scoreboard pour tous
        for (Player player : new HashSet<>(viewers)) {
            hide(player);
        }
    }

    /**
     * Démarre la tâche de mise à jour (toutes les secondes)
     */
    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : new HashSet<>(viewers)) {
                if (player.isOnline()) {
                    updateScoreboard(player);
                } else {
                    viewers.remove(player);
                }
            }
        }, 20L, 20L); // Update toutes les secondes
    }

    /**
     * Met à jour le scoreboard d'un joueur
     */
    private void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("ytlive", "dummy",
                ChatColor.RED + "♦ " + ChatColor.WHITE + "YouTube" + ChatColor.RED + " Live " + ChatColor.RED + "♦");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 15;

        // Ligne vide en haut
        setScore(objective, ChatColor.GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬", score--);

        // Statut
        if (running) {
            setScore(objective, ChatColor.GREEN + "● " + ChatColor.WHITE + "EN DIRECT", score--);
        } else {
            setScore(objective, ChatColor.RED + "● " + ChatColor.GRAY + "Hors ligne", score--);
        }

        setScore(objective, " ", score--);

        // Stats du live
        long likes = plugin.getLikeTracker().getLastLikeCount();
        long views = plugin.getLikeTracker().getLastViewCount();
        long newLikes = plugin.getLikeTracker().getTotalNewLikes();

        // Likes avec animation
        String likeIcon = getAnimatedHeart();
        setScore(objective, likeIcon + ChatColor.WHITE + " Likes: " + ChatColor.YELLOW + formatNumber(likes), score--);

        // Nouveaux likes cette session
        if (newLikes > 0) {
            setScore(objective, ChatColor.GRAY + "   +" + newLikes + " cette session", score--);
        } else {
            setScore(objective, ChatColor.DARK_GRAY + "   +0 cette session", score--);
        }

        setScore(objective, "  ", score--);

        // Vues
        setScore(objective, ChatColor.AQUA + "👁 " + ChatColor.WHITE + "Vues: " + ChatColor.AQUA + formatNumber(views), score--);

        setScore(objective, "   ", score--);

        // Durée du live
        String duration = formatDuration(System.currentTimeMillis() - startTime);
        setScore(objective, ChatColor.LIGHT_PURPLE + "⏱ " + ChatColor.WHITE + "Durée: " + ChatColor.LIGHT_PURPLE + duration, score--);

        setScore(objective, "    ", score--);

        // Joueurs en ligne
        int online = Bukkit.getOnlinePlayers().size();
        setScore(objective, ChatColor.GREEN + "⚡ " + ChatColor.WHITE + "Joueurs: " + ChatColor.GREEN + online, score--);

        // Ligne de fin
        setScore(objective, ChatColor.GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", score--);

        // Petit texte en bas
        setScore(objective, ChatColor.DARK_GRAY + "mc.arkyalys.net", score--);

        player.setScoreboard(scoreboard);
    }

    /**
     * Ajoute une ligne au scoreboard
     */
    private void setScore(Objective objective, String text, int score) {
        // Limiter à 40 caractères
        if (text.length() > 40) {
            text = text.substring(0, 40);
        }
        Score s = objective.getScore(text);
        s.setScore(score);
    }

    /**
     * Formate un nombre avec des séparateurs
     */
    private String formatNumber(long number) {
        if (number < 0) return "0";
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return numberFormat.format(number);
    }

    /**
     * Formate une durée en HH:MM:SS
     */
    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    /**
     * Retourne un coeur animé basé sur le temps
     */
    private String getAnimatedHeart() {
        long tick = (System.currentTimeMillis() / 500) % 4;
        return switch ((int) tick) {
            case 0 -> ChatColor.RED + "❤";
            case 1 -> ChatColor.LIGHT_PURPLE + "❤";
            case 2 -> ChatColor.RED + "❤";
            case 3 -> ChatColor.DARK_RED + "❤";
            default -> ChatColor.RED + "❤";
        };
    }

    /**
     * Vérifie si un joueur voit le scoreboard
     */
    public boolean isViewing(Player player) {
        return viewers.contains(player);
    }

    /**
     * Toggle le scoreboard pour un joueur
     */
    public void toggle(Player player) {
        if (isViewing(player)) {
            hide(player);
        } else {
            show(player);
        }
    }

    /**
     * Affiche le scoreboard à tous les joueurs
     */
    public void showAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }

    /**
     * Cache le scoreboard pour tous les joueurs
     */
    public void hideAll() {
        for (Player player : new HashSet<>(viewers)) {
            hide(player);
        }
    }

    public boolean isRunning() {
        return running;
    }
}
