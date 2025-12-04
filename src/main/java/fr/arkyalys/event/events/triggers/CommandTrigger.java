package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Exécute des commandes
 * Supporte %all% pour exécuter la commande pour chaque joueur en ligne
 */
public class CommandTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public CommandTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String executor = getString(config, "executor", "CONSOLE").toUpperCase();

        // Commande unique ou liste de commandes
        Object cmdObj = config.get("command");
        Object cmdsObj = config.get("commands");

        if (cmdObj != null) {
            executeCommand(cmdObj.toString(), executor, message, target);
        }

        if (cmdsObj instanceof List<?> commands) {
            for (Object cmd : commands) {
                executeCommand(cmd.toString(), executor, message, target);
            }
        }
    }

    private void executeCommand(String command, String executor, ChatMessage message, Player target) {
        // Remplacer les placeholders de base
        String cmd = replacePlaceholders(command, message, target);

        // Si la commande contient %all%, l'exécuter pour chaque joueur en ligne
        if (cmd.contains("%all%")) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

            if (onlinePlayers.isEmpty()) {
                plugin.getLogger().info("Aucun joueur en ligne pour la commande avec %all%");
                return;
            }

            plugin.getLogger().info("Execution de la commande pour " + onlinePlayers.size() + " joueurs");

            for (Player player : onlinePlayers) {
                String playerCmd = cmd.replace("%all%", player.getName());
                executeSingleCommand(playerCmd, executor, target);
            }
        } else {
            executeSingleCommand(cmd, executor, target);
        }
    }

    private void executeSingleCommand(String cmd, String executor, Player target) {
        switch (executor) {
            case "CONSOLE" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            case "PLAYER" -> {
                if (target != null && target.isOnline()) {
                    target.performCommand(cmd);
                }
            }
            case "OP" -> {
                if (target != null && target.isOnline()) {
                    boolean wasOp = target.isOp();
                    target.setOp(true);
                    try {
                        target.performCommand(cmd);
                    } finally {
                        if (!wasOp) {
                            target.setOp(false);
                        }
                    }
                }
            }
            default -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        plugin.getLogger().info("Commande executee: " + cmd);
    }
}
