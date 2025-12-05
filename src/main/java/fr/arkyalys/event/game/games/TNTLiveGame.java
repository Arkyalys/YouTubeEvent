package fr.arkyalys.event.game.games;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameState;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Event "TNTLive" - Le streamer vs les subs
 * - Équipe Streamer = 1 joueur
 * - Équipe Sub = tous les autres
 * - Subs respawn infiniment tant que le streamer est en vie
 * - Streamer meurt = fin de l'event
 * - 1 like YouTube = 1 flèche pour tous
 */
public class TNTLiveGame extends GameEvent implements Listener {

    // Teams
    private UUID streamerId = null;
    private final Set<UUID> subTeam = new HashSet<>();

    // Spawns
    private Location streamerSpawn = null;
    private Location subSpawn = null;

    // Stuff (inventaire sauvegardé)
    private ItemStack[] streamerStuff = null;
    private ItemStack[] streamerArmor = null;
    private ItemStack[] subStuff = null;
    private ItemStack[] subArmor = null;

    // Schematic
    private String schematicName = "tntlive";
    private BlockVector3 schematicOrigin = null;

    // Stats
    private int streamerKills = 0;

    public TNTLiveGame(YouTubeEventPlugin plugin) {
        super(plugin, "tntlive");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void loadEventConfig(YamlConfiguration config) {
        // Charger le nom du schematic
        schematicName = config.getString("tntlive.schematic", "tntlive");
        plugin.getLogger().info("[TNTLive] Chargement config - schematicName: " + schematicName);

        // Charger l'origine du schematic
        plugin.getLogger().info("[TNTLive] Config contient 'tntlive.schematic-origin': " + config.contains("tntlive.schematic-origin"));
        plugin.getLogger().info("[TNTLive] Config keys at 'tntlive': " + (config.getConfigurationSection("tntlive") != null ?
            config.getConfigurationSection("tntlive").getKeys(false) : "null"));

        if (config.contains("tntlive.schematic-origin")) {
            int x = config.getInt("tntlive.schematic-origin.x", 0);
            int y = config.getInt("tntlive.schematic-origin.y", 64);
            int z = config.getInt("tntlive.schematic-origin.z", 0);
            schematicOrigin = BlockVector3.at(x, y, z);
            plugin.getLogger().info("[TNTLive] Origine schematic chargée: " + schematicOrigin);
        } else {
            plugin.getLogger().warning("[TNTLive] Aucune origine schematic dans la config!");
        }

        // Charger le spawn streamer
        if (config.contains("tntlive.streamer-spawn")) {
            double x = config.getDouble("tntlive.streamer-spawn.x");
            double y = config.getDouble("tntlive.streamer-spawn.y");
            double z = config.getDouble("tntlive.streamer-spawn.z");
            float yaw = (float) config.getDouble("tntlive.streamer-spawn.yaw", 0);
            float pitch = (float) config.getDouble("tntlive.streamer-spawn.pitch", 0);
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                streamerSpawn = new Location(world, x, y, z, yaw, pitch);
            }
        }

        // Charger le spawn sub
        if (config.contains("tntlive.sub-spawn")) {
            double x = config.getDouble("tntlive.sub-spawn.x");
            double y = config.getDouble("tntlive.sub-spawn.y");
            double z = config.getDouble("tntlive.sub-spawn.z");
            float yaw = (float) config.getDouble("tntlive.sub-spawn.yaw", 0);
            float pitch = (float) config.getDouble("tntlive.sub-spawn.pitch", 0);
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                subSpawn = new Location(world, x, y, z, yaw, pitch);
            }
        }

        // Charger le stuff streamer
        if (config.contains("tntlive.streamer-stuff")) {
            streamerStuff = loadItemStacks(config, "tntlive.streamer-stuff.inventory");
            streamerArmor = loadItemStacks(config, "tntlive.streamer-stuff.armor");
        }

        // Charger le stuff sub
        if (config.contains("tntlive.sub-stuff")) {
            subStuff = loadItemStacks(config, "tntlive.sub-stuff.inventory");
            subArmor = loadItemStacks(config, "tntlive.sub-stuff.armor");
        }

        // Charger le streamer (persistant!)
        if (config.contains("tntlive.streamer-uuid")) {
            String uuidStr = config.getString("tntlive.streamer-uuid");
            try {
                streamerId = UUID.fromString(uuidStr);
                plugin.getLogger().info("[TNTLive] Streamer charge: " + streamerId);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[TNTLive] UUID streamer invalide: " + uuidStr);
            }
        }
    }

    @Override
    protected void saveDefaultEventConfig(YamlConfiguration config) {
        config.set("tntlive.schematic", "tntlive");
        config.set("tntlive.schematic-origin.x", 0);
        config.set("tntlive.schematic-origin.y", 64);
        config.set("tntlive.schematic-origin.z", 0);

        // YouTube triggers
        config.set("youtube-triggers.like", List.of(
                "give %participant% arrow 1"
        ));
    }

    // ==================== Gestion des équipes ====================

    /**
     * Définit le streamer
     */
    public void setStreamer(Player player) {
        this.streamerId = player.getUniqueId();
        saveStreamerToConfig();
        plugin.getLogger().info("Streamer défini: " + player.getName());
    }

    /**
     * Récupère le streamer
     */
    public Player getStreamer() {
        if (streamerId == null) return null;
        return Bukkit.getPlayer(streamerId);
    }

    /**
     * Vérifie si un joueur est le streamer
     */
    public boolean isStreamer(Player player) {
        return streamerId != null && streamerId.equals(player.getUniqueId());
    }

    /**
     * Vérifie si un joueur est dans l'équipe sub
     */
    public boolean isSub(Player player) {
        return subTeam.contains(player.getUniqueId());
    }

    // ==================== Gestion des spawns ====================

    /**
     * Définit le spawn du streamer
     */
    public void setStreamerSpawn(Location location) {
        this.streamerSpawn = location.clone();
        saveSpawnToConfig("streamer-spawn", location);
        plugin.getLogger().info("Spawn streamer défini: " + locationToString(location));
    }

    /**
     * Définit le spawn des subs
     */
    public void setSubSpawn(Location location) {
        this.subSpawn = location.clone();
        saveSpawnToConfig("sub-spawn", location);
        plugin.getLogger().info("Spawn sub défini: " + locationToString(location));
    }

    private String locationToString(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    // ==================== Gestion du stuff ====================

    /**
     * Sauvegarde le stuff du streamer depuis l'inventaire d'un joueur
     */
    public void saveStreamerStuff(Player player) {
        streamerStuff = player.getInventory().getContents().clone();
        streamerArmor = player.getInventory().getArmorContents().clone();
        saveStuffToConfig("streamer-stuff", streamerStuff, streamerArmor);
        plugin.getLogger().info("Stuff streamer sauvegardé depuis " + player.getName());
    }

    /**
     * Sauvegarde le stuff des subs depuis l'inventaire d'un joueur
     */
    public void saveSubStuff(Player player) {
        subStuff = player.getInventory().getContents().clone();
        subArmor = player.getInventory().getArmorContents().clone();
        saveStuffToConfig("sub-stuff", subStuff, subArmor);
        plugin.getLogger().info("Stuff sub sauvegardé depuis " + player.getName());
    }

    /**
     * Donne le stuff approprié à un joueur selon son équipe
     */
    private void giveStuff(Player player) {
        player.getInventory().clear();

        if (isStreamer(player)) {
            if (streamerStuff != null) {
                player.getInventory().setContents(streamerStuff.clone());
            }
            if (streamerArmor != null) {
                player.getInventory().setArmorContents(streamerArmor.clone());
            }
        } else {
            if (subStuff != null) {
                player.getInventory().setContents(subStuff.clone());
            }
            if (subArmor != null) {
                player.getInventory().setArmorContents(subArmor.clone());
            }
        }
    }

    // ==================== Schematic ====================

    /**
     * Définit l'origine du schematic (position actuelle du joueur)
     */
    public void setSchematicOrigin(Location location) {
        this.schematicOrigin = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        config.set("tntlive.schematic-origin.x", location.getBlockX());
        config.set("tntlive.schematic-origin.y", location.getBlockY());
        config.set("tntlive.schematic-origin.z", location.getBlockZ());
        saveConfig();
        plugin.getLogger().info("Origine schematic définie: " + schematicOrigin);
    }

    /**
     * Paste le schematic pour reset la map (public pour commande /event reset)
     */
    public void pasteSchematic() {
        plugin.getLogger().info("[TNTLive] === DEBUT PASTE SCHEMATIC ===");
        plugin.getLogger().info("[TNTLive] schematicOrigin: " + (schematicOrigin != null ? schematicOrigin.toString() : "NULL"));
        plugin.getLogger().info("[TNTLive] schematicName: " + schematicName);
        plugin.getLogger().info("[TNTLive] worldName: " + worldName);

        if (schematicOrigin == null) {
            plugin.getLogger().warning("[TNTLive] Origine du schematic non définie!");
            return;
        }

        // Chercher le fichier schematic
        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        plugin.getLogger().info("[TNTLive] Dossier schematics: " + schematicsFolder.getAbsolutePath());
        plugin.getLogger().info("[TNTLive] Dossier existe: " + schematicsFolder.exists());

        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
            plugin.getLogger().warning("[TNTLive] Dossier schematics créé. Placez-y vos fichiers .schem");
            return;
        }

        // Lister les fichiers dans le dossier
        File[] files = schematicsFolder.listFiles();
        if (files != null) {
            plugin.getLogger().info("[TNTLive] Fichiers dans schematics: " + files.length);
            for (File f : files) {
                plugin.getLogger().info("[TNTLive]   - " + f.getName());
            }
        }

        File schematicFile = findSchematicFile(schematicsFolder, schematicName);
        if (schematicFile == null) {
            plugin.getLogger().warning("[TNTLive] Schematic '" + schematicName + "' introuvable dans " + schematicsFolder.getPath());
            return;
        }

        plugin.getLogger().info("[TNTLive] Fichier schematic trouvé: " + schematicFile.getAbsolutePath());
        plugin.getLogger().info("[TNTLive] Taille du fichier: " + schematicFile.length() + " bytes");

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getLogger().warning("[TNTLive] Format de schematic non reconnu: " + schematicFile.getName());
                return;
            }
            plugin.getLogger().info("[TNTLive] Format détecté: " + format.getName());

            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                clipboard = reader.read();
            }

            plugin.getLogger().info("[TNTLive] Clipboard chargé - Dimensions: " +
                    clipboard.getDimensions().getX() + "x" +
                    clipboard.getDimensions().getY() + "x" +
                    clipboard.getDimensions().getZ());
            plugin.getLogger().info("[TNTLive] Clipboard origin: " + clipboard.getOrigin());
            plugin.getLogger().info("[TNTLive] Clipboard minPoint: " + clipboard.getMinimumPoint());
            plugin.getLogger().info("[TNTLive] Clipboard maxPoint: " + clipboard.getMaximumPoint());

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[TNTLive] Monde '" + worldName + "' introuvable pour paste schematic");
                return;
            }

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            plugin.getLogger().info("[TNTLive] Monde WorldEdit: " + weWorld.getName());

            // Utiliser le builder pattern recommandé par FAWE
            EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(weWorld)
                    .build();

            try {
                plugin.getLogger().info("[TNTLive] Création de l'opération de paste à " + schematicOrigin);

                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(schematicOrigin)
                        .ignoreAirBlocks(false)
                        .copyEntities(false)
                        .build();

                plugin.getLogger().info("[TNTLive] Exécution de l'opération...");
                Operations.complete(operation);

                int blocksChanged = editSession.getBlockChangeCount();
                plugin.getLogger().info("[TNTLive] Schematic '" + schematicName + "' collé avec succès! Blocs modifiés: " + blocksChanged);

                if (blocksChanged == 0) {
                    plugin.getLogger().warning("[TNTLive] ATTENTION: Aucun bloc n'a été modifié! Vérifiez l'origine et le schematic.");
                }

            } finally {
                editSession.close();
            }

            plugin.getLogger().info("[TNTLive] === FIN PASTE SCHEMATIC ===");

        } catch (Exception e) {
            plugin.getLogger().warning("[TNTLive] Erreur lors du paste du schematic: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cherche un fichier schematic avec différentes extensions
     */
    private File findSchematicFile(File folder, String name) {
        String[] extensions = {".schem", ".schematic"};
        for (String ext : extensions) {
            File file = new File(folder, name + ext);
            if (file.exists()) {
                return file;
            }
        }
        // Essayer sans extension (au cas où le nom inclut déjà l'extension)
        File direct = new File(folder, name);
        if (direct.exists()) {
            return direct;
        }
        return null;
    }

    // ==================== Game Events ====================

    @Override
    protected void onOpen() {
        // Reset
        subTeam.clear();
        streamerKills = 0;

        if (streamerId == null) {
            plugin.getLogger().warning("Aucun streamer défini! Utilisez /event setstreamer <joueur>");
        }

        plugin.getLogger().info("Event TNTLive ouvert!");
    }

    @Override
    protected void onBegin() {
        // Vérifier qu'on a un streamer
        Player streamer = getStreamer();
        if (streamer == null || !participants.contains(streamerId)) {
            plugin.getLogger().warning("Le streamer n'est pas dans l'event!");
            Bukkit.broadcastMessage("§c[TNTLive] Le streamer n'est pas présent! Event annulé.");
            return;
        }

        // Assigner tous les autres à l'équipe sub
        for (UUID uuid : participants) {
            if (!uuid.equals(streamerId)) {
                subTeam.add(uuid);
            }
        }

        // Téléporter et équiper tout le monde
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Téléporter au bon spawn
                if (isStreamer(player)) {
                    if (streamerSpawn != null) {
                        player.teleport(streamerSpawn);
                    }
                } else {
                    if (subSpawn != null) {
                        player.teleport(subSpawn);
                    }
                }

                // Donner le stuff
                giveStuff(player);

                // Heal et feed
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
            }
        }

        // Annonce
        Player streamerPlayer = getStreamer();
        String streamerName = streamerPlayer != null ? streamerPlayer.getName() : "???";
        Bukkit.broadcastMessage("§c§l[TNTLive] §f" + streamerName + " §cvs §f" + subTeam.size() + " §csubs!");
        Bukkit.broadcastMessage("§7Le streamer doit survivre! Les subs respawn infiniment.");

        plugin.getLogger().info("Event TNTLive commencé! Streamer: " + streamerName + ", Subs: " + subTeam.size());
    }

    @Override
    protected void onStop() {
        // Paste le schematic pour reset la map
        pasteSchematic();

        plugin.getLogger().info("Event TNTLive arrêté!");
    }

    @Override
    protected void onPlayerJoin(Player player) {
        player.sendMessage("§a[TNTLive] §7Vous avez rejoint l'event!");

        if (isStreamer(player)) {
            player.sendMessage("§6§lVous êtes le STREAMER! §7Survivez le plus longtemps possible!");
        } else {
            player.sendMessage("§c§lVous êtes un SUB! §7Éliminez le streamer!");
        }
    }

    @Override
    protected void onPlayerLeave(Player player) {
        subTeam.remove(player.getUniqueId());
    }

    @Override
    protected void onPlayerEliminated(Player player) {
        // Ne devrait pas être appelé car on gère la mort différemment
    }

    @Override
    protected void onWin(Player winner) {
        // Paste le schematic
        pasteSchematic();

        // Annonce
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', "&c&lLES SUBS GAGNENT!"),
                    ChatColor.translateAlternateColorCodes('&', "&7Le streamer a été éliminé!"),
                    10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f);
        }
    }

    // ==================== Death Handling ====================

    /**
     * Gère la mort des joueurs dans l'event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (state != GameState.RUNNING) return;

        Player player = event.getEntity();
        if (!participants.contains(player.getUniqueId())) return;

        // Clear les drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        if (isStreamer(player)) {
            // Le streamer est mort = fin de l'event!
            plugin.getLogger().info("Le streamer " + player.getName() + " est mort! Fin de l'event.");

            // Scheduler pour après le respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Annoncer la victoire des subs
                Bukkit.broadcastMessage("§c§l[TNTLive] §fLe streamer §c" + player.getName() + " §fa été éliminé!");
                Bukkit.broadcastMessage("§a§l[TNTLive] §fLes SUBS gagnent! §7(" + streamerKills + " kills par le streamer)");

                // Fin de l'event
                stop();
            }, 20L);

        } else {
            // Un sub est mort - il va respawn
            streamerKills++;

            // Message
            Player killer = player.getKiller();
            if (killer != null && isStreamer(killer)) {
                Bukkit.broadcastMessage("§7[TNTLive] §f" + player.getName() + " §7éliminé! §8(Kills: " + streamerKills + ")");
            }
        }
    }

    /**
     * Gère le respawn des subs
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (state != GameState.RUNNING) return;

        Player player = event.getPlayer();
        if (!participants.contains(player.getUniqueId())) return;

        if (isSub(player)) {
            // Respawn au spawn des subs
            if (subSpawn != null) {
                event.setRespawnLocation(subSpawn);
            }

            // Redonner le stuff après le respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.RUNNING && participants.contains(player.getUniqueId())) {
                    giveStuff(player);
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                }
            }, 1L);
        }
    }

    // ==================== YouTube Triggers ====================

    /**
     * Donne une flèche à tous les participants (appelé par GameManager sur like)
     */
    public void giveLikeArrow() {
        if (state != GameState.RUNNING) return;

        ItemStack arrow = new ItemStack(Material.ARROW, 1);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(arrow);
            }
        }
    }

    // ==================== Config Helpers ====================

    private void saveStreamerToConfig() {
        if (streamerId != null) {
            config.set("tntlive.streamer-uuid", streamerId.toString());
            saveConfig();
        }
    }

    private void saveSpawnToConfig(String key, Location loc) {
        config.set("tntlive." + key + ".x", loc.getX());
        config.set("tntlive." + key + ".y", loc.getY());
        config.set("tntlive." + key + ".z", loc.getZ());
        config.set("tntlive." + key + ".yaw", loc.getYaw());
        config.set("tntlive." + key + ".pitch", loc.getPitch());
        saveConfig();
    }

    private void saveStuffToConfig(String key, ItemStack[] inventory, ItemStack[] armor) {
        // Sauvegarder l'inventaire
        config.set("tntlive." + key + ".inventory", null);
        if (inventory != null) {
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null) {
                    config.set("tntlive." + key + ".inventory." + i, inventory[i]);
                }
            }
        }

        // Sauvegarder l'armure
        config.set("tntlive." + key + ".armor", null);
        if (armor != null) {
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null) {
                    config.set("tntlive." + key + ".armor." + i, armor[i]);
                }
            }
        }

        saveConfig();
    }

    private ItemStack[] loadItemStacks(YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;

        ItemStack[] items = new ItemStack[41]; // Taille max inventaire
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key);
                if (item != null && slot < items.length) {
                    items[slot] = item;
                }
            } catch (NumberFormatException ignored) {}
        }
        return items;
    }

    // ==================== Getters ====================

    public int getStreamerKills() {
        return streamerKills;
    }

    public int getSubCount() {
        return subTeam.size();
    }

    public Location getStreamerSpawn() {
        return streamerSpawn;
    }

    public Location getSubSpawn() {
        return subSpawn;
    }

    public BlockVector3 getSchematicOrigin() {
        return schematicOrigin;
    }

    public String getSchematicName() {
        return schematicName;
    }

    /**
     * Force le rechargement de la config du schematic depuis le fichier
     */
    public void reloadSchematicConfig() {
        plugin.getLogger().info("[TNTLive] Rechargement de la config schematic...");

        // Recharger le fichier config
        config = YamlConfiguration.loadConfiguration(configFile);

        // Recharger l'origine
        schematicName = config.getString("tntlive.schematic", "tntlive");

        if (config.contains("tntlive.schematic-origin")) {
            int x = config.getInt("tntlive.schematic-origin.x", 0);
            int y = config.getInt("tntlive.schematic-origin.y", 64);
            int z = config.getInt("tntlive.schematic-origin.z", 0);
            schematicOrigin = BlockVector3.at(x, y, z);
            plugin.getLogger().info("[TNTLive] Origine rechargée: " + schematicOrigin);
        } else {
            plugin.getLogger().warning("[TNTLive] Pas d'origine dans la config après reload!");

            // Essayer de charger directement sans le check contains()
            int x = config.getInt("tntlive.schematic-origin.x", -1);
            int y = config.getInt("tntlive.schematic-origin.y", -1);
            int z = config.getInt("tntlive.schematic-origin.z", -1);
            plugin.getLogger().info("[TNTLive] Valeurs directes: x=" + x + ", y=" + y + ", z=" + z);

            if (x != -1 && y != -1 && z != -1) {
                schematicOrigin = BlockVector3.at(x, y, z);
                plugin.getLogger().info("[TNTLive] Origine chargée via valeurs directes: " + schematicOrigin);
            }
        }
    }
}
