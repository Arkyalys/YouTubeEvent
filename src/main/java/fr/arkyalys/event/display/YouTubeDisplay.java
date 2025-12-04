package fr.arkyalys.event.display;

import fr.arkyalys.event.YouTubeEventPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * Affichage stylé avec BossBars et ActionBar pour les stats YouTube
 */
public class YouTubeDisplay {

    private final YouTubeEventPlugin plugin;
    private BukkitTask updateTask;

    // BossBars
    private BossBar timeBossBar;
    private BossBar ipBossBar;

    // Joueurs qui voient l'affichage
    private final Set<Player> viewers = new HashSet<>();

    // Stats
    private long startTime = 0;
    private boolean running = false;

    // Animation
    private int animationTick = 0;
    private final String[] heartFrames = {"§c❤", "§d❤", "§c❤", "§4❤"};
    private final String[] liveFrames = {"§c§l⬤ §f§lLIVE", "§4§l⬤ §f§lLIVE", "§c§l⬤ §f§lLIVE", "§4§l⬤ §f§lLIVE"};

    // Formatters
    private final DecimalFormat numberFormat = new DecimalFormat("#,###");

    public YouTubeDisplay(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        createBossBars();
    }

    /**
     * Crée les BossBars
     */
    private void createBossBars() {
        // BossBar du temps (en haut)
        timeBossBar = Bukkit.createBossBar(
                "§c§l⬤ §f§lLIVE §8│ §f⏱ §e00:00",
                BarColor.RED,
                BarStyle.SOLID
        );
        timeBossBar.setVisible(true);

        // BossBar de l'IP (en dessous)
        ipBossBar = Bukkit.createBossBar(
                "§6✦ §e§lplay.arkyalys.net §6✦",
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        ipBossBar.setProgress(1.0);
        ipBossBar.setVisible(true);
    }

    /**
     * Démarre l'affichage pour le live
     */
    public void startLive() {
        this.startTime = System.currentTimeMillis();
        this.running = true;
        this.animationTick = 0;

        // Démarrer la mise à jour
        if (updateTask == null) {
            startUpdateTask();
        }
    }

    /**
     * Arrête l'affichage
     */
    public void stopLive() {
        this.running = false;

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        // Cacher les bossbars
        timeBossBar.removeAll();
        ipBossBar.removeAll();
        viewers.clear();
    }

    /**
     * Ajoute un joueur à l'affichage
     */
    public void show(Player player) {
        viewers.add(player);
        timeBossBar.addPlayer(player);
        ipBossBar.addPlayer(player);

        if (updateTask == null && running) {
            startUpdateTask();
        }
    }

    /**
     * Retire un joueur de l'affichage
     */
    public void hide(Player player) {
        viewers.remove(player);
        timeBossBar.removePlayer(player);
        ipBossBar.removePlayer(player);

        // Effacer l'actionbar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    /**
     * Affiche pour tous les joueurs
     */
    public void showAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }

    /**
     * Toggle l'affichage pour un joueur
     */
    public void toggle(Player player) {
        if (viewers.contains(player)) {
            hide(player);
        } else {
            show(player);
        }
    }

    /**
     * Démarre la tâche de mise à jour
     */
    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;

            animationTick++;

            // Mettre à jour les bossbars et actionbar
            updateTimeBossBar();
            updateActionBar();
            updateIpBossBar();

            // Nettoyer les joueurs déconnectés
            viewers.removeIf(p -> !p.isOnline());

        }, 0L, 10L); // Update toutes les 0.5 secondes (10 ticks)
    }

    /**
     * Met à jour la bossbar du temps
     */
    private void updateTimeBossBar() {
        String duration = formatDuration(System.currentTimeMillis() - startTime);
        String liveIndicator = liveFrames[animationTick % liveFrames.length];

        // Calculer la progression (cycle de 60 secondes pour l'animation)
        long seconds = (System.currentTimeMillis() - startTime) / 1000;
        double progress = (seconds % 60) / 60.0;

        // Alterner les couleurs
        BarColor color = (animationTick % 4 < 2) ? BarColor.RED : BarColor.PINK;

        timeBossBar.setTitle(liveIndicator + " §8│ §f⏱ §e" + duration + " §8│ §7Streaming...");
        timeBossBar.setProgress(progress);
        timeBossBar.setColor(color);
    }

    /**
     * Met à jour l'actionbar avec likes/vues/joueurs
     */
    private void updateActionBar() {
        long likes = plugin.getLikeTracker().getLastLikeCount();
        long views = plugin.getLikeTracker().getLastViewCount();
        long newLikes = plugin.getLikeTracker().getTotalNewLikes();
        int players = Bukkit.getOnlinePlayers().size();

        String heart = heartFrames[animationTick % heartFrames.length];

        // Construire l'actionbar
        StringBuilder actionBar = new StringBuilder();

        // Likes
        actionBar.append(heart).append(" §f").append(formatNumber(likes));
        if (newLikes > 0) {
            actionBar.append(" §7(§a+").append(newLikes).append("§7)");
        }

        // Séparateur
        actionBar.append("  §8§l│  ");

        // Vues
        actionBar.append("§b👁 §f").append(formatNumber(views));

        // Séparateur
        actionBar.append("  §8§l│  ");

        // Joueurs
        actionBar.append("§a⚡ §f").append(players).append(" joueur").append(players > 1 ? "s" : "");

        // Envoyer à tous les viewers
        TextComponent component = new TextComponent(actionBar.toString());
        for (Player player : viewers) {
            if (player.isOnline()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, component);
            }
        }
    }

    /**
     * Met à jour la bossbar IP avec animation
     */
    private void updateIpBossBar() {
        // Animation de couleurs pour l'IP
        String[] ipFrames = {
                "§6✦ §e§lplay.arkyalys.net §6✦",
                "§e✦ §6§lplay.arkyalys.net §e✦",
                "§6✦ §e§lplay.arkyalys.net §6✦",
                "§e✦ §6§lplay.arkyalys.net §e✦"
        };

        BarColor[] ipColors = {BarColor.YELLOW, BarColor.WHITE, BarColor.YELLOW, BarColor.WHITE};

        ipBossBar.setTitle(ipFrames[animationTick % ipFrames.length]);
        ipBossBar.setColor(ipColors[animationTick % ipColors.length]);
    }

    /**
     * Formate un nombre
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
     * Formate une durée
     */
    private String formatDuration(long millis) {
        if (millis < 0) millis = 0;

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        if (hours > 0) {
            return String.format("§e%d§7:§e%02d§7:§e%02d", hours, minutes, seconds);
        } else {
            return String.format("§e%02d§7:§e%02d", minutes, seconds);
        }
    }

    /**
     * Vérifie si un joueur voit l'affichage
     */
    public boolean isViewing(Player player) {
        return viewers.contains(player);
    }

    public boolean isRunning() {
        return running;
    }
}
