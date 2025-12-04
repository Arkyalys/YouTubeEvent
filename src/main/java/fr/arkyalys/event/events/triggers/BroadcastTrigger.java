package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Envoie des messages broadcast ou actionbar
 */
public class BroadcastTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public BroadcastTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String text = getString(config, "message", "&7Message de %viewer%");
        String type = getString(config, "broadcast-type", "CHAT").toUpperCase();

        String formattedText = replacePlaceholders(text, message, target).replace("&", "\u00A7");

        switch (type) {
            case "CHAT" -> Bukkit.broadcastMessage(formattedText);
            case "ACTIONBAR" -> sendActionBar(formattedText);
            case "TITLE" -> sendTitle(config, formattedText, message, target);
            case "PLAYER" -> target.sendMessage(formattedText);
            default -> Bukkit.broadcastMessage(formattedText);
        }
    }

    private void sendActionBar(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
    }

    private void sendTitle(Map<?, ?> config, String title, ChatMessage message, Player target) {
        String subtitle = getString(config, "subtitle", "");
        subtitle = replacePlaceholders(subtitle, message, target).replace("&", "\u00A7");

        int fadeIn = getInt(config, "fade-in", 10);
        int stay = getInt(config, "stay", 70);
        int fadeOut = getInt(config, "fade-out", 20);

        boolean broadcast = getBoolean(config, "broadcast", false);

        if (broadcast) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        } else {
            target.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }
}
