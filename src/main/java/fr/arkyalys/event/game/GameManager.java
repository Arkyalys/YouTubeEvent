package fr.arkyalys.event.game;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.games.FeuilleGame;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.bukkit.Location;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Gère tous les events de jeu
 */
public class GameManager implements Listener {

    private final YouTubeEventPlugin plugin;
    private final Map<String, GameEvent> registeredGames = new HashMap<>();
    private GameEvent currentGame = null;
    private Location returnSpawn = null; // Spawn de retour (leave/elimination)

    public GameManager(YouTubeEventPlugin plugin) {
        this.plugin = plugin;

        // Enregistrer les listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Enregistrer les jeux par défaut
        registerDefaultGames();
    }

    /**
     * Enregistre les jeux par défaut
     */
    private void registerDefaultGames() {
        registerGame(new FeuilleGame(plugin));
    }

    /**
     * Enregistre un nouveau jeu
     */
    public void registerGame(GameEvent game) {
        registeredGames.put(game.getName().toLowerCase(), game);
        plugin.getLogger().info("Event enregistré: " + game.getName());
    }

    /**
     * Récupère un jeu par son nom
     */
    public GameEvent getGame(String name) {
        return registeredGames.get(name.toLowerCase());
    }

    /**
     * Récupère tous les jeux enregistrés
     */
    public Collection<GameEvent> getGames() {
        return registeredGames.values();
    }

    /**
     * Récupère le jeu en cours
     */
    public GameEvent getCurrentGame() {
        return currentGame;
    }

    /**
     * Vérifie si un event est en cours
     */
    public boolean hasActiveGame() {
        return currentGame != null && currentGame.getState() != GameState.WAITING;
    }

    /**
     * Ouvre un event
     */
    public boolean startGame(String gameName) {
        if (hasActiveGame()) {
            return false;
        }

        GameEvent game = getGame(gameName);
        if (game == null) {
            return false;
        }

        if (game.open()) {
            currentGame = game;
            return true;
        }

        return false;
    }

    /**
     * Lance l'event en cours
     */
    public boolean beginGame() {
        if (currentGame == null || currentGame.getState() != GameState.OPEN) {
            return false;
        }

        return currentGame.begin();
    }

    /**
     * Arrête l'event en cours
     */
    public void stopGame() {
        if (currentGame != null) {
            currentGame.stop();
            currentGame = null;
        }
    }

    /**
     * Un joueur rejoint l'event en cours
     */
    public boolean joinGame(Player player) {
        if (currentGame == null || currentGame.getState() != GameState.OPEN) {
            return false;
        }

        return currentGame.join(player);
    }

    /**
     * Un joueur quitte l'event en cours
     */
    public boolean leaveGame(Player player) {
        if (currentGame == null) {
            return false;
        }

        return currentGame.leave(player);
    }

    /**
     * Gère les triggers YouTube pour l'event en cours
     */
    public void handleYouTubeMessage(ChatMessage message) {
        if (currentGame == null || currentGame.getState() == GameState.WAITING) {
            return;
        }

        String viewer = message.getAuthorName();

        switch (message.getType()) {
            case TEXT_MESSAGE:
                currentGame.handleYouTubeTrigger("message", viewer, null);
                break;

            case SUPER_CHAT:
                currentGame.handleYouTubeTrigger("super-chat", viewer, message.getAmountDisplay());
                break;

            case SUPER_STICKER:
                currentGame.handleYouTubeTrigger("super-sticker", viewer, message.getAmountDisplay());
                break;

            case NEW_MEMBER:
                currentGame.handleYouTubeTrigger("new-member", viewer, null);
                break;
        }
    }

    /**
     * Gère les likes YouTube pour l'event en cours
     */
    public void handleYouTubeLike(long newLikes, long totalLikes) {
        if (currentGame == null || currentGame.getState() == GameState.WAITING) {
            return;
        }

        // Exécuter X fois pour chaque like
        for (int i = 0; i < newLikes; i++) {
            currentGame.handleYouTubeTrigger("like", "Viewer", String.valueOf(totalLikes));

            // Si c'est l'event Feuille, activer le boost
            if (currentGame instanceof FeuilleGame feuilleGame) {
                feuilleGame.triggerBoost();
            }
        }
    }

    /**
     * Gère les paliers de vues pour l'event en cours
     */
    public void handleYouTubeViewMilestone(long viewCount) {
        if (currentGame == null || currentGame.getState() == GameState.WAITING) {
            return;
        }

        currentGame.handleYouTubeTrigger("view-milestone", "YouTube", String.valueOf(viewCount));
    }

    // ==================== Event Listeners ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (currentGame == null || currentGame.getState() != GameState.RUNNING) {
            return;
        }

        Player player = event.getEntity();
        if (currentGame.isParticipant(player)) {
            // Éliminer le joueur
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                currentGame.eliminate(player);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentGame == null) {
            return;
        }

        Player player = event.getPlayer();
        if (currentGame.isParticipant(player)) {
            if (currentGame.getState() == GameState.RUNNING) {
                // Éliminer le joueur s'il quitte pendant le jeu
                currentGame.eliminate(player);
            } else {
                // Juste le retirer s'il quitte pendant la phase d'inscription
                currentGame.leave(player);
            }
        }
    }

    /**
     * Recharge tous les jeux
     */
    public void reload() {
        // Arrêter l'event en cours si présent
        if (hasActiveGame()) {
            stopGame();
        }

        // Recharger les configs
        for (GameEvent game : registeredGames.values()) {
            game.loadConfig();
        }
    }

    /**
     * Définit le spawn de retour (leave/elimination)
     */
    public void setReturnSpawn(Location location) {
        this.returnSpawn = location;
    }

    /**
     * Récupère le spawn de retour
     */
    public Location getReturnSpawn() {
        return returnSpawn;
    }

    // ==================== Protections Event ====================

    /**
     * Vérifie si un joueur est protégé (participant et non-OP)
     */
    private boolean isProtectedParticipant(Player player) {
        if (player.isOp()) return false;
        if (currentGame == null) return false;
        if (currentGame.getState() != GameState.RUNNING && currentGame.getState() != GameState.OPEN) return false;
        return currentGame.isParticipant(player);
    }

    /**
     * Bloque le PvP entre participants
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Vérifier si c'est un joueur qui attaque
        if (!(event.getDamager() instanceof Player attacker)) return;
        // Vérifier si la victime est un joueur
        if (!(event.getEntity() instanceof Player victim)) return;

        // Si l'attaquant OU la victime est un participant protégé, bloquer
        if (isProtectedParticipant(attacker) || isProtectedParticipant(victim)) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloque la perte de faim pour les participants
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (isProtectedParticipant(player)) {
            // Bloquer la perte de faim (mais pas le gain)
            if (event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Bloque le cassage de blocs pour les participants
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l[Event] §7Vous ne pouvez pas casser de blocs pendant l'event!");
        }
    }

    /**
     * Bloque le placement de blocs pour les participants
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l[Event] §7Vous ne pouvez pas placer de blocs pendant l'event!");
        }
    }

    /**
     * Bloque les commandes sauf /event leave pour les participants
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!isProtectedParticipant(player)) return;

        String message = event.getMessage().toLowerCase();

        // Autoriser /event leave et ses alias
        if (message.startsWith("/event leave") ||
            message.startsWith("/ev leave") ||
            message.startsWith("/events leave")) {
            return;
        }

        // Bloquer toutes les autres commandes
        event.setCancelled(true);
        player.sendMessage("§c§l[Event] §7Vous ne pouvez utiliser que §f/event leave §7pendant l'event!");
    }
}
