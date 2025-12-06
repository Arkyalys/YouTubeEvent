package fr.arkyalys.event.game;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.games.FeuilleGame;
import fr.arkyalys.event.game.games.TNTLiveGame;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère tous les events de jeu
 */
public class GameManager implements Listener {

    private final YouTubeEventPlugin plugin;
    private final Map<String, GameEvent> registeredGames = new HashMap<>();
    private GameEvent currentGame = null;
    private Location returnSpawn = null; // Spawn de retour (leave/elimination)
    private final Set<UUID> disconnectedPlayers = new HashSet<>(); // Joueurs déco pendant un event
    private boolean autoJoinNewPlayers = true; // TP auto les nouveaux joueurs à l'event

    public GameManager(YouTubeEventPlugin plugin) {
        this.plugin = plugin;

        // Enregistrer les listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Charger le spawn de retour depuis la config
        loadReturnSpawn();

        // Enregistrer les jeux par défaut
        registerDefaultGames();
    }

    /**
     * Enregistre les jeux par défaut
     */
    private void registerDefaultGames() {
        registerGame(new FeuilleGame(plugin));
        registerGame(new TNTLiveGame(plugin));
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
        if (currentGame == null) {
            return false;
        }

        // La méthode join() gère elle-même les états autorisés
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

            // Feuille: boost le tick speed (mécanisme core, pas dans config)
            if (currentGame instanceof FeuilleGame feuilleGame) {
                feuilleGame.triggerBoost();
            }
            // TNTLive: les flèches sont gérées par la config (youtube-triggers.like)
        }

        // TNTLive: vérifier les milestones de likes (Mega TNT, Nuke, etc.)
        if (currentGame instanceof TNTLiveGame tntLiveGame) {
            tntLiveGame.checkLikeMilestones(totalLikes);
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

        // TNTLive gère ses propres morts (subs respawn, streamer = fin)
        if (currentGame instanceof TNTLiveGame) {
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

    /**
     * Force le respawn des participants au spawn de retour (pas dans l'arène)
     * Note: TNTLive gère son propre respawn (subs respawn dans l'arène)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (currentGame == null) {
            return;
        }

        // TNTLive gère son propre respawn (subs restent dans l'arène)
        if (currentGame instanceof TNTLiveGame) {
            return;
        }

        Player player = event.getPlayer();

        // Si le joueur était/est un participant, le faire respawn au spawn de retour
        if (currentGame.isParticipant(player) || currentGame.getState() == GameState.RUNNING) {
            if (returnSpawn != null) {
                event.setRespawnLocation(returnSpawn);
            } else {
                // Fallback: spawn du monde principal
                event.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentGame == null) {
            return;
        }

        Player player = event.getPlayer();
        if (currentGame.isParticipant(player)) {
            // Marquer le joueur pour TP au spawn à la reconnexion
            disconnectedPlayers.add(player.getUniqueId());

            if (currentGame.getState() == GameState.RUNNING) {
                // Éliminer le joueur s'il quitte pendant le jeu
                currentGame.eliminate(player);
            } else {
                // Juste le retirer s'il quitte pendant la phase d'inscription
                currentGame.leave(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Si le joueur avait déco pendant un event, le TP au spawn
        if (disconnectedPlayers.remove(player.getUniqueId())) {
            // Attendre 1 tick pour que le joueur soit bien connecté
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (returnSpawn != null) {
                    player.teleport(returnSpawn);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
                }
                player.sendMessage("§6[Event] §7Vous avez été téléporté au spawn (déconnexion pendant l'event).");
            }, 5L);
            return;
        }

        // Auto-join: Si un event est actif et l'option est activée, inscrire le joueur
        // Pour TNTLive: fonctionne aussi en RUNNING (les subs peuvent rejoindre à tout moment)
        if (autoJoinNewPlayers && hasActiveGame()) {
            GameState gameState = currentGame.getState();
            // Accepter OPEN pour tous les events, et RUNNING pour ceux qui le permettent (TNTLive)
            if (gameState == GameState.OPEN || gameState == GameState.RUNNING) {
                // Attendre quelques ticks pour laisser Essentials faire son spawn d'abord, puis override
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (currentGame != null &&
                        (currentGame.getState() == GameState.OPEN || currentGame.getState() == GameState.RUNNING)) {
                        // Vérifier si le joueur n'est pas déjà inscrit
                        if (!currentGame.isParticipant(player)) {
                            // Inscrire le joueur à l'event (ça le téléporte automatiquement)
                            // La méthode join() vérifie elle-même si on peut rejoindre en RUNNING
                            if (currentGame.join(player)) {
                                player.sendMessage("§a§l[Event] §fVous avez été automatiquement inscrit à l'event §6" +
                                    currentGame.getDisplayName() + "§f!");
                            }
                        }
                    }
                }, 10L); // 10 ticks = 0.5 sec, laisse le temps à Essentials de faire son travail
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

        // Recharger le spawn de retour
        loadReturnSpawn();

        // Recharger les configs
        for (GameEvent game : registeredGames.values()) {
            game.loadConfig();
        }
    }

    /**
     * Définit le spawn de retour (leave/elimination) et le sauvegarde
     */
    public void setReturnSpawn(Location location) {
        this.returnSpawn = location;
        saveReturnSpawn();
    }

    /**
     * Récupère le spawn de retour
     */
    public Location getReturnSpawn() {
        return returnSpawn;
    }

    /**
     * Active/désactive l'auto-join des nouveaux joueurs
     */
    public void setAutoJoinNewPlayers(boolean enabled) {
        this.autoJoinNewPlayers = enabled;
        saveSettings();
    }

    /**
     * Vérifie si l'auto-join est activé
     */
    public boolean isAutoJoinNewPlayers() {
        return autoJoinNewPlayers;
    }

    /**
     * Sauvegarde tous les paramètres dans spawns.yml
     */
    private void saveSettings() {
        File spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        YamlConfiguration config = spawnsFile.exists() ? YamlConfiguration.loadConfiguration(spawnsFile) : new YamlConfiguration();

        // Sauvegarder auto-join
        config.set("settings.auto-join-new-players", autoJoinNewPlayers);

        try {
            config.save(spawnsFile);
            plugin.getLogger().info("[GameManager] Paramètres sauvegardés!");
        } catch (IOException e) {
            plugin.getLogger().severe("[GameManager] Erreur sauvegarde paramètres: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde le spawn de retour dans spawns.yml
     */
    private void saveReturnSpawn() {
        if (returnSpawn == null) return;

        File spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        YamlConfiguration config = spawnsFile.exists() ? YamlConfiguration.loadConfiguration(spawnsFile) : new YamlConfiguration();

        config.set("return-spawn.world", returnSpawn.getWorld().getName());
        config.set("return-spawn.x", returnSpawn.getX());
        config.set("return-spawn.y", returnSpawn.getY());
        config.set("return-spawn.z", returnSpawn.getZ());
        config.set("return-spawn.yaw", returnSpawn.getYaw());
        config.set("return-spawn.pitch", returnSpawn.getPitch());

        try {
            config.save(spawnsFile);
            plugin.getLogger().info("[GameManager] Spawn de retour sauvegardé!");
        } catch (IOException e) {
            plugin.getLogger().severe("[GameManager] Erreur sauvegarde spawn de retour: " + e.getMessage());
        }
    }

    /**
     * Charge le spawn de retour et les paramètres depuis spawns.yml
     */
    private void loadReturnSpawn() {
        File spawnsFile = new File(plugin.getDataFolder(), "spawns.yml");
        if (!spawnsFile.exists()) {
            plugin.getLogger().info("[GameManager] Aucune config de spawn trouvée, utilisation des valeurs par défaut.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(spawnsFile);

        // Charger les paramètres
        this.autoJoinNewPlayers = config.getBoolean("settings.auto-join-new-players", true);
        plugin.getLogger().info("[GameManager] Auto-join nouveaux joueurs: " + (autoJoinNewPlayers ? "activé" : "désactivé"));

        // Charger le spawn de retour
        if (!config.contains("return-spawn.world")) {
            return;
        }

        String worldName = config.getString("return-spawn.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[GameManager] Monde du spawn de retour introuvable: " + worldName);
            return;
        }

        double x = config.getDouble("return-spawn.x");
        double y = config.getDouble("return-spawn.y");
        double z = config.getDouble("return-spawn.z");
        float yaw = (float) config.getDouble("return-spawn.yaw");
        float pitch = (float) config.getDouble("return-spawn.pitch");

        this.returnSpawn = new Location(world, x, y, z, yaw, pitch);
        plugin.getLogger().info("[GameManager] Spawn de retour chargé: " + worldName + " " + x + ", " + y + ", " + z);
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
     * Bloque le PvP entre participants (sauf TNTLive)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Vérifier si c'est un joueur qui attaque
        if (!(event.getDamager() instanceof Player attacker)) return;
        // Vérifier si la victime est un joueur
        if (!(event.getEntity() instanceof Player victim)) return;

        // TNTLive: PvP autorisé!
        if (currentGame instanceof TNTLiveGame) {
            return; // Ne pas bloquer
        }

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
     * Bloque le cassage de blocs pour les participants (sauf TNTLive)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // TNTLive: cassage autorisé!
        if (currentGame instanceof TNTLiveGame) {
            return;
        }

        if (isProtectedParticipant(player)) {
            event.setCancelled(true);
            player.sendMessage("§c§l[Event] §7Vous ne pouvez pas casser de blocs pendant l'event!");
        }
    }

    /**
     * Bloque le placement de blocs pour les participants (sauf TNTLive)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        // TNTLive: placement autorisé!
        if (currentGame instanceof TNTLiveGame) {
            return;
        }

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

    /**
     * Bloque le spawn naturel de mobs dans les mondes des events
     * Autorise: commandes, oeufs, spawners custom, plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        String worldName = event.getLocation().getWorld().getName();

        // Vérifier si c'est un monde d'event (feuille ou tntlive)
        if (!worldName.equalsIgnoreCase("feuille") && !worldName.equalsIgnoreCase("tntlive")) {
            return;
        }

        // Raisons de spawn autorisées
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        switch (reason) {
            case COMMAND:           // /summon
            case SPAWNER_EGG:       // Oeuf de spawn
            case CUSTOM:            // Plugin custom
            case SPAWNER:           // Spawner block (si voulu)
            case DISPENSE_EGG:      // Dispenser avec oeuf
                return; // Autorisé
            default:
                event.setCancelled(true); // Bloqué (NATURAL, CHUNK_GEN, etc.)
        }
    }
}
