package fr.arkyalys.event.game.games;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameState;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Event "Feuille" - Les feuilles disparaissent progressivement
 * Dernier survivant gagne!
 */
public class FeuilleGame extends GameEvent {

    // Config spécifique
    private int leafDecayDelay = 20; // Ticks entre chaque vague de disparition
    private int leafDecayRadius = 1; // Nombre de blocs qui disparaissent par vague
    private int leafDecayStartDelay = 100; // Délai avant le début de la disparition (5 secondes)
    private List<Material> leafMaterials = new ArrayList<>();

    // Runtime
    private BukkitTask decayTask;

    public FeuilleGame(YouTubeEventPlugin plugin) {
        super(plugin, "feuille");
    }

    @Override
    protected void loadEventConfig(YamlConfiguration config) {
        leafDecayDelay = config.getInt("feuille.decay-delay", 20);
        leafDecayRadius = config.getInt("feuille.decay-radius", 1);
        leafDecayStartDelay = config.getInt("feuille.start-delay", 100);

        // Charger les types de feuilles
        leafMaterials.clear();
        List<String> materials = config.getStringList("feuille.leaf-materials");
        if (materials.isEmpty()) {
            // Par défaut, tous les types de feuilles
            leafMaterials.add(Material.OAK_LEAVES);
            leafMaterials.add(Material.SPRUCE_LEAVES);
            leafMaterials.add(Material.BIRCH_LEAVES);
            leafMaterials.add(Material.JUNGLE_LEAVES);
            leafMaterials.add(Material.ACACIA_LEAVES);
            leafMaterials.add(Material.DARK_OAK_LEAVES);
            leafMaterials.add(Material.AZALEA_LEAVES);
            leafMaterials.add(Material.FLOWERING_AZALEA_LEAVES);
            leafMaterials.add(Material.MANGROVE_LEAVES);
            leafMaterials.add(Material.CHERRY_LEAVES);
        } else {
            for (String mat : materials) {
                try {
                    leafMaterials.add(Material.valueOf(mat.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Matériau invalide dans config feuille: " + mat);
                }
            }
        }
    }

    @Override
    protected void saveDefaultEventConfig(YamlConfiguration config) {
        config.set("feuille.decay-delay", 20);
        config.set("feuille.decay-radius", 1);
        config.set("feuille.start-delay", 100);
        config.set("feuille.leaf-materials", List.of(
                "OAK_LEAVES",
                "SPRUCE_LEAVES",
                "BIRCH_LEAVES",
                "JUNGLE_LEAVES",
                "ACACIA_LEAVES",
                "DARK_OAK_LEAVES"
        ));

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
        plugin.getLogger().info("Event Feuille ouvert!");
    }

    @Override
    protected void onBegin() {
        plugin.getLogger().info("Event Feuille commence!");

        // Démarrer la disparition des feuilles après un délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == GameState.RUNNING) {
                startLeafDecay();
            }
        }, leafDecayStartDelay);

        // Broadcast countdown
        broadcastCountdown();
    }

    /**
     * Affiche un countdown avant le début de la disparition
     */
    private void broadcastCountdown() {
        int seconds = leafDecayStartDelay / 20;

        for (int i = seconds; i > 0; i--) {
            final int count = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
        }

        // Message final
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
    }

    /**
     * Démarre la tâche de disparition des feuilles
     */
    private void startLeafDecay() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        decayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.RUNNING) {
                cancelDecayTask();
                return;
            }

            // Faire disparaître des feuilles aléatoires autour des joueurs
            for (UUID uuid : participants) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;

                // Chercher des feuilles sous le joueur
                Location playerLoc = player.getLocation();
                for (int x = -leafDecayRadius; x <= leafDecayRadius; x++) {
                    for (int z = -leafDecayRadius; z <= leafDecayRadius; z++) {
                        for (int y = -3; y <= 0; y++) {
                            Block block = playerLoc.clone().add(x, y, z).getBlock();
                            if (isLeaf(block.getType())) {
                                // Chance de disparition
                                if (Math.random() < 0.3) {
                                    // Effet visuel
                                    world.spawnParticle(
                                            Particle.BLOCK,
                                            block.getLocation().add(0.5, 0.5, 0.5),
                                            10,
                                            0.3, 0.3, 0.3,
                                            0.1,
                                            block.getBlockData()
                                    );

                                    // Son
                                    world.playSound(
                                            block.getLocation(),
                                            Sound.BLOCK_GRASS_BREAK,
                                            0.5f, 1f
                                    );

                                    // Supprimer le bloc
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, leafDecayDelay);
    }

    /**
     * Vérifie si un matériau est une feuille
     */
    private boolean isLeaf(Material material) {
        return leafMaterials.contains(material);
    }

    /**
     * Annule la tâche de disparition
     */
    private void cancelDecayTask() {
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    @Override
    protected void onStop() {
        cancelDecayTask();
        plugin.getLogger().info("Event Feuille arrêté!");
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
        cancelDecayTask();

        // Feux d'artifice pour le gagnant
        Location loc = winner.getLocation();
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                loc.getWorld().spawnParticle(Particle.FIREWORK, loc.add(0, 2, 0), 50, 1, 1, 1, 0.1);
            }, i * 20L);
        }
    }

    /**
     * Force la disparition de feuilles (appelé par les triggers YouTube)
     */
    public void forceLeafDecay(int amount) {
        if (state != GameState.RUNNING) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        int destroyed = 0;
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            Location loc = player.getLocation();
            for (int x = -5; x <= 5 && destroyed < amount; x++) {
                for (int z = -5; z <= 5 && destroyed < amount; z++) {
                    for (int y = -5; y <= 0 && destroyed < amount; y++) {
                        Block block = loc.clone().add(x, y, z).getBlock();
                        if (isLeaf(block.getType())) {
                            block.setType(Material.AIR);
                            world.spawnParticle(
                                    Particle.BLOCK,
                                    block.getLocation().add(0.5, 0.5, 0.5),
                                    10, 0.3, 0.3, 0.3, 0.1,
                                    block.getBlockData()
                            );
                            destroyed++;
                        }
                    }
                }
            }
        }
    }
}
