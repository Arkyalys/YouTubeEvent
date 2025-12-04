package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/**
 * Applique des effets de potion au joueur
 */
public class EffectTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public EffectTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String effectName = getString(config, "effect", "SPEED").toUpperCase();
        int duration = getInt(config, "duration", 10); // En secondes
        int amplifier = getInt(config, "amplifier", 0); // 0 = niveau 1
        boolean ambient = getBoolean(config, "ambient", true);
        boolean particles = getBoolean(config, "particles", true);
        boolean icon = getBoolean(config, "icon", true);

        PotionEffectType effectType = PotionEffectType.getByName(effectName);
        if (effectType == null) {
            plugin.getLogger().warning("Type d'effet invalide: " + effectName);
            return;
        }

        // Durée en ticks (20 ticks = 1 seconde)
        int durationTicks = duration * 20;

        PotionEffect effect = new PotionEffect(
                effectType,
                durationTicks,
                amplifier,
                ambient,
                particles,
                icon
        );

        target.addPotionEffect(effect);

        plugin.getLogger().info("Effet applique: " + effectName + " niveau " + (amplifier + 1) +
                                " pendant " + duration + "s pour " + message.getAuthorName());
    }
}
