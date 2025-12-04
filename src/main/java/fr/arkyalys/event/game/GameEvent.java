package fr.arkyalys.event.game;

import fr.arkyalys.event.YouTubeEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Classe abstraite pour tous les events de jeu
 */
public abstract class GameEvent {

    protected final YouTubeEventPlugin plugin;
    protected final String name;
    protected final File configFile;
    protected YamlConfiguration config;

    protected GameState state = GameState.WAITING;
    protected final Set<UUID> participants = new HashSet<>();
    protected final Set<UUID> eliminated = new HashSet<>();

    // Config
    protected String worldName;
    protected Location spawnLocation;
    protected int minPlayers = 2;
    protected int maxPlayers = 100;
    protected String displayName;
    protected List<String> winCommands = new ArrayList<>();
    protected List<String> participateCommands = new ArrayList<>();

    // YouTube triggers pour cet event
    protected Map<String, List<String>> youtubeTriggers = new HashMap<>();

    public GameEvent(YouTubeEventPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name.toLowerCase();
        this.configFile = new File(plugin.getDataFolder(), "events/" + this.name + ".yml");
        loadConfig();
    }

    /**
     * Charge la configuration de l'event
     */
    protected void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Charger les paramètres généraux
        displayName = config.getString("display-name", name);
        worldName = config.getString("world", name);
        minPlayers = config.getInt("min-players", 2);
        maxPlayers = config.getInt("max-players", 100);

        // Charger le spawn
        if (config.contains("spawn")) {
            double x = config.getDouble("spawn.x", 0);
            double y = config.getDouble("spawn.y", 64);
            double z = config.getDouble("spawn.z", 0);
            float yaw = (float) config.getDouble("spawn.yaw", 0);
            float pitch = (float) config.getDouble("spawn.pitch", 0);
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                spawnLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }

        // Charger les commandes de victoire
        winCommands = config.getStringList("rewards.win-commands");
        participateCommands = config.getStringList("rewards.participate-commands");

        // Charger les triggers YouTube
        ConfigurationSection ytSection = config.getConfigurationSection("youtube-triggers");
        if (ytSection != null) {
            for (String trigger : ytSection.getKeys(false)) {
                youtubeTriggers.put(trigger, ytSection.getStringList(trigger));
            }
        }

        // Charger la config spécifique à l'event
        loadEventConfig(config);
    }

    /**
     * Crée la configuration par défaut
     */
    protected void createDefaultConfig() {
        configFile.getParentFile().mkdirs();
        config = new YamlConfiguration();

        config.set("display-name", "&6" + name.substring(0, 1).toUpperCase() + name.substring(1));
        config.set("world", name);
        config.set("min-players", 2);
        config.set("max-players", 100);

        config.set("spawn.x", 0);
        config.set("spawn.y", 64);
        config.set("spawn.z", 0);
        config.set("spawn.yaw", 0);
        config.set("spawn.pitch", 0);

        config.set("rewards.win-commands", Arrays.asList(
                "eco give %player% 10000",
                "broadcast &a%player% &7a gagne l'event &6" + name + "&7!"
        ));
        config.set("rewards.participate-commands", Arrays.asList(
                "eco give %player% 100"
        ));

        // Triggers YouTube par défaut
        config.set("youtube-triggers.like", Arrays.asList(
                "broadcast &c+1 Like! &7Merci pour le soutien!"
        ));
        config.set("youtube-triggers.super-chat", Arrays.asList(
                "broadcast &d[SUPER CHAT] &f%viewer% &7a envoye &a%amount%&7!",
                "eco give * 500"
        ));

        // Config spécifique à l'event
        saveDefaultEventConfig(config);

        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de créer la config pour l'event " + name);
        }
    }

    /**
     * Sauvegarde la configuration
     */
    public void saveConfig() {
        try {
            // Sauvegarder le spawn
            if (spawnLocation != null) {
                config.set("spawn.x", spawnLocation.getX());
                config.set("spawn.y", spawnLocation.getY());
                config.set("spawn.z", spawnLocation.getZ());
                config.set("spawn.yaw", spawnLocation.getYaw());
                config.set("spawn.pitch", spawnLocation.getPitch());
            }

            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder la config pour l'event " + name);
        }
    }

    /**
     * Ouvre l'event (phase d'inscription)
     */
    public boolean open() {
        if (state != GameState.WAITING) {
            return false;
        }

        // Vérifier que le monde existe
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Le monde '" + worldName + "' n'existe pas pour l'event " + name);
            return false;
        }

        // Mettre à jour le spawn si pas défini
        if (spawnLocation == null) {
            spawnLocation = world.getSpawnLocation();
        }

        state = GameState.OPEN;
        participants.clear();
        eliminated.clear();

        onOpen();

        // Synchroniser l'affichage YouTube (maintenant filtré par event)
        plugin.getDisplay().syncWithEvent();

        // Broadcast
        String message = plugin.getConfigManager().getPrefix() +
                "&aL'event &6" + displayName + " &aest ouvert! &7Utilisez &f/event join &7pour rejoindre!";
        Bukkit.broadcastMessage(message.replace("&", "§"));

        // Si un live YouTube est actif, afficher le lien
        if (plugin.isConnected()) {
            String liveId = plugin.getLiveChatPoller().getCurrentLiveId();
            String liveLink = "https://youtube.com/watch?v=" + liveId;
            Bukkit.broadcastMessage(("&7Regardez le live: &b" + liveLink).replace("&", "§"));
        }

        return true;
    }

    /**
     * Lance l'event (début du jeu)
     */
    public boolean begin() {
        if (state != GameState.OPEN) {
            return false;
        }

        if (participants.size() < minPlayers) {
            return false;
        }

        state = GameState.RUNNING;
        onBegin();

        String message = plugin.getConfigManager().getPrefix() +
                "&cL'event &6" + displayName + " &ccommence! &7" + participants.size() + " participants!";
        Bukkit.broadcastMessage(message.replace("&", "§"));

        return true;
    }

    /**
     * Arrête l'event
     */
    public void stop() {
        if (state == GameState.WAITING) {
            return;
        }

        onStop();

        // Téléporter tous les participants au spawn
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                teleportToSpawn(player);
            }
        }

        state = GameState.WAITING;
        participants.clear();
        eliminated.clear();

        // Synchroniser l'affichage YouTube (tout le monde peut voir à nouveau)
        plugin.getDisplay().showAll();

        String message = plugin.getConfigManager().getPrefix() +
                "&cL'event &6" + displayName + " &ca été arrêté.";
        Bukkit.broadcastMessage(message.replace("&", "§"));
    }

    /**
     * Un joueur rejoint l'event
     */
    public boolean join(Player player) {
        if (state != GameState.OPEN) {
            return false;
        }

        if (participants.size() >= maxPlayers) {
            player.sendMessage((plugin.getConfigManager().getPrefix() +
                    "&cL'event est plein!").replace("&", "§"));
            return false;
        }

        if (participants.contains(player.getUniqueId())) {
            player.sendMessage((plugin.getConfigManager().getPrefix() +
                    "&cVous participez déjà!").replace("&", "§"));
            return false;
        }

        participants.add(player.getUniqueId());

        // Téléporter vers le monde de l'event
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        } else {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                player.teleport(world.getSpawnLocation());
            }
        }

        onPlayerJoin(player);

        // Synchroniser l'affichage YouTube avec les participants
        plugin.getDisplay().syncWithEvent();

        player.sendMessage((plugin.getConfigManager().getPrefix() +
                "&aVous avez rejoint l'event &6" + displayName + "&a!").replace("&", "§"));

        // Broadcast
        String message = "&7" + player.getName() + " &aa rejoint l'event! &8(" +
                participants.size() + "/" + maxPlayers + ")";
        Bukkit.broadcastMessage(message.replace("&", "§"));

        return true;
    }

    /**
     * Un joueur quitte l'event
     */
    public boolean leave(Player player) {
        if (!participants.contains(player.getUniqueId())) {
            return false;
        }

        participants.remove(player.getUniqueId());
        onPlayerLeave(player);
        teleportToSpawn(player);

        // Synchroniser l'affichage YouTube (retirer le joueur)
        plugin.getDisplay().syncWithEvent();

        player.sendMessage((plugin.getConfigManager().getPrefix() +
                "&eVous avez quitté l'event.").replace("&", "§"));

        return true;
    }

    /**
     * Élimine un joueur
     */
    public void eliminate(Player player) {
        if (!participants.contains(player.getUniqueId())) {
            return;
        }

        // Ne pas éliminer les OP
        if (player.isOp()) {
            return;
        }

        participants.remove(player.getUniqueId());
        eliminated.add(player.getUniqueId());

        onPlayerEliminated(player);

        // Donner les récompenses de participation
        executeCommands(participateCommands, player);

        // Téléporter au spawn
        teleportToSpawn(player);

        // Synchroniser l'affichage YouTube (retirer le joueur éliminé)
        plugin.getDisplay().syncWithEvent();

        String message = "&c" + player.getName() + " &7a été éliminé! &8(" +
                participants.size() + " restants)";
        Bukkit.broadcastMessage(message.replace("&", "§"));

        // Vérifier s'il reste un seul joueur
        checkWinner();
    }

    /**
     * Vérifie s'il y a un gagnant
     */
    protected void checkWinner() {
        if (state != GameState.RUNNING) {
            return;
        }

        // Filtrer les participants encore en ligne
        List<Player> remaining = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !player.isOp()) {
                remaining.add(player);
            }
        }

        if (remaining.size() == 1) {
            Player winner = remaining.get(0);
            win(winner);
        } else if (remaining.isEmpty()) {
            // Pas de gagnant
            stop();
        }
    }

    /**
     * Déclare un gagnant
     */
    public void win(Player winner) {
        state = GameState.ENDED;

        String message = plugin.getConfigManager().getPrefix() +
                "&6&l" + winner.getName() + " &aa gagné l'event &6" + displayName + "&a!";
        Bukkit.broadcastMessage(message.replace("&", "§"));

        // Exécuter les commandes de victoire
        executeCommands(winCommands, winner);

        onWin(winner);

        // Téléporter tout le monde au spawn
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                teleportToSpawn(player);
            }
        }

        // Reset
        participants.clear();
        eliminated.clear();
        state = GameState.WAITING;

        // Synchroniser l'affichage YouTube (tout le monde peut voir à nouveau)
        plugin.getDisplay().showAll();
    }

    /**
     * Téléporte un joueur au spawn de retour
     */
    protected void teleportToSpawn(Player player) {
        // Utiliser le spawn de retour si défini
        Location returnSpawn = plugin.getGameManager().getReturnSpawn();
        if (returnSpawn != null) {
            player.teleport(returnSpawn);
        } else {
            // Fallback: exécuter /spawn sur le joueur
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
        }
    }

    /**
     * Exécute une liste de commandes pour un joueur
     */
    protected void executeCommands(List<String> commands, Player player) {
        for (String cmd : commands) {
            String finalCmd = cmd
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%event%", name)
                    .replace("%display_name%", displayName);

            // Si %all% est présent, exécuter pour tous les joueurs
            if (finalCmd.contains("%all%")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String allCmd = finalCmd.replace("%all%", online.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), allCmd);
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            }
        }
    }

    /**
     * Définit le spawn de l'event
     */
    public void setSpawn(Location location) {
        this.spawnLocation = location;
        this.worldName = location.getWorld().getName();
        config.set("world", worldName);
        saveConfig();
    }

    /**
     * Gère un trigger YouTube
     */
    public void handleYouTubeTrigger(String trigger, String viewer, String amount) {
        if (state == GameState.WAITING) {
            return; // Pas d'event actif
        }

        List<String> commands = youtubeTriggers.get(trigger);
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (String cmd : commands) {
            String finalCmd = cmd
                    .replace("%viewer%", viewer)
                    .replace("%amount%", amount != null ? amount : "")
                    .replace("%event%", name)
                    .replace("%count%", String.valueOf(participants.size()));

            // %participant% = Execute pour chaque participant de l'event UNIQUEMENT
            if (finalCmd.contains("%participant%")) {
                for (UUID uuid : participants) {
                    Player participant = Bukkit.getPlayer(uuid);
                    if (participant != null && participant.isOnline()) {
                        String partCmd = finalCmd.replace("%participant%", participant.getName());
                        executeCommand(partCmd);
                    }
                }
            }
            // %all% = Execute pour tous les joueurs du serveur
            else if (finalCmd.contains("%all%")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String allCmd = finalCmd.replace("%all%", online.getName());
                    executeCommand(allCmd);
                }
            }
            // Pas de placeholder joueur = execute une fois
            else {
                executeCommand(finalCmd);
            }
        }
    }

    /**
     * Execute une commande (gère broadcast spécialement)
     */
    private void executeCommand(String cmd) {
        if (cmd.startsWith("broadcast ")) {
            Bukkit.broadcastMessage(cmd.substring(10).replace("&", "§"));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    // ==================== Méthodes abstraites ====================

    /**
     * Charge la configuration spécifique à l'event
     */
    protected abstract void loadEventConfig(YamlConfiguration config);

    /**
     * Sauvegarde la configuration par défaut spécifique à l'event
     */
    protected abstract void saveDefaultEventConfig(YamlConfiguration config);

    /**
     * Appelé quand l'event est ouvert
     */
    protected abstract void onOpen();

    /**
     * Appelé quand l'event commence
     */
    protected abstract void onBegin();

    /**
     * Appelé quand l'event est arrêté
     */
    protected abstract void onStop();

    /**
     * Appelé quand un joueur rejoint
     */
    protected abstract void onPlayerJoin(Player player);

    /**
     * Appelé quand un joueur quitte
     */
    protected abstract void onPlayerLeave(Player player);

    /**
     * Appelé quand un joueur est éliminé
     */
    protected abstract void onPlayerEliminated(Player player);

    /**
     * Appelé quand quelqu'un gagne
     */
    protected abstract void onWin(Player winner);

    // ==================== Getters ====================

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GameState getState() {
        return state;
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }
}
