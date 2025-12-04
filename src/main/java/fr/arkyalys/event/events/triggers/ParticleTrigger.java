package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Affiche des particules
 */
public class ParticleTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public ParticleTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String particleName = getString(config, "particle", "HEART").toUpperCase();
        int count = getInt(config, "count", 10);
        double offsetX = getDouble(config, "offset-x", 0.5);
        double offsetY = getDouble(config, "offset-y", 0.5);
        double offsetZ = getDouble(config, "offset-z", 0.5);
        double speed = getDouble(config, "speed", 0.1);

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Particule invalide: " + particleName);
            return;
        }

        Location loc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(
                particle,
                loc,
                count,
                offsetX, offsetY, offsetZ,
                speed
        );
    }
}
