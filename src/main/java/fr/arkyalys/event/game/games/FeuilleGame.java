package fr.arkyalys.event.game.games;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameState;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.List;
import java.util.UUID;

/**
 * Event "Feuille" - Les feuilles disparaissent progressivement via randomTickSpeed
 * Dernier survivant gagne!
 */
public class FeuilleGame extends GameEvent implements Listener {

    // Config spécifique
    private int leafDecayStartDelay = 100; // Délai avant le début de la disparition (5 secondes)
    private int startTickSpeed = 3;        // Vitesse au début de l'event (après countdown)
    private int likeTickSpeedBonus = 1;    // Bonus de tickspeed par like (+1)
    private List<String> regionNames;      // Régions WorldGuard pour régénérer (initialisé dans loadEventConfig)
    private Material leafMaterial;         // Type de feuille à régénérer (initialisé dans loadEventConfig)

    // Runtime
    private BukkitTask decayStartTask;
    private final java.util.List<BukkitTask> countdownTasks = new java.util.ArrayList<>();
    private int currentTickSpeed = 0;      // Tickspeed actuel (accumule avec les likes)

    public FeuilleGame(YouTubeEventPlugin plugin) {
        super(plugin, "feuille");
        // Enregistrer le listener pour bloquer les drops de feuilles
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Empêche les feuilles de drop des items dans le monde de l'event
     */
    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Vérifier si c'est le monde de l'event
        if (!event.getBlock().getWorld().getName().equals(worldName)) {
            return;
        }

        // Empêcher le drop (le bloc disparaît mais pas de drop)
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);
    }

    /**
     * Empêche les feuilles de brûler dans le monde de l'event
     */
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (!event.getBlock().getWorld().getName().equals(worldName)) {
            return;
        }

        // Bloquer la combustion des feuilles
        if (event.getBlock().getType().name().contains("LEAVES")) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche le feu de se propager dans le monde de l'event
     */
    @EventHandler
    public void onFireSpread(BlockSpreadEvent event) {
        if (!event.getBlock().getWorld().getName().equals(worldName)) {
            return;
        }

        // Bloquer la propagation du feu
        if (event.getSource().getType() == Material.FIRE) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void loadEventConfig(YamlConfiguration config) {
        leafDecayStartDelay = config.getInt("feuille.start-delay", 100);
        startTickSpeed = config.getInt("feuille.start-tick-speed", 3);
        likeTickSpeedBonus = config.getInt("feuille.like-tick-bonus", 1);

        // Charger les régions (peut être une liste)
        // NOTE: Ne pas utiliser de field initializer car le constructeur parent
        // appelle loadConfig() AVANT que les field initializers de la sous-classe s'exécutent
        List<String> loadedRegions = config.getStringList("feuille.regions");
        if (loadedRegions == null || loadedRegions.isEmpty()) {
            regionNames = List.of("feuille");
            plugin.getLogger().warning("Aucune région configurée, utilisation du défaut 'feuille'");
        } else {
            regionNames = loadedRegions;
        }
        plugin.getLogger().info("Régions WorldGuard chargées: " + regionNames);

        // Charger le type de feuille
        String leafType = config.getString("feuille.leaf-material", "OAK_LEAVES");
        try {
            leafMaterial = Material.valueOf(leafType.toUpperCase());
        } catch (IllegalArgumentException e) {
            leafMaterial = Material.OAK_LEAVES;
            plugin.getLogger().warning("Matériau invalide: " + leafType + ", utilisation de OAK_LEAVES");
        }
    }

    @Override
    protected void saveDefaultEventConfig(YamlConfiguration config) {
        config.set("feuille.start-delay", 100);           // 5 sec avant début (en ticks)
        config.set("feuille.start-tick-speed", 3);        // Tickspeed au début (après countdown)
        config.set("feuille.like-tick-bonus", 1);         // +1 tickspeed par like
        config.set("feuille.regions", List.of("feuille1", "feuille2")); // Régions WorldGuard à régénérer
        config.set("feuille.leaf-material", "OAK_LEAVES");// Type de feuille à régénérer

        // Triggers YouTube spécifiques à Feuille
        // %participant% = seulement les joueurs dans l'event
        // %all% = tous les joueurs du serveur
        config.set("youtube-triggers.like", List.of(
                "broadcast &a+1 Like! &7Merci pour le soutien!",
                "eco give %participant% 50"
        ));
        config.set("youtube-triggers.super-chat", List.of(
                "broadcast &d[SUPER CHAT] &f%viewer% &7(%amount%)",
                "eco give %participant% 500",
                "effect give %participant% speed 10 1"
        ));
        config.set("youtube-triggers.new-member", List.of(
                "broadcast &6[NOUVEAU MEMBRE] &f%viewer% &7rejoint la chaine!",
                "eco give %participant% 1000"
        ));
    }

    @Override
    protected void onOpen() {
        // S'assurer que le tickspeed est à 0 avant de regen
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            setRandomTickSpeed(world, 0);
        }

        // Reset les feuilles à l'ouverture
        regenerateLeaves();
        plugin.getLogger().info("Event Feuille ouvert! (tickspeed=0 jusqu'au begin)");
    }

    /**
     * Reset manuel des feuilles (appelable via commande)
     */
    public void resetLeaves() {
        regenerateLeaves();
    }

    @Override
    protected void onBegin() {
        plugin.getLogger().info("Event Feuille commence!");

        // Bloquer le tick speed à 0 pendant le countdown pour empêcher tout decay
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            setRandomTickSpeed(world, 0);
        }

        // Démarrer la disparition des feuilles APRÈS le countdown
        decayStartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.RUNNING) {
                // D'abord rendre les feuilles non-persistantes
                makeLeavesDecayable();
                // Puis activer le tick speed élevé
                startLeafDecay();
            }
        }, leafDecayStartDelay);

        // Broadcast countdown
        broadcastCountdown();
    }

    /**
     * Rend les feuilles non-persistantes pour qu'elles puissent decay
     */
    private void makeLeavesDecayable() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) return;

            int count = 0;
            for (String regionName : regionNames) {
                ProtectedRegion region = regionManager.getRegion(regionName);
                if (region == null) continue;

                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                            Block block = world.getBlockAt(x, y, z);
                            if (block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leavesData) {
                                if (leavesData.isPersistent()) {
                                    leavesData.setPersistent(false);
                                    block.setBlockData(leavesData);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
            plugin.getLogger().info(count + " feuilles rendues non-persistantes (vont decay)");
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur makeLeavesDecayable: " + e.getMessage());
        }
    }

    /**
     * Affiche un countdown avant le début de la disparition
     */
    private void broadcastCountdown() {
        // Clear les anciennes tâches
        countdownTasks.clear();

        int seconds = leafDecayStartDelay / 20;

        for (int i = seconds; i > 0; i--) {
            final int count = i;
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == GameState.RUNNING) {
                    String message = "&e" + count + "...";
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(
                                ChatColor.translateAlternateColorCodes('&', "&6Les feuilles vont tomber!"),
                                ChatColor.translateAlternateColorCodes('&', message),
                                0, 25, 5
                        );
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                }
            }, (seconds - i) * 20L);
            countdownTasks.add(task);
        }

        // Message final
        BukkitTask finalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.RUNNING) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(
                            ChatColor.translateAlternateColorCodes('&', "&c&lC'EST PARTI!"),
                            ChatColor.translateAlternateColorCodes('&', "&7Survivez le plus longtemps possible!"),
                            0, 40, 10
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                }
            }
        }, leafDecayStartDelay);
        countdownTasks.add(finalTask);
    }

    /**
     * Démarre la disparition des feuilles via randomTickSpeed
     */
    private void startLeafDecay() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Monde '" + worldName + "' introuvable pour l'event Feuille!");
            return;
        }

        // Démarrer avec le tickspeed de base
        currentTickSpeed = startTickSpeed;
        setRandomTickSpeed(world, currentTickSpeed);
        plugin.getLogger().info("RandomTickSpeed du monde '" + worldName + "' réglé à " + currentTickSpeed);
    }

    /**
     * Augmente le tickspeed de +1 (appelé par les likes YouTube)
     * Les likes s'accumulent!
     */
    public void triggerBoost() {
        if (state != GameState.RUNNING) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Augmenter le tickspeed (permanent, pas temporaire!)
        currentTickSpeed += likeTickSpeedBonus;
        setRandomTickSpeed(world, currentTickSpeed);

        // Effet visuel pour tous les participants
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', "&c+1 Like!"),
                        ChatColor.translateAlternateColorCodes('&', "&7Vitesse: &e" + currentTickSpeed),
                        5, 20, 5
                );
            }
        }

        plugin.getLogger().info("Like! RandomTickSpeed augmenté à " + currentTickSpeed);
    }

    /**
     * Définit le randomTickSpeed d'un monde
     */
    private void setRandomTickSpeed(World world, int speed) {
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, speed);
    }


    /**
     * Régénère les feuilles dans toutes les régions WorldGuard configurées
     */
    private void regenerateLeaves() {
        // Vérifier si WorldGuard est disponible
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            plugin.getLogger().warning("WorldGuard non installé - impossible de régénérer les feuilles");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Monde '" + worldName + "' introuvable pour régénérer les feuilles");
            return;
        }

        try {
            // Obtenir le RegionContainer de WorldGuard
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                plugin.getLogger().warning("Impossible d'obtenir le RegionManager pour le monde '" + worldName + "'");
                return;
            }

            int totalBlocksReplaced = 0;

            // Parcourir toutes les régions configurées
            for (String regionName : regionNames) {
                ProtectedRegion region = regionManager.getRegion(regionName);
                if (region == null) {
                    plugin.getLogger().warning("Région '" + regionName + "' introuvable dans le monde '" + worldName + "'");
                    continue;
                }

                // Obtenir les bornes de la région
                BlockVector3 min = region.getMinimumPoint();
                BlockVector3 max = region.getMaximumPoint();

                int blocksReplaced = 0;

                // Parcourir tous les blocs de la région
                for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                            Block block = world.getBlockAt(x, y, z);
                            // Si c'est de l'air, remettre des feuilles persistantes
                            if (block.getType() == Material.AIR) {
                                block.setType(leafMaterial);
                                // Marquer les feuilles comme persistantes (ne decay pas naturellement)
                                if (block.getBlockData() instanceof org.bukkit.block.data.type.Leaves leavesData) {
                                    leavesData.setPersistent(true);
                                    block.setBlockData(leavesData);
                                }
                                blocksReplaced++;
                            }
                        }
                    }
                }

                totalBlocksReplaced += blocksReplaced;
                plugin.getLogger().info("Région '" + regionName + "': " + blocksReplaced + " feuilles régénérées");
            }

            plugin.getLogger().info("Régénération terminée: " + totalBlocksReplaced + " feuilles au total");

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la régénération des feuilles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Récupère le tickspeed actuel
     */
    public int getCurrentTickSpeed() {
        return currentTickSpeed;
    }

    /**
     * Annule toutes les tâches planifiées (countdown, decay start)
     */
    private void cancelAllTasks() {
        // Annuler la tâche de démarrage du decay
        if (decayStartTask != null) {
            decayStartTask.cancel();
            decayStartTask = null;
        }

        // Annuler toutes les tâches de countdown
        for (BukkitTask task : countdownTasks) {
            if (task != null) {
                task.cancel();
            }
        }
        countdownTasks.clear();
    }

    @Override
    protected void onStop() {
        cancelAllTasks();

        // D'abord bloquer le decay (0) pour éviter toute perte pendant la regen
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            setRandomTickSpeed(world, 0);
        }

        // Régénérer les feuilles (seront persistantes)
        regenerateLeaves();

        // Garder à 0 entre les events pour être safe
        // (les feuilles sont persistantes mais on reste prudent)
        plugin.getLogger().info("Event Feuille arrêté! RandomTickSpeed maintenu à 0.");
    }

    @Override
    protected void onPlayerJoin(Player player) {
        // Effet de bienvenue
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    @Override
    protected void onPlayerLeave(Player player) {
        // Rien de spécial
    }

    @Override
    protected void onPlayerEliminated(Player player) {
        // Son d'élimination
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
    }

    @Override
    protected void onWin(Player winner) {
        // 1. Arrêter immédiatement toutes les tâches
        cancelAllTasks();

        // 2. Bloquer le decay AVANT de régénérer
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            setRandomTickSpeed(world, 0);
        }
        plugin.getLogger().info("Victoire! RandomTickSpeed mis à 0 pour regen.");

        // 3. Régénérer les feuilles (seront persistantes)
        regenerateLeaves();
        plugin.getLogger().info("Arène réinitialisée - feuilles régénérées, tickspeed reste à 0.");

        // 3. Titre de victoire pour tous
        String winnerName = winner.getName();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', "&6&l" + winnerName + " GAGNE!"),
                    ChatColor.translateAlternateColorCodes('&', "&aFélicitations au vainqueur!"),
                    10, 70, 20
            );
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        // 4. Feux d'artifice pour le gagnant
        Location loc = winner.getLocation().clone();
        for (int i = 0; i < 5; i++) {
            final Location fireworkLoc = loc.clone().add(0, 2, 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                fireworkLoc.getWorld().playSound(fireworkLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                fireworkLoc.getWorld().spawnParticle(Particle.FIREWORK, fireworkLoc, 50, 1, 1, 1, 0.1);
            }, i * 20L);
        }
    }

}
