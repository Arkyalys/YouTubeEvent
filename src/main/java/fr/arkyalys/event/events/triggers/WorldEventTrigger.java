package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.Map;
import java.util.Random;

/**
 * Déclenche des événements du monde (explosions, météo, foudre, etc.)
 */
public class WorldEventTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;
    private final Random random = new Random();

    public WorldEventTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String event = getString(config, "event", "LIGHTNING").toUpperCase();

        switch (event) {
            case "LIGHTNING" -> spawnLightning(config, target);
            case "EXPLOSION" -> createExplosion(config, target);
            case "TNT" -> spawnTNT(config, target);
            case "RAIN" -> setWeather(target.getWorld(), true, false);
            case "STORM" -> setWeather(target.getWorld(), true, true);
            case "CLEAR" -> setWeather(target.getWorld(), false, false);
            case "DAY" -> setTime(target.getWorld(), 1000);
            case "NIGHT" -> setTime(target.getWorld(), 13000);
            case "MIDNIGHT" -> setTime(target.getWorld(), 18000);
            case "FIRE" -> setFire(config, target);
            case "METEOR" -> spawnMeteor(config, target);
            default -> plugin.getLogger().warning("Evenement monde inconnu: " + event);
        }

        plugin.getLogger().info("Evenement monde: " + event + " pour " + message.getAuthorName());
    }

    private void spawnLightning(Map<?, ?> config, Player target) {
        int radius = getInt(config, "radius", 3);
        boolean damage = getBoolean(config, "damage", true);
        int count = getInt(config, "count", 1);

        for (int i = 0; i < count; i++) {
            Location loc = getRandomLocation(target.getLocation(), radius);
            if (damage) {
                target.getWorld().strikeLightning(loc);
            } else {
                target.getWorld().strikeLightningEffect(loc);
            }
        }
    }

    private void createExplosion(Map<?, ?> config, Player target) {
        float power = (float) getDouble(config, "power", 2.0);
        boolean fire = getBoolean(config, "fire", false);
        boolean breakBlocks = getBoolean(config, "break-blocks", false);
        int radius = getInt(config, "radius", 3);

        Location loc = getRandomLocation(target.getLocation(), radius);
        target.getWorld().createExplosion(loc, power, fire, breakBlocks);
    }

    private void spawnTNT(Map<?, ?> config, Player target) {
        int count = getInt(config, "count", 1);
        int radius = getInt(config, "radius", 3);
        int fuse = getInt(config, "fuse", 80); // Ticks avant explosion

        for (int i = 0; i < count; i++) {
            Location loc = getRandomLocation(target.getLocation(), radius);
            loc.setY(loc.getY() + 5); // Spawn en l'air

            TNTPrimed tnt = (TNTPrimed) target.getWorld().spawnEntity(loc, EntityType.TNT);
            tnt.setFuseTicks(fuse);
        }
    }

    private void setWeather(World world, boolean rain, boolean thunder) {
        world.setStorm(rain);
        world.setThundering(thunder);
        if (rain) {
            world.setWeatherDuration(6000); // 5 minutes
        }
    }

    private void setTime(World world, long time) {
        world.setTime(time);
    }

    private void setFire(Map<?, ?> config, Player target) {
        int duration = getInt(config, "duration", 5); // Secondes
        target.setFireTicks(duration * 20);
    }

    private void spawnMeteor(Map<?, ?> config, Player target) {
        int count = getInt(config, "count", 5);
        int radius = getInt(config, "radius", 10);
        float power = (float) getDouble(config, "power", 2.0);

        for (int i = 0; i < count; i++) {
            Location loc = getRandomLocation(target.getLocation(), radius);
            loc.setY(target.getLocation().getY() + 30 + random.nextInt(20));

            // Spawn une boule de feu ou TNT qui tombe
            target.getWorld().spawnEntity(loc, EntityType.FIREBALL);
        }
    }

    private Location getRandomLocation(Location center, int radius) {
        double x = center.getX() + (random.nextDouble() * 2 - 1) * radius;
        double z = center.getZ() + (random.nextDouble() * 2 - 1) * radius;
        double y = center.getY();

        return new Location(center.getWorld(), x, y, z);
    }
}
