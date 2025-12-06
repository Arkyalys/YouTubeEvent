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
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;

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

    // TNT Custom - Map de TNT entity UUID -> explosion power
    private final Map<UUID, Float> customTntPower = new HashMap<>();

    // Milestones déjà atteints (pour ne pas répéter)
    private final Set<Integer> reachedLikeMilestones = new HashSet<>();
    private final Set<Integer> reachedKillMilestones = new HashSet<>();

    // Effets permanents pour les subs (donnés par kill milestones)
    private final List<PotionEffect> permanentSubEffects = new ArrayList<>();

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
    private int totalLikes = 0;
    private int totalArrowsGiven = 0;

    public TNTLiveGame(YouTubeEventPlugin plugin) {
        super(plugin, "tntlive");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void loadEventConfig(YamlConfiguration config) {
        // Charger le nom du schematic
        schematicName = config.getString("tntlive.schematic", "tntlive");

        // Charger l'origine du schematic
        if (config.contains("tntlive.schematic-origin")) {
            int x = config.getInt("tntlive.schematic-origin.x", 0);
            int y = config.getInt("tntlive.schematic-origin.y", 64);
            int z = config.getInt("tntlive.schematic-origin.z", 0);
            schematicOrigin = BlockVector3.at(x, y, z);
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
                plugin.getLogger().info("[TNTLive] Spawn streamer chargé: " + locationToString(streamerSpawn));
            } else {
                plugin.getLogger().warning("[TNTLive] Monde '" + worldName + "' non chargé, spawn streamer sera chargé plus tard");
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
                plugin.getLogger().info("[TNTLive] Spawn sub chargé: " + locationToString(subSpawn));
            } else {
                // Sauvegarder les coords pour chargement différé
                plugin.getLogger().warning("[TNTLive] Monde '" + worldName + "' non chargé, spawn sub sera chargé plus tard");
            }
        }

        // Charger le stuff streamer
        if (config.contains("tntlive.streamer-stuff")) {
            streamerStuff = loadItemStacks(config, "tntlive.streamer-stuff.inventory");
            streamerArmor = loadItemStacks(config, "tntlive.streamer-stuff.armor");
            plugin.getLogger().info("[TNTLive] Stuff streamer chargé: " + (streamerStuff != null ? countItems(streamerStuff) + " items" : "NULL"));
        } else {
            plugin.getLogger().warning("[TNTLive] Pas de section tntlive.streamer-stuff dans la config!");
        }

        // Charger le stuff sub
        if (config.contains("tntlive.sub-stuff")) {
            subStuff = loadItemStacks(config, "tntlive.sub-stuff.inventory");
            subArmor = loadItemStacks(config, "tntlive.sub-stuff.armor");
            plugin.getLogger().info("[TNTLive] Stuff sub chargé: " + (subStuff != null ? countItems(subStuff) + " items" : "NULL"));
        } else {
            plugin.getLogger().warning("[TNTLive] Pas de section tntlive.sub-stuff dans la config!");
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

        // ==================== SETTINGS ====================

        // Auto-streamer (pseudo du streamer par défaut si connecté)
        config.set("tntlive.settings.auto-streamer", "Raynise");

        // Nom de la région WorldGuard pour les barrières
        config.set("tntlive.settings.barrier-region", "barrier");

        // Délais (en secondes)
        config.set("tntlive.settings.schematic-reset-delay", 60);  // Délai avant paste schematic
        config.set("tntlive.settings.win-delay", 2);               // Délai avant fin après mort streamer

        // Quantité de flèches par like
        config.set("tntlive.settings.arrows-per-like", 1);

        // ==================== MESSAGES ====================

        // Messages de like
        config.set("tntlive.messages.like.title", "&c&l❤ &f&lLIKE &c&l❤");
        config.set("tntlive.messages.like.subtitle", "&a+%amount% &7flèche(s)");
        config.set("tntlive.messages.like.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");

        // Messages de victoire (subs gagnent)
        config.set("tntlive.messages.win.broadcast1", "&c&l[TNTLive] &fLe streamer &c%streamer% &fa été éliminé!");
        config.set("tntlive.messages.win.broadcast2", "&a&l[TNTLive] &fLes SUBS gagnent! &7(%kills% kills par le streamer)");
        config.set("tntlive.messages.win.title", "&a&lLES SUBS GAGNENT!");
        config.set("tntlive.messages.win.subtitle", "&7Le streamer a été éliminé!");
        config.set("tntlive.messages.win.sound", "UI_TOAST_CHALLENGE_COMPLETE");

        // Messages de victoire (streamer déco)
        config.set("tntlive.messages.forfeit.broadcast1", "&c&l[TNTLive] &fLe streamer &c%streamer% &fs'est déconnecté!");
        config.set("tntlive.messages.forfeit.broadcast2", "&a&l[TNTLive] &fLes SUBS gagnent par forfait! &7(%kills% kills par le streamer)");
        config.set("tntlive.messages.forfeit.title", "&a&lLES SUBS GAGNENT!");
        config.set("tntlive.messages.forfeit.subtitle", "&7Le streamer a abandonné!");

        // Messages de barrières
        config.set("tntlive.messages.barriers-open", "&c&l[TNTLive] &fLes barrières sont ouvertes! &cGO!");

        // Messages de début
        config.set("tntlive.messages.start.announce1", "&c&l[TNTLive] &f%streamer% &cvs &f%subs% &csubs!");
        config.set("tntlive.messages.start.announce2", "&7Le streamer doit survivre! Les subs respawn infiniment.");

        // Messages de kill
        config.set("tntlive.messages.kill", "&7[TNTLive] &f%player% &7éliminé! &8(Kills: %kills%)");
        config.set("tntlive.messages.kill-milestone", "&6&l[TNTLive] &e%kills% kills! &7Les subs reçoivent un buff!");

        // ==================== TITLES ====================

        // Durées des titles (en ticks: 20 = 1 seconde)
        config.set("tntlive.titles.like.fade-in", 5);
        config.set("tntlive.titles.like.stay", 25);
        config.set("tntlive.titles.like.fade-out", 10);

        config.set("tntlive.titles.win.fade-in", 10);
        config.set("tntlive.titles.win.stay", 70);
        config.set("tntlive.titles.win.fade-out", 20);

        config.set("tntlive.titles.milestone.fade-in", 10);
        config.set("tntlive.titles.milestone.stay", 40);
        config.set("tntlive.titles.milestone.fade-out", 10);

        // ==================== TNT CUSTOM ====================

        // Puissance d'explosion
        config.set("tntlive.custom-tnt.mega.power", 8.0);
        config.set("tntlive.custom-tnt.mega.name", "&c&lMEGA TNT");
        config.set("tntlive.custom-tnt.nuke.power", 15.0);
        config.set("tntlive.custom-tnt.nuke.name", "&4&lNUKE");
        config.set("tntlive.custom-tnt.mini.power", 2.0);
        config.set("tntlive.custom-tnt.mini.name", "&e&lMINI TNT");

        // Chances de transformation aléatoire des TNT normales (en %)
        config.set("tntlive.random-tnt.nuke-chance", 2.0);
        config.set("tntlive.random-tnt.mega-chance", 8.0);
        config.set("tntlive.random-tnt.mini-chance", 15.0);

        // Messages de transformation
        config.set("tntlive.random-tnt.messages.nuke", "&4&l★ &cTa TNT s'est transformée en &4&lNUKE&c!");
        config.set("tntlive.random-tnt.messages.mega", "&6&l★ &eTa TNT s'est transformée en &c&lMEGA TNT&e!");
        config.set("tntlive.random-tnt.messages.mini", "&e&l★ &7Ta TNT s'est transformée en &e&lMINI TNT&7!");

        // Sons de transformation
        config.set("tntlive.random-tnt.sounds.nuke", "ENTITY_WITHER_SPAWN");
        config.set("tntlive.random-tnt.sounds.mega", "ENTITY_PLAYER_LEVELUP");
        config.set("tntlive.random-tnt.sounds.mini", "BLOCK_NOTE_BLOCK_PLING");

        // ==================== MILESTONES ====================

        // Milestones de likes
        config.set("tntlive.like-milestones.10.team", "sub");
        config.set("tntlive.like-milestones.10.item", "mega_tnt");
        config.set("tntlive.like-milestones.10.title", "&c&l10 LIKES!");
        config.set("tntlive.like-milestones.10.subtitle", "&eMEGA TNT pour les subs!");
        config.set("tntlive.like-milestones.10.sound", "ENTITY_PLAYER_LEVELUP");

        config.set("tntlive.like-milestones.15.team", "sub");
        config.set("tntlive.like-milestones.15.item", "nuke");
        config.set("tntlive.like-milestones.15.title", "&4&l15 LIKES!");
        config.set("tntlive.like-milestones.15.subtitle", "&c&lNUKE pour les subs!");
        config.set("tntlive.like-milestones.15.sound", "ENTITY_PLAYER_LEVELUP");

        config.set("tntlive.like-milestones.25.team", "sub");
        config.set("tntlive.like-milestones.25.item", "nuke");
        config.set("tntlive.like-milestones.25.title", "&4&l25 LIKES!");
        config.set("tntlive.like-milestones.25.subtitle", "&c&lNUKE BONUS!");
        config.set("tntlive.like-milestones.25.sound", "ENTITY_PLAYER_LEVELUP");

        // Milestones de kills
        config.set("tntlive.kill-milestones.10.effects", List.of("speed:2"));
        config.set("tntlive.kill-milestones.10.title", "&6&l10 KILLS!");
        config.set("tntlive.kill-milestones.10.subtitle", "&eLes subs reçoivent &fSpeed II &epermanent!");
        config.set("tntlive.kill-milestones.10.sound", "ENTITY_ZOMBIE_VILLAGER_CURE");

        config.set("tntlive.kill-milestones.15.effects", List.of("strength:1"));
        config.set("tntlive.kill-milestones.15.title", "&c&l15 KILLS!");
        config.set("tntlive.kill-milestones.15.subtitle", "&eLes subs reçoivent &fStrength I &epermanent!");
        config.set("tntlive.kill-milestones.15.sound", "ENTITY_ZOMBIE_VILLAGER_CURE");

        config.set("tntlive.kill-milestones.20.effects", List.of("resistance:1"));
        config.set("tntlive.kill-milestones.20.title", "&4&l20 KILLS!");
        config.set("tntlive.kill-milestones.20.subtitle", "&eLes subs reçoivent &fResistance I &epermanent!");
        config.set("tntlive.kill-milestones.20.sound", "ENTITY_ZOMBIE_VILLAGER_CURE");

        // ==================== REWARDS ====================

        config.set("rewards.win-commands", List.of(
                "broadcast &a&lLes SUBS ont gagné TNTLive! &7(%streamer_kills% kills par le streamer)"
        ));
    }

    // ==================== Helpers Config ====================

    /**
     * Récupère un message de la config et remplace les placeholders
     */
    private String msg(String path) {
        return config.getString("tntlive.messages." + path, "").replace("&", "§");
    }

    /**
     * Récupère un son de la config
     */
    private Sound getSound(String path) {
        String soundName = config.getString(path, "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[TNTLive] Son inconnu: " + soundName);
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }

    /**
     * Récupère un setting de la config
     */
    private String setting(String key, String defaultValue) {
        return config.getString("tntlive.settings." + key, defaultValue);
    }

    private int settingInt(String key, int defaultValue) {
        return config.getInt("tntlive.settings." + key, defaultValue);
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

    private int countItems(ItemStack[] items) {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) count++;
        }
        return count;
    }

    /**
     * Supprime tous les blocs BARRIER dans une région WorldGuard
     */
    private void removeBarriersInRegion(String regionName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[TNTLive] Monde " + worldName + " introuvable pour supprimer les barriers!");
            return;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                plugin.getLogger().warning("[TNTLive] RegionManager null pour le monde " + worldName);
                return;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                plugin.getLogger().warning("[TNTLive] Région '" + regionName + "' introuvable!");
                return;
            }

            // Récupérer les limites de la région
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            int removed = 0;

            // Parcourir tous les blocs de la région
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Location loc = new Location(world, x, y, z);
                        if (loc.getBlock().getType() == Material.BARRIER) {
                            loc.getBlock().setType(Material.AIR);
                            removed++;
                        }
                    }
                }
            }

            if (removed > 0) {
                plugin.getLogger().info("[TNTLive] " + removed + " barriers supprimés dans la région '" + regionName + "'");
                Bukkit.broadcastMessage(msg("barriers-open"));
            } else {
                plugin.getLogger().info("[TNTLive] Aucun barrier trouvé dans la région '" + regionName + "'");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("[TNTLive] Erreur lors de la suppression des barriers: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Définit le flag invincible sur la région "barrier"
     * @param allow true = joueurs invincibles, false = joueurs peuvent prendre des dégâts
     */
    private void setBarrierInvincible(boolean allow) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[TNTLive] Monde " + worldName + " introuvable pour set invincible flag!");
            return;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                plugin.getLogger().warning("[TNTLive] RegionManager null pour le monde " + worldName);
                return;
            }

            String barrierRegion = setting("barrier-region", "barrier");
            ProtectedRegion region = regionManager.getRegion(barrierRegion);
            if (region == null) {
                plugin.getLogger().warning("[TNTLive] Région '" + barrierRegion + "' introuvable pour set invincible!");
                return;
            }

            // Définir le flag invincible
            StateFlag.State state = allow ? StateFlag.State.ALLOW : StateFlag.State.DENY;
            region.setFlag(Flags.INVINCIBILITY, state);

            plugin.getLogger().info("[TNTLive] Flag invincible sur région 'barrier': " + (allow ? "ALLOW" : "DENY"));

        } catch (Exception e) {
            plugin.getLogger().severe("[TNTLive] Erreur lors du set invincible flag: " + e.getMessage());
            e.printStackTrace();
        }
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

        plugin.getLogger().info("[TNTLive] giveStuff() pour " + player.getName() + " - isStreamer: " + isStreamer(player));

        if (isStreamer(player)) {
            plugin.getLogger().info("[TNTLive] Donner stuff STREAMER - streamerStuff null? " + (streamerStuff == null));
            if (streamerStuff != null) {
                player.getInventory().setContents(streamerStuff.clone());
                plugin.getLogger().info("[TNTLive] Stuff streamer donné!");
            }
            if (streamerArmor != null) {
                player.getInventory().setArmorContents(extractArmor(streamerArmor));
            }
        } else {
            plugin.getLogger().info("[TNTLive] Donner stuff SUB - subStuff null? " + (subStuff == null));
            if (subStuff != null) {
                player.getInventory().setContents(subStuff.clone());
                plugin.getLogger().info("[TNTLive] Stuff sub donné!");
            } else {
                plugin.getLogger().warning("[TNTLive] subStuff est NULL! Vérifiez la config tntlive.yml");
            }
            if (subArmor != null) {
                player.getInventory().setArmorContents(extractArmor(subArmor));
            }
        }
    }

    /**
     * Extrait les 4 premiers éléments d'un tableau pour l'armure
     * (setArmorContents nécessite exactement 4 éléments)
     */
    private ItemStack[] extractArmor(ItemStack[] source) {
        ItemStack[] armor = new ItemStack[4];
        if (source != null) {
            for (int i = 0; i < Math.min(4, source.length); i++) {
                armor[i] = source[i];
            }
        }
        return armor;
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
     * Paste le schematic pour reset la map
     */
    public void pasteSchematic() {
        if (schematicOrigin == null) {
            plugin.getLogger().warning("[TNTLive] Origine du schematic non définie!");
            return;
        }

        File schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
            plugin.getLogger().warning("[TNTLive] Dossier schematics créé.");
            return;
        }

        File schematicFile = findSchematicFile(schematicsFolder, schematicName);
        if (schematicFile == null) {
            plugin.getLogger().warning("[TNTLive] Schematic '" + schematicName + "' introuvable!");
            return;
        }

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            plugin.getLogger().warning("[TNTLive] Monde '" + worldName + "' introuvable!");
            return;
        }

        final File finalSchematicFile = schematicFile;
        final BlockVector3 pasteLocation = schematicOrigin;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(finalSchematicFile);
                if (format == null) return;

                Clipboard clipboard;
                try (FileInputStream fis = new FileInputStream(finalSchematicFile);
                     ClipboardReader reader = format.getReader(fis)) {
                    clipboard = reader.read();
                }

                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(pasteLocation)
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                    plugin.getLogger().info("[TNTLive] Schematic collé (" + editSession.getBlockChangeCount() + " blocs)");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[TNTLive] Erreur paste: " + e.getMessage());
            }
        });
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
        totalLikes = 0;
        totalArrowsGiven = 0;

        // Reset des milestones et effets permanents
        reachedLikeMilestones.clear();
        reachedKillMilestones.clear();
        permanentSubEffects.clear();
        customTntPower.clear();
        pendingCustomTnt.clear();

        // Reset des hologrammes TNT (au cas où il en reste)
        removeAllTntHolograms();
        holoCounter = 0;

        // Recharger les spawns si nécessaire (monde chargé maintenant)
        reloadSpawnsIfNeeded();

        // Recharger le stuff si nécessaire (corrige le bug où stuff devient null après restart)
        reloadStuffIfNeeded();

        // Activer l'invincibilité dans la zone d'attente (barrier)
        setBarrierInvincible(true);

        // Auto-set le streamer par défaut si aucun streamer défini et qu'il est connecté
        if (streamerId == null) {
            String autoStreamer = setting("auto-streamer", "Raynise");
            Player defaultStreamer = Bukkit.getPlayerExact(autoStreamer);
            if (defaultStreamer != null && defaultStreamer.isOnline()) {
                setStreamer(defaultStreamer);
                plugin.getLogger().info("[TNTLive] " + autoStreamer + " détecté en ligne, auto-défini comme streamer!");
            } else {
                plugin.getLogger().warning("Aucun streamer défini! Utilisez /event setstreamer <joueur>");
            }
        }

        plugin.getLogger().info("Event TNTLive ouvert!");

        // Log l'état des spawns
        if (streamerSpawn != null) {
            plugin.getLogger().info("[TNTLive] Spawn streamer: " + locationToString(streamerSpawn));
        } else {
            plugin.getLogger().warning("[TNTLive] Spawn streamer NON DÉFINI!");
        }
        if (subSpawn != null) {
            plugin.getLogger().info("[TNTLive] Spawn sub: " + locationToString(subSpawn));
        } else {
            plugin.getLogger().warning("[TNTLive] Spawn sub NON DÉFINI!");
        }
    }

    /**
     * Recharge les spawns depuis la config si le monde est maintenant disponible
     */
    private void reloadSpawnsIfNeeded() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[TNTLive] Monde '" + worldName + "' toujours non chargé!");
            return;
        }

        // Recharger spawn streamer si null mais présent dans config
        if (streamerSpawn == null && config.contains("tntlive.streamer-spawn")) {
            double x = config.getDouble("tntlive.streamer-spawn.x");
            double y = config.getDouble("tntlive.streamer-spawn.y");
            double z = config.getDouble("tntlive.streamer-spawn.z");
            float yaw = (float) config.getDouble("tntlive.streamer-spawn.yaw", 0);
            float pitch = (float) config.getDouble("tntlive.streamer-spawn.pitch", 0);
            streamerSpawn = new Location(world, x, y, z, yaw, pitch);
            plugin.getLogger().info("[TNTLive] Spawn streamer rechargé: " + locationToString(streamerSpawn));
        }

        // Recharger spawn sub si null mais présent dans config
        if (subSpawn == null && config.contains("tntlive.sub-spawn")) {
            double x = config.getDouble("tntlive.sub-spawn.x");
            double y = config.getDouble("tntlive.sub-spawn.y");
            double z = config.getDouble("tntlive.sub-spawn.z");
            float yaw = (float) config.getDouble("tntlive.sub-spawn.yaw", 0);
            float pitch = (float) config.getDouble("tntlive.sub-spawn.pitch", 0);
            subSpawn = new Location(world, x, y, z, yaw, pitch);
            plugin.getLogger().info("[TNTLive] Spawn sub rechargé: " + locationToString(subSpawn));
        }
    }

    /**
     * Recharge le stuff depuis la config si null (au cas où le chargement initial a échoué)
     */
    private void reloadStuffIfNeeded() {
        plugin.getLogger().info("[TNTLive] reloadStuffIfNeeded() - subStuff null? " + (subStuff == null) +
                               ", streamerStuff null? " + (streamerStuff == null));

        // Recharger la config depuis le fichier pour s'assurer d'avoir les dernières données
        config = YamlConfiguration.loadConfiguration(configFile);

        // Recharger le stuff sub si null mais présent dans config
        if (subStuff == null && config.contains("tntlive.sub-stuff.inventory")) {
            subStuff = loadItemStacks(config, "tntlive.sub-stuff.inventory");
            subArmor = loadItemStacks(config, "tntlive.sub-stuff.armor");
            plugin.getLogger().info("[TNTLive] Stuff sub rechargé: " + (subStuff != null ? countItems(subStuff) + " items" : "TOUJOURS NULL!"));
        }

        // Recharger le stuff streamer si null mais présent dans config
        if (streamerStuff == null && config.contains("tntlive.streamer-stuff.inventory")) {
            streamerStuff = loadItemStacks(config, "tntlive.streamer-stuff.inventory");
            streamerArmor = loadItemStacks(config, "tntlive.streamer-stuff.armor");
            plugin.getLogger().info("[TNTLive] Stuff streamer rechargé: " + (streamerStuff != null ? countItems(streamerStuff) + " items" : "TOUJOURS NULL!"));
        }

        // Si toujours null, afficher un warning avec le chemin de la config
        if (subStuff == null) {
            plugin.getLogger().warning("[TNTLive] ATTENTION: subStuff toujours null après rechargement!");
            plugin.getLogger().warning("[TNTLive] Config file: " + configFile.getAbsolutePath());
            plugin.getLogger().warning("[TNTLive] Config contient tntlive.sub-stuff? " + config.contains("tntlive.sub-stuff"));
            plugin.getLogger().warning("[TNTLive] Config contient tntlive.sub-stuff.inventory? " + config.contains("tntlive.sub-stuff.inventory"));
        }
    }

    @Override
    protected void onBegin() {
        // Vérifier qu'on a un streamer AVANT de modifier quoi que ce soit
        Player streamer = getStreamer();
        if (streamer == null || !participants.contains(streamerId)) {
            plugin.getLogger().warning("Le streamer n'est pas dans l'event!");
            Bukkit.broadcastMessage("§c[TNTLive] Le streamer n'est pas présent! Event annulé.");
            return;
        }

        // Désactiver l'invincibilité (le jeu commence!)
        setBarrierInvincible(false);

        // Supprimer les barriers de la région configurée
        removeBarriersInRegion(setting("barrier-region", "barrier"));

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

                // Reset l'état du joueur (invulnérable, gamemode, etc.)
                resetPlayerState(player);

                // Donner le stuff
                giveStuff(player);
            }
        }

        // Annonce
        Player streamerPlayer = getStreamer();
        String streamerName = streamerPlayer != null ? streamerPlayer.getName() : "???";
        Bukkit.broadcastMessage(msg("start.announce1")
                .replace("%streamer%", streamerName)
                .replace("%subs%", String.valueOf(subTeam.size())));
        Bukkit.broadcastMessage(msg("start.announce2"));

        plugin.getLogger().info("Event TNTLive commencé! Streamer: " + streamerName + ", Subs: " + subTeam.size());
    }

    @Override
    protected void onStop() {
        // Réactiver l'invincibilité dans la zone barrier (pour le prochain event)
        setBarrierInvincible(true);

        // Supprimer tous les hologrammes de TNT
        removeAllTntHolograms();

        // Nettoyer les entités au sol
        cleanupEntities();

        // Paste le schematic après délai configurable
        int resetDelay = settingInt("schematic-reset-delay", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pasteSchematic();
            plugin.getLogger().info("[TNTLive] Map reset (" + resetDelay + "s après la fin)");
        }, 20L * resetDelay);

        plugin.getLogger().info("Event TNTLive arrêté! Map reset dans " + resetDelay + " secondes.");
    }

    /**
     * Supprime toutes les entités au sol (items, flèches, etc.) dans le monde TNTLive
     */
    private void cleanupEntities() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[TNTLive] Monde " + worldName + " non trouvé pour cleanup");
            return;
        }

        int removed = 0;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            // Supprimer les items au sol
            if (entity instanceof org.bukkit.entity.Item) {
                entity.remove();
                removed++;
            }
            // Supprimer les flèches
            else if (entity instanceof org.bukkit.entity.Arrow) {
                entity.remove();
                removed++;
            }
            // Supprimer les items dans des frames (optionnel)
            else if (entity instanceof org.bukkit.entity.ExperienceOrb) {
                entity.remove();
                removed++;
            }
            // Supprimer les TNT amorcées
            else if (entity instanceof org.bukkit.entity.TNTPrimed) {
                entity.remove();
                removed++;
            }
            // Supprimer les falling blocks
            else if (entity instanceof org.bukkit.entity.FallingBlock) {
                entity.remove();
                removed++;
            }
        }

        plugin.getLogger().info("[TNTLive] " + removed + " entités supprimées dans " + worldName);
    }

    /**
     * Termine l'event proprement
     * Note: stop() (parent) s'occupe du TP + cleanup, onStop() fait le paste après 1 min
     */
    private void endEvent() {
        plugin.getLogger().info("[TNTLive] endEvent() - fin de l'event");
        // stop() fait: onStop() + TP des participants + reset state
        stop();
    }

    /**
     * Exécute les commandes de victoire pour tous les participants
     */
    private void executeWinCommands() {
        List<String> winCommands = config.getStringList("rewards.win-commands");
        if (winCommands.isEmpty()) {
            plugin.getLogger().info("[TNTLive] Pas de win-commands configurées");
            return;
        }

        plugin.getLogger().info("[TNTLive] Exécution de " + winCommands.size() + " win-commands");

        for (String cmd : winCommands) {
            // Remplacer les placeholders
            String finalCmd = cmd
                    .replace("%event%", getName())
                    .replace("%streamer_kills%", String.valueOf(streamerKills));

            // Si la commande contient %player%, l'exécuter pour chaque participant
            if (finalCmd.contains("%player%")) {
                for (UUID uuid : participants) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        String playerCmd = finalCmd.replace("%player%", p.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), playerCmd);
                        plugin.getLogger().info("[TNTLive] Exécuté: " + playerCmd);
                    }
                }
            } else {
                // Commande globale (broadcast, etc.)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                plugin.getLogger().info("[TNTLive] Exécuté: " + finalCmd);
            }
        }
    }

    /**
     * TNTLive permet de rejoindre même quand l'event est en cours
     */
    @Override
    protected boolean canJoinWhileRunning() {
        return true;
    }

    @Override
    protected void onPlayerJoin(Player player) {
        player.sendMessage("§a[TNTLive] §7Vous avez rejoint l'event!");

        if (isStreamer(player)) {
            player.sendMessage("§6§lVous êtes le STREAMER! §7Survivez le plus longtemps possible!");
            // Téléporter au spawn streamer si défini
            if (streamerSpawn != null) {
                player.teleport(streamerSpawn);
            }
        } else {
            player.sendMessage("§c§lVous êtes un SUB! §7Éliminez le streamer!");

            // Si l'event est en cours, ajouter à l'équipe sub
            if (state == GameState.RUNNING) {
                subTeam.add(player.getUniqueId());
            }

            // Téléporter au spawn sub si défini
            if (subSpawn != null) {
                player.teleport(subSpawn);
            } else {
                plugin.getLogger().warning("[TNTLive] subSpawn est null! Utilisez /event setspawn tntlive sub");
            }
        }

        // Reset l'état du joueur (fix invulnérable, gamemode, etc.)
        resetPlayerState(player);

        // Donner le stuff immédiatement au join
        giveStuff(player);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    /**
     * Reset l'état du joueur pour qu'il soit prêt à jouer
     * Note: Ne touche PAS aux effets de potion (ils persistent)
     */
    private void resetPlayerState(Player player) {
        // Mettre en survie
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Enlever l'invulnérabilité
        player.setInvulnerable(false);

        // Enlever le fly
        player.setAllowFlight(false);
        player.setFlying(false);

        // Reset autres effets négatifs
        player.setFireTicks(0);
        player.setFreezeTicks(0);

        // Heal et feed
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // On NE supprime PAS les effets de potion - ils persistent (milestones, /event effect, etc.)

        plugin.getLogger().info("[TNTLive] État de " + player.getName() + " réinitialisé (invulnerable: false, survival)");
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
        // TNTLive gère la victoire différemment (via onPlayerDeath/onPlayerQuit)
        // Cette méthode n'est pas appelée car on utilise endEvent() directement
        // Mais on doit l'implémenter car elle est abstract dans GameEvent
    }

    // ==================== PvP & Death Handling ====================

    /**
     * Bloque le PvP entre subs (même équipe)
     * Autorise: Sub -> Streamer, Streamer -> Sub
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (state != GameState.RUNNING) return;

        // Vérifier si c'est un joueur qui attaque un joueur
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        // Gérer les dégâts directs et par projectile
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) return;

        // Vérifier si les deux sont participants
        if (!participants.contains(attacker.getUniqueId()) || !participants.contains(victim.getUniqueId())) {
            return;
        }

        // Si les deux sont des subs = bloquer le PvP (friendly fire)
        if (isSub(attacker) && isSub(victim)) {
            event.setCancelled(true);
            // Message discret (pas de spam)
            // attacker.sendMessage("§c§l[TNTLive] §7Vous ne pouvez pas attaquer vos coéquipiers!");
        }
        // Sinon: Sub vs Streamer ou Streamer vs Sub = autorisé
    }

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

            int winDelay = settingInt("win-delay", 2);

            // Scheduler pour après le respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Annoncer la victoire des subs
                Bukkit.broadcastMessage(msg("win.broadcast1")
                        .replace("%streamer%", player.getName()));
                Bukkit.broadcastMessage(msg("win.broadcast2")
                        .replace("%kills%", String.valueOf(streamerKills)));

                // Title pour tous les joueurs
                String title = msg("win.title");
                String subtitle = msg("win.subtitle");
                Sound sound = getSound("tntlive.messages.win.sound");
                int fadeIn = config.getInt("tntlive.titles.win.fade-in", 10);
                int stay = config.getInt("tntlive.titles.win.stay", 70);
                int fadeOut = config.getInt("tntlive.titles.win.fade-out", 20);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    p.playSound(p.getLocation(), sound, 1f, 1f);
                }

                // Exécuter les commandes de victoire
                executeWinCommands();

                // Fin de l'event (TP + reset map)
                endEvent();
            }, winDelay * 20L);

        } else {
            // Un sub est mort - il va respawn
            streamerKills++;

            // Message
            Player killer = player.getKiller();
            if (killer != null && isStreamer(killer)) {
                Bukkit.broadcastMessage(msg("kill")
                        .replace("%player%", player.getName())
                        .replace("%kills%", String.valueOf(streamerKills)));
            }

            // Vérifier les milestones de kills
            checkKillMilestones();
        }
    }

    /**
     * Gère la déconnexion du streamer = victoire des subs (comme si le streamer était mort)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (state != GameState.RUNNING) return;

        Player player = event.getPlayer();
        if (isStreamer(player)) {
            // Le streamer s'est déconnecté = victoire des subs!
            plugin.getLogger().info("Le streamer " + player.getName() + " s'est déconnecté! Fin de l'event.");

            Bukkit.broadcastMessage(msg("forfeit.broadcast1")
                    .replace("%streamer%", player.getName()));
            Bukkit.broadcastMessage(msg("forfeit.broadcast2")
                    .replace("%kills%", String.valueOf(streamerKills)));

            // Title pour tous les joueurs
            String title = msg("forfeit.title");
            String subtitle = msg("forfeit.subtitle");
            Sound sound = getSound("tntlive.messages.win.sound"); // Même son que victoire
            int fadeIn = config.getInt("tntlive.titles.win.fade-in", 10);
            int stay = config.getInt("tntlive.titles.win.stay", 70);
            int fadeOut = config.getInt("tntlive.titles.win.fade-out", 20);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                p.playSound(p.getLocation(), sound, 1f, 1f);
            }

            // Exécuter les commandes de victoire + TP + stop
            int winDelay = settingInt("win-delay", 2);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                executeWinCommands();
                endEvent();
            }, winDelay * 20L);
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
                    resetPlayerState(player);
                    giveStuff(player);
                    // Appliquer les effets permanents des subs
                    applyPermanentEffects(player);
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

        // Incrémenter le compteur de likes
        totalLikes++;

        int arrowAmount = settingInt("arrows-per-like", 1);
        ItemStack arrow = new ItemStack(Material.ARROW, arrowAmount);

        // Config title
        String title = msg("like.title");
        String subtitle = msg("like.subtitle").replace("%amount%", String.valueOf(arrowAmount));
        Sound sound = getSound("tntlive.messages.like.sound");
        int fadeIn = config.getInt("tntlive.titles.like.fade-in", 5);
        int stay = config.getInt("tntlive.titles.like.stay", 25);
        int fadeOut = config.getInt("tntlive.titles.like.fade-out", 10);

        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(arrow);
                totalArrowsGiven += arrowAmount;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                player.playSound(player.getLocation(), sound, 0.7f, 1.2f);
            }
        }
    }

    /**
     * Appelé par GameManager quand le total de likes change
     * Vérifie les milestones et donne les récompenses
     */
    public void checkLikeMilestones(long totalLikes) {
        if (state != GameState.RUNNING) return;

        ConfigurationSection milestones = config.getConfigurationSection("tntlive.like-milestones");
        if (milestones == null) return;

        for (String key : milestones.getKeys(false)) {
            try {
                int milestone = Integer.parseInt(key);
                if (totalLikes >= milestone && !reachedLikeMilestones.contains(milestone)) {
                    reachedLikeMilestones.add(milestone);

                    // Récupérer la config de ce milestone
                    ConfigurationSection ms = milestones.getConfigurationSection(key);
                    if (ms == null) continue;

                    String team = ms.getString("team", "sub");
                    String itemType = ms.getString("item", "");
                    String title = ms.getString("title", "");
                    String subtitle = ms.getString("subtitle", "");

                    plugin.getLogger().info("[TNTLive] Milestone " + milestone + " likes atteint!");

                    // Donner l'item
                    if (!itemType.isEmpty()) {
                        giveTeamItem(team, itemType, 1);
                    }

                    // Afficher le title
                    if (!title.isEmpty()) {
                        String soundName = ms.getString("sound", "ENTITY_PLAYER_LEVELUP");
                        Sound sound = getSound("tntlive.like-milestones." + key + ".sound");
                        int fadeIn = config.getInt("tntlive.titles.milestone.fade-in", 10);
                        int stay = config.getInt("tntlive.titles.milestone.stay", 40);
                        int fadeOut = config.getInt("tntlive.titles.milestone.fade-out", 10);

                        for (UUID uuid : participants) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.sendTitle(
                                    title.replace("&", "§"),
                                    subtitle.replace("&", "§"),
                                    fadeIn, stay, fadeOut
                                );
                                p.playSound(p.getLocation(), sound, 1f, 1f);
                            }
                        }
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Vérifie les milestones de kills du streamer
     */
    private void checkKillMilestones() {
        ConfigurationSection milestones = config.getConfigurationSection("tntlive.kill-milestones");
        if (milestones == null) return;

        for (String key : milestones.getKeys(false)) {
            try {
                int milestone = Integer.parseInt(key);
                if (streamerKills >= milestone && !reachedKillMilestones.contains(milestone)) {
                    reachedKillMilestones.add(milestone);

                    ConfigurationSection ms = milestones.getConfigurationSection(key);
                    if (ms == null) continue;

                    String title = ms.getString("title", "");
                    String subtitle = ms.getString("subtitle", "");
                    List<String> effects = ms.getStringList("effects");

                    plugin.getLogger().info("[TNTLive] Milestone " + milestone + " kills atteint!");

                    // Ajouter les effets permanents
                    for (String effectStr : effects) {
                        PotionEffect effect = parseEffect(effectStr);
                        if (effect != null) {
                            permanentSubEffects.add(effect);
                            plugin.getLogger().info("[TNTLive] Effet permanent ajouté: " + effect.getType().getName());
                        }
                    }

                    // Config title
                    Sound sound = getSound("tntlive.kill-milestones." + key + ".sound");
                    int fadeIn = config.getInt("tntlive.titles.milestone.fade-in", 10);
                    int stay = config.getInt("tntlive.titles.milestone.stay", 40);
                    int fadeOut = config.getInt("tntlive.titles.milestone.fade-out", 10);

                    // Appliquer à tous les subs vivants
                    for (UUID uuid : subTeam) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            applyPermanentEffects(p);

                            // Afficher le title
                            if (!title.isEmpty()) {
                                p.sendTitle(
                                    title.replace("&", "§"),
                                    subtitle.replace("&", "§"),
                                    fadeIn, stay, fadeOut
                                );
                            }
                            p.playSound(p.getLocation(), sound, 1f, 1.5f);
                        }
                    }

                    // Annoncer
                    Bukkit.broadcastMessage(msg("kill-milestone")
                            .replace("%kills%", String.valueOf(streamerKills)));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Parse un effet depuis une string (ex: "speed:2" ou "strength:1:30")
     */
    private PotionEffect parseEffect(String effectStr) {
        String[] parts = effectStr.split(":");
        if (parts.length < 2) return null;

        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
        if (type == null) {
            plugin.getLogger().warning("[TNTLive] Effet inconnu: " + parts[0]);
            return null;
        }

        int amplifier = Integer.parseInt(parts[1]) - 1; // -1 car amplifier 0 = niveau 1
        int duration = parts.length > 2 ? Integer.parseInt(parts[2]) * 20 : Integer.MAX_VALUE; // Infini par défaut

        return new PotionEffect(type, duration, amplifier, false, true, true);
    }

    /**
     * Applique les effets permanents à un joueur (sub)
     */
    private void applyPermanentEffects(Player player) {
        if (!isSub(player)) return;

        for (PotionEffect effect : permanentSubEffects) {
            player.addPotionEffect(effect, true);
        }
    }

    /**
     * Donne un item à une équipe
     */
    public void giveTeamItem(String team, String itemName, int amount) {
        ItemStack item = createItem(itemName, amount);
        if (item == null) {
            plugin.getLogger().warning("[TNTLive] Item inconnu: " + itemName);
            return;
        }

        Set<UUID> targets = team.equalsIgnoreCase("streamer") ?
            (streamerId != null ? Set.of(streamerId) : Set.of()) :
            new HashSet<>(subTeam);

        for (UUID uuid : targets) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getInventory().addItem(item.clone());
            }
        }

        plugin.getLogger().info("[TNTLive] " + item.getType() + " x" + amount + " donné à l'équipe " + team);
    }

    /**
     * Donne un effet à une équipe
     */
    public void giveTeamEffect(String team, String effectName, int level, int durationSeconds) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase());
        if (type == null) {
            plugin.getLogger().warning("[TNTLive] Effet inconnu: " + effectName);
            return;
        }

        PotionEffect effect = new PotionEffect(type, durationSeconds * 20, level - 1, false, true, true);

        Set<UUID> targets = team.equalsIgnoreCase("streamer") ?
            (streamerId != null ? Set.of(streamerId) : Set.of()) :
            new HashSet<>(subTeam);

        for (UUID uuid : targets) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.addPotionEffect(effect, true);
            }
        }

        plugin.getLogger().info("[TNTLive] Effet " + effectName + " " + level + " donné à l'équipe " + team);
    }

    /**
     * Crée un item depuis son nom (supporte les TNT custom)
     */
    private ItemStack createItem(String itemName, int amount) {
        // TNT Custom
        if (itemName.equalsIgnoreCase("mega_tnt") || itemName.equalsIgnoreCase("megatnt")) {
            String name = config.getString("tntlive.custom-tnt.mega.name", "&c&lMEGA TNT").replace("&", "§");
            return createCustomTnt(name, config.getDouble("tntlive.custom-tnt.mega.power", 8.0));
        }
        if (itemName.equalsIgnoreCase("nuke") || itemName.equalsIgnoreCase("nuke_tnt")) {
            String name = config.getString("tntlive.custom-tnt.nuke.name", "&4&lNUKE").replace("&", "§");
            return createCustomTnt(name, config.getDouble("tntlive.custom-tnt.nuke.power", 15.0));
        }
        if (itemName.equalsIgnoreCase("mini_tnt") || itemName.equalsIgnoreCase("minitnt")) {
            String name = config.getString("tntlive.custom-tnt.mini.name", "&e&lMINI TNT").replace("&", "§");
            return createCustomTnt(name, config.getDouble("tntlive.custom-tnt.mini.power", 2.0));
        }

        // Item normal
        try {
            Material mat = Material.matchMaterial(itemName.toUpperCase());
            if (mat != null) {
                return new ItemStack(mat, amount);
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Crée une TNT custom avec un nom et une puissance
     */
    public ItemStack createCustomTnt(String displayName, double power) {
        ItemStack tnt = new ItemStack(Material.TNT, 1);
        ItemMeta meta = tnt.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            // Stocker la puissance dans le lore (sera lu quand la TNT explose)
            meta.setLore(Arrays.asList(
                "§7Puissance: §f" + power,
                "§8Power:" + power // Tag caché pour le parsing
            ));
            tnt.setItemMeta(meta);
        }
        return tnt;
    }

    // ==================== TNT Custom Event Handlers ====================

    /**
     * Détecte quand une TNT est placée (custom ou chance aléatoire)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (state != GameState.RUNNING) return;
        if (!event.getBlock().getWorld().getName().equals(worldName)) return;

        if (event.getBlock().getType() == Material.TNT) {
            ItemStack item = event.getItemInHand();
            Location loc = event.getBlock().getLocation();
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

            // Vérifier si c'est une TNT custom (avec lore)
            boolean isCustom = false;
            if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    if (line.startsWith("§8Power:")) {
                        try {
                            double power = Double.parseDouble(line.substring(8));
                            pendingCustomTnt.put(key, (float) power);
                            isCustom = true;

                            // Créer un hologramme pour indiquer la TNT custom
                            String holoName = item.getItemMeta().getDisplayName();
                            createTntHologram(loc, holoName, power);

                            plugin.getLogger().info("[TNTLive] TNT custom placée: power=" + power + " at " + key);
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            }

            // Si c'est une TNT normale, chance de devenir custom !
            if (!isCustom) {
                double roll = random.nextDouble() * 100; // 0-100

                // Chances configurables dans la config
                double chanceNuke = config.getDouble("tntlive.random-tnt.nuke-chance", 2.0);
                double chanceMega = config.getDouble("tntlive.random-tnt.mega-chance", 8.0);
                double chanceMini = config.getDouble("tntlive.random-tnt.mini-chance", 15.0);

                if (roll < chanceNuke) {
                    // NUKE !
                    double power = config.getDouble("tntlive.custom-tnt.nuke.power", 15.0);
                    String name = config.getString("tntlive.custom-tnt.nuke.name", "&4&lNUKE").replace("&", "§");
                    pendingCustomTnt.put(key, (float) power);
                    createTntHologram(loc, name, power);
                    event.getPlayer().sendMessage(config.getString("tntlive.random-tnt.messages.nuke", "").replace("&", "§"));
                    event.getPlayer().playSound(loc, getSound("tntlive.random-tnt.sounds.nuke"), 0.5f, 1.5f);
                    plugin.getLogger().info("[TNTLive] TNT normale -> NUKE! at " + key);

                } else if (roll < chanceNuke + chanceMega) {
                    // MEGA TNT
                    double power = config.getDouble("tntlive.custom-tnt.mega.power", 8.0);
                    String name = config.getString("tntlive.custom-tnt.mega.name", "&c&lMEGA TNT").replace("&", "§");
                    pendingCustomTnt.put(key, (float) power);
                    createTntHologram(loc, name, power);
                    event.getPlayer().sendMessage(config.getString("tntlive.random-tnt.messages.mega", "").replace("&", "§"));
                    event.getPlayer().playSound(loc, getSound("tntlive.random-tnt.sounds.mega"), 0.7f, 1.2f);
                    plugin.getLogger().info("[TNTLive] TNT normale -> MEGA at " + key);

                } else if (roll < chanceNuke + chanceMega + chanceMini) {
                    // MINI TNT
                    double power = config.getDouble("tntlive.custom-tnt.mini.power", 2.0);
                    String name = config.getString("tntlive.custom-tnt.mini.name", "&e&lMINI TNT").replace("&", "§");
                    pendingCustomTnt.put(key, (float) power);
                    createTntHologram(loc, name, power);
                    event.getPlayer().sendMessage(config.getString("tntlive.random-tnt.messages.mini", "").replace("&", "§"));
                    event.getPlayer().playSound(loc, getSound("tntlive.random-tnt.sounds.mini"), 0.7f, 1.5f);
                    plugin.getLogger().info("[TNTLive] TNT normale -> MINI at " + key);
                }
                // Sinon: TNT normale, pas de transformation
            }
        }
    }

    /**
     * Crée un hologramme au-dessus d'une TNT custom
     */
    private void createTntHologram(Location loc, String name, double power) {
        try {
            String holoId = "tntlive_" + (holoCounter++);
            Location holoLoc = loc.clone().add(0.5, 1.5, 0.5); // Au-dessus du bloc

            Hologram holo = DHAPI.createHologram(holoId, holoLoc, false);
            DHAPI.addHologramLine(holo, name);
            DHAPI.addHologramLine(holo, "§7Power: §f" + power);

            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            tntHolograms.put(key, holoId);

            plugin.getLogger().info("[TNTLive] Hologramme créé: " + holoId);
        } catch (Exception e) {
            plugin.getLogger().warning("[TNTLive] Erreur création hologramme: " + e.getMessage());
        }
    }

    /**
     * Supprime un hologramme de TNT
     */
    private void removeTntHologram(String locationKey) {
        String holoId = tntHolograms.remove(locationKey);
        if (holoId != null) {
            try {
                DHAPI.removeHologram(holoId);
                plugin.getLogger().info("[TNTLive] Hologramme supprimé: " + holoId);
            } catch (Exception e) {
                plugin.getLogger().warning("[TNTLive] Erreur suppression hologramme: " + e.getMessage());
            }
        }
    }

    /**
     * Supprime tous les hologrammes de TNT
     */
    private void removeAllTntHolograms() {
        for (String holoId : tntHolograms.values()) {
            try {
                DHAPI.removeHologram(holoId);
            } catch (Exception ignored) {}
        }
        tntHolograms.clear();
        plugin.getLogger().info("[TNTLive] Tous les hologrammes TNT supprimés");
    }

    // Map temporaire pour les TNT placées
    private final Map<String, Float> pendingCustomTnt = new HashMap<>();

    // Map des hologrammes pour les TNT custom (location key -> hologram name)
    private final Map<String, String> tntHolograms = new HashMap<>();
    private int holoCounter = 0;

    // Random pour les TNT aléatoires
    private final Random random = new Random();

    /**
     * Détecte quand une TNT est amorcée et stocke sa puissance
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (state != GameState.RUNNING) return;
        if (!event.getEntity().getWorld().getName().equals(worldName)) return;

        if (event.getEntity() instanceof org.bukkit.entity.TNTPrimed tnt) {
            Location loc = tnt.getLocation();
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

            // Supprimer l'hologramme (la TNT va exploser)
            removeTntHologram(key);

            // Vérifier si c'est une TNT custom
            if (pendingCustomTnt.containsKey(key)) {
                float power = pendingCustomTnt.remove(key);
                customTntPower.put(tnt.getUniqueId(), power);
                plugin.getLogger().info("[TNTLive] TNT amorcée avec power=" + power);
            }
        }
    }

    /**
     * Modifie l'explosion des TNT custom
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (state != GameState.RUNNING) return;
        if (!event.getEntity().getWorld().getName().equals(worldName)) return;

        UUID entityId = event.getEntity().getUniqueId();
        if (customTntPower.containsKey(entityId)) {
            float power = customTntPower.remove(entityId);

            // Annuler l'explosion par défaut et créer une plus grosse
            event.setCancelled(true);

            Location loc = event.getLocation();
            loc.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, true, true);

            plugin.getLogger().info("[TNTLive] Explosion custom: power=" + power);
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
            int saved = 0;
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null) {
                    config.set("tntlive." + key + ".inventory." + i, inventory[i]);
                    saved++;
                    // Log avec durabilité
                    String duraInfo = "";
                    if (inventory[i].getType().getMaxDurability() > 0) {
                        short maxDura = inventory[i].getType().getMaxDurability();
                        short currentDura = (short) (maxDura - getDamage(inventory[i]));
                        duraInfo = " (dura: " + currentDura + "/" + maxDura + ")";
                    }
                    plugin.getLogger().info("[TNTLive] Sauvegarde slot " + i + ": " + inventory[i].getType() + duraInfo);
                }
            }
            plugin.getLogger().info("[TNTLive] " + saved + " items sauvegardés pour " + key);
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
        if (section == null) {
            plugin.getLogger().warning("[TNTLive] Section introuvable: " + path);
            return null;
        }

        ItemStack[] items = new ItemStack[41]; // Taille max inventaire
        int loaded = 0;
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                // Utiliser le chemin complet pour récupérer l'ItemStack
                ItemStack item = config.getItemStack(path + "." + key);
                if (item != null && slot < items.length) {
                    items[slot] = item;
                    loaded++;
                    // Log avec durabilité si applicable
                    String duraInfo = "";
                    if (item.getType().getMaxDurability() > 0) {
                        short maxDura = item.getType().getMaxDurability();
                        short currentDura = (short) (maxDura - getDamage(item));
                        duraInfo = " (dura: " + currentDura + "/" + maxDura + ")";
                    }
                    plugin.getLogger().info("[TNTLive] Item chargé slot " + slot + ": " + item.getType() + duraInfo);
                }
            } catch (NumberFormatException ignored) {}
        }
        plugin.getLogger().info("[TNTLive] " + loaded + " items chargés depuis " + path);
        return loaded > 0 ? items : null;
    }

    /**
     * Récupère le damage (durabilité utilisée) d'un item
     */
    private int getDamage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
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

    // ==================== Stats Getters (pour overlays/placeholders) ====================

    /**
     * Récupère le nombre total de likes reçus pendant l'event
     */
    public int getTotalLikes() {
        return totalLikes;
    }

    /**
     * Récupère le nombre total de flèches données
     */
    public int getTotalArrowsGiven() {
        return totalArrowsGiven;
    }

    /**
     * Récupère le nombre de participants encore en vie (subs)
     */
    public int getAliveParticipants() {
        int alive = 0;
        for (UUID uuid : subTeam) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.isDead()) {
                alive++;
            }
        }
        return alive;
    }

    /**
     * Récupère le nombre total de participants
     */
    public int getTotalParticipants() {
        return participants.size();
    }

    /**
     * Récupère le nom du streamer
     */
    public String getStreamerName() {
        if (streamerId == null) return "Aucun";
        Player streamer = Bukkit.getPlayer(streamerId);
        if (streamer != null) {
            return streamer.getName();
        }
        // Si le streamer n'est pas en ligne, récupérer le nom depuis l'UUID
        return Bukkit.getOfflinePlayer(streamerId).getName();
    }

    /**
     * Force le rechargement de la config du schematic depuis le fichier
     */
    public void reloadSchematicConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        schematicName = config.getString("tntlive.schematic", "tntlive");

        if (config.contains("tntlive.schematic-origin")) {
            int x = config.getInt("tntlive.schematic-origin.x", 0);
            int y = config.getInt("tntlive.schematic-origin.y", 64);
            int z = config.getInt("tntlive.schematic-origin.z", 0);
            schematicOrigin = BlockVector3.at(x, y, z);
        }
    }
}
