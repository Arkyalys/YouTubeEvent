package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Joue des sons
 */
public class SoundTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public SoundTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String soundName = getString(config, "sound", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase();
        float volume = (float) getDouble(config, "volume", 1.0);
        float pitch = (float) getDouble(config, "pitch", 1.0);
        boolean broadcast = getBoolean(config, "broadcast", false);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son invalide: " + soundName);
            return;
        }

        if (broadcast) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } else {
            target.playSound(target.getLocation(), sound, volume, pitch);
        }
    }
}
