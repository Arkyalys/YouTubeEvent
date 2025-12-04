package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;

/**
 * Fait spawn des mobs près du joueur
 */
public class MobSpawnTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;
    private final Random random = new Random();

    public MobSpawnTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String mobName = getString(config, "mob", "ZOMBIE").toUpperCase();
        int amount = getInt(config, "amount", 1);
        int radius = getInt(config, "radius", 5);
        String customName = getString(config, "name", null);
        boolean hostile = getBoolean(config, "hostile", true);

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(mobName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de mob invalide: " + mobName);
            return;
        }

        for (int i = 0; i < amount; i++) {
            Location spawnLoc = getRandomLocation(target.getLocation(), radius);

            Entity entity = target.getWorld().spawnEntity(spawnLoc, entityType);

            // Nom custom
            if (customName != null) {
                String name = replacePlaceholders(customName, message, target);
                entity.setCustomName(name.replace("&", "\u00A7"));
                entity.setCustomNameVisible(true);
            }

            // Si non hostile, ne pas cibler le joueur
            if (!hostile && entity instanceof LivingEntity living) {
                // Le mob n'attaquera pas automatiquement
            }
        }

        plugin.getLogger().info("Spawn de " + amount + " " + mobName + " pour " + message.getAuthorName());
    }

    private Location getRandomLocation(Location center, int radius) {
        double x = center.getX() + (random.nextDouble() * 2 - 1) * radius;
        double z = center.getZ() + (random.nextDouble() * 2 - 1) * radius;
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(center.getWorld(), x, y, z);
    }
}
