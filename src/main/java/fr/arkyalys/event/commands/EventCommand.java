package fr.arkyalys.event.commands;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameManager;
import fr.arkyalys.event.game.GameState;
import fr.arkyalys.event.game.games.FeuilleGame;
import fr.arkyalys.event.game.games.TNTLiveGame;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commandes pour le système d'events
 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private final YouTubeEventPlugin plugin;
    private final String prefix;

    public EventCommand(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.prefix = plugin.getConfigManager().getPrefix();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start" -> handleStart(sender, args);
            case "begin" -> handleBegin(sender);
            case "stop" -> handleStop(sender);
            case "list" -> handleList(sender);
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "reset" -> handleReset(sender, args);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "setstreamer" -> handleSetStreamer(sender, args);
            case "setstuff" -> handleSetStuff(sender, args);
            case "setorigin" -> handleSetOrigin(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage((prefix + "&cCommande inconnue. Utilisez /event help").replace("&", "§"));
            }
        }

        return true;
    }

    /**
     * /event start <event>
     * Si l'event est déjà ouvert, le lance (begin)
     */
    private void handleStart(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (args.length < 2) {
            sender.sendMessage((prefix + "&cUtilisation: /event start <nom_event>").replace("&", "§"));
            sender.sendMessage((prefix + "&7Events disponibles: " + getGameNames()).replace("&", "§"));
            return;
        }

        GameManager gameManager = plugin.getGameManager();
        String gameName = args[1].toLowerCase();
        GameEvent game = gameManager.getGame(gameName);

        if (game == null) {
            sender.sendMessage((prefix + "&cEvent inconnu: " + gameName).replace("&", "§"));
            sender.sendMessage((prefix + "&7Events disponibles: " + getGameNames()).replace("&", "§"));
            return;
        }

        // Si cet event est déjà ouvert, le lancer (begin)
        GameEvent currentGame = gameManager.getCurrentGame();
        if (currentGame != null && currentGame.getName().equals(gameName)) {
            if (currentGame.getState() == GameState.OPEN) {
                // Lancer le jeu
                if (currentGame.getParticipantCount() < currentGame.getMinPlayers()) {
                    sender.sendMessage((prefix + "&cPas assez de joueurs! Minimum: " + currentGame.getMinPlayers() +
                            " (actuellement: " + currentGame.getParticipantCount() + ")").replace("&", "§"));
                    return;
                }
                if (gameManager.beginGame()) {
                    sender.sendMessage((prefix + "&aEvent &6" + game.getDisplayName() + " &alancé!").replace("&", "§"));
                } else {
                    sender.sendMessage((prefix + "&cImpossible de lancer l'event.").replace("&", "§"));
                }
                return;
            } else if (currentGame.getState() == GameState.RUNNING) {
                sender.sendMessage((prefix + "&cCet event est déjà en cours!").replace("&", "§"));
                return;
            }
        }

        // Si un autre event est actif
        if (gameManager.hasActiveGame()) {
            sender.sendMessage((prefix + "&cUn autre event est en cours! Utilisez /event stop d'abord.").replace("&", "§"));
            return;
        }

        // Ouvrir l'event
        if (gameManager.startGame(gameName)) {
            sender.sendMessage((prefix + "&aEvent &6" + game.getDisplayName() + " &aouvert!").replace("&", "§"));
            sender.sendMessage((prefix + "&7Refaites &f/event start " + gameName + " &7pour lancer le jeu.").replace("&", "§"));
        } else {
            sender.sendMessage((prefix + "&cImpossible d'ouvrir l'event. Vérifiez que le monde existe.").replace("&", "§"));
        }
    }

    /**
     * /event begin
     */
    private void handleBegin(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        GameManager gameManager = plugin.getGameManager();
        GameEvent game = gameManager.getCurrentGame();

        if (game == null || game.getState() != GameState.OPEN) {
            sender.sendMessage((prefix + "&cAucun event n'est ouvert! Utilisez /event start <event>").replace("&", "§"));
            return;
        }

        if (game.getParticipantCount() < game.getMinPlayers()) {
            sender.sendMessage((prefix + "&cPas assez de joueurs! Minimum: " + game.getMinPlayers() +
                    " (actuellement: " + game.getParticipantCount() + ")").replace("&", "§"));
            return;
        }

        if (gameManager.beginGame()) {
            sender.sendMessage((prefix + "&aEvent &6" + game.getDisplayName() + " &alancé!").replace("&", "§"));
        } else {
            sender.sendMessage((prefix + "&cImpossible de lancer l'event.").replace("&", "§"));
        }
    }

    /**
     * /event stop
     */
    private void handleStop(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        GameManager gameManager = plugin.getGameManager();

        if (!gameManager.hasActiveGame()) {
            sender.sendMessage((prefix + "&cAucun event en cours.").replace("&", "§"));
            return;
        }

        gameManager.stopGame();
        sender.sendMessage((prefix + "&cEvent arrêté.").replace("&", "§"));
    }

    /**
     * /event list
     */
    private void handleList(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        sender.sendMessage("&6========== &eEvents Disponibles &6==========".replace("&", "§"));

        for (GameEvent game : plugin.getGameManager().getGames()) {
            String status = getStatusColor(game.getState());
            sender.sendMessage(("&7- &f" + game.getName() + " &7(" + game.getDisplayName() + "&7) " + status).replace("&", "§"));
        }

        sender.sendMessage("&6==========================================".replace("&", "§"));
    }

    private String getStatusColor(GameState state) {
        return switch (state) {
            case WAITING -> "&8[INACTIF]";
            case OPEN -> "&a[OUVERT]";
            case RUNNING -> "&c[EN COURS]";
            case ENDED -> "&e[TERMINÉ]";
        };
    }

    /**
     * /event join
     */
    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage((prefix + "&cCette commande doit être exécutée par un joueur.").replace("&", "§"));
            return;
        }

        GameManager gameManager = plugin.getGameManager();
        GameEvent game = gameManager.getCurrentGame();

        if (game == null || game.getState() != GameState.OPEN) {
            player.sendMessage((prefix + "&cAucun event n'est ouvert pour le moment.").replace("&", "§"));
            return;
        }

        gameManager.joinGame(player);
    }

    /**
     * /event leave
     */
    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage((prefix + "&cCette commande doit être exécutée par un joueur.").replace("&", "§"));
            return;
        }

        GameManager gameManager = plugin.getGameManager();
        GameEvent game = gameManager.getCurrentGame();

        if (game == null) {
            player.sendMessage((prefix + "&cVous ne participez à aucun event.").replace("&", "§"));
            return;
        }

        if (!game.isParticipant(player)) {
            player.sendMessage((prefix + "&cVous ne participez pas à cet event.").replace("&", "§"));
            return;
        }

        gameManager.leaveGame(player);
    }

    /**
     * /event setspawn <event|spawn> [streamer|sub]
     */
    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (!(sender instanceof Player player)) {
            sender.sendMessage((prefix + "&cCette commande doit être exécutée par un joueur.").replace("&", "§"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage((prefix + "&cUtilisation: /event setspawn <nom_event|spawn> [streamer|sub]").replace("&", "§"));
            sender.sendMessage((prefix + "&7spawn = spawn de retour (leave/elimination)").replace("&", "§"));
            sender.sendMessage((prefix + "&7Pour TNTLive: /event setspawn tntlive streamer|sub").replace("&", "§"));
            return;
        }

        String target = args[1].toLowerCase();

        // Spawn de retour global
        if (target.equals("spawn")) {
            plugin.getGameManager().setReturnSpawn(player.getLocation());
            sender.sendMessage((prefix + "&aSpawn de retour défini à votre position!").replace("&", "§"));
            sender.sendMessage((prefix + "&7Les joueurs seront TP ici après leave/elimination.").replace("&", "§"));
            return;
        }

        // Spawn d'un event spécifique
        GameEvent game = plugin.getGameManager().getGame(target);

        if (game == null) {
            sender.sendMessage((prefix + "&cEvent inconnu: " + target).replace("&", "§"));
            sender.sendMessage((prefix + "&7Utilisez &fspawn &7pour le spawn de retour.").replace("&", "§"));
            return;
        }

        // TNTLive: spawns par équipe
        if (game instanceof TNTLiveGame tntLiveGame) {
            if (args.length < 3) {
                sender.sendMessage((prefix + "&cUtilisation: /event setspawn tntlive <streamer|sub>").replace("&", "§"));
                return;
            }

            String team = args[2].toLowerCase();
            if (team.equals("streamer")) {
                tntLiveGame.setStreamerSpawn(player.getLocation());
                sender.sendMessage((prefix + "&aSpawn du &6STREAMER &adéfini!").replace("&", "§"));
            } else if (team.equals("sub")) {
                tntLiveGame.setSubSpawn(player.getLocation());
                sender.sendMessage((prefix + "&aSpawn des &cSUBS &adéfini!").replace("&", "§"));
            } else {
                sender.sendMessage((prefix + "&cÉquipe invalide. Utilisez: streamer ou sub").replace("&", "§"));
            }
            return;
        }

        // Autres events: spawn unique
        game.setSpawn(player.getLocation());
        sender.sendMessage((prefix + "&aSpawn de l'event &6" + game.getDisplayName() +
                " &adéfini à votre position!").replace("&", "§"));
    }

    /**
     * /event reset <event>
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (args.length < 2) {
            sender.sendMessage((prefix + "&cUtilisation: /event reset <nom_event>").replace("&", "§"));
            return;
        }

        String gameName = args[1].toLowerCase();
        GameEvent game = plugin.getGameManager().getGame(gameName);

        if (game == null) {
            sender.sendMessage((prefix + "&cEvent inconnu: " + gameName).replace("&", "§"));
            return;
        }

        // Reset spécifique selon le type d'event
        if (game instanceof FeuilleGame feuilleGame) {
            feuilleGame.resetLeaves();
            sender.sendMessage((prefix + "&aFeuilles de l'event &6" + game.getDisplayName() + " &aréinitialisées!").replace("&", "§"));
        } else if (game instanceof TNTLiveGame tntLiveGame) {
            // Recharger la config du schematic (au cas où elle a été modifiée)
            tntLiveGame.reloadSchematicConfig();

            // Afficher les infos de debug
            sender.sendMessage((prefix + "&7Schematic: &f" + tntLiveGame.getSchematicName()).replace("&", "§"));
            sender.sendMessage((prefix + "&7Origine: &f" + (tntLiveGame.getSchematicOrigin() != null ?
                tntLiveGame.getSchematicOrigin().toString() : "NON DÉFINIE")).replace("&", "§"));

            if (tntLiveGame.getSchematicOrigin() == null) {
                sender.sendMessage((prefix + "&cErreur: Origine non définie! Utilisez /event setorigin tntlive").replace("&", "§"));
                return;
            }

            sender.sendMessage((prefix + "&7Paste du schematic en cours...").replace("&", "§"));
            tntLiveGame.pasteSchematic();
            sender.sendMessage((prefix + "&aMap TNTLive réinitialisée!").replace("&", "§"));
        } else {
            sender.sendMessage((prefix + "&eCet event n'a pas de fonction de reset.").replace("&", "§"));
        }
    }

    /**
     * /event setstreamer <player>
     * Définit le streamer pour l'event TNTLive
     */
    private void handleSetStreamer(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (args.length < 2) {
            sender.sendMessage((prefix + "&cUtilisation: /event setstreamer <joueur>").replace("&", "§"));
            return;
        }

        // Récupérer le joueur
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage((prefix + "&cJoueur introuvable: " + args[1]).replace("&", "§"));
            return;
        }

        // Récupérer l'event TNTLive
        GameEvent game = plugin.getGameManager().getGame("tntlive");
        if (!(game instanceof TNTLiveGame tntLiveGame)) {
            sender.sendMessage((prefix + "&cEvent TNTLive introuvable!").replace("&", "§"));
            return;
        }

        tntLiveGame.setStreamer(target);
        sender.sendMessage((prefix + "&a" + target.getName() + " &7est maintenant le &6STREAMER &7pour TNTLive!").replace("&", "§"));
    }

    /**
     * /event setstuff <event> <team>
     * Sauvegarde l'inventaire actuel comme stuff d'une équipe
     */
    private void handleSetStuff(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (!(sender instanceof Player player)) {
            sender.sendMessage((prefix + "&cCette commande doit être exécutée par un joueur.").replace("&", "§"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage((prefix + "&cUtilisation: /event setstuff tntlive <streamer|sub>").replace("&", "§"));
            sender.sendMessage((prefix + "&7Votre inventaire actuel sera sauvegardé.").replace("&", "§"));
            return;
        }

        String gameName = args[1].toLowerCase();
        String team = args[2].toLowerCase();

        // Pour l'instant, seul TNTLive supporte cette commande
        if (!gameName.equals("tntlive")) {
            sender.sendMessage((prefix + "&cSeul l'event TNTLive supporte cette commande.").replace("&", "§"));
            return;
        }

        GameEvent game = plugin.getGameManager().getGame("tntlive");
        if (!(game instanceof TNTLiveGame tntLiveGame)) {
            sender.sendMessage((prefix + "&cEvent TNTLive introuvable!").replace("&", "§"));
            return;
        }

        if (team.equals("streamer")) {
            tntLiveGame.saveStreamerStuff(player);
            sender.sendMessage((prefix + "&aStuff du &6STREAMER &asauvegardé depuis votre inventaire!").replace("&", "§"));
        } else if (team.equals("sub")) {
            tntLiveGame.saveSubStuff(player);
            sender.sendMessage((prefix + "&aStuff des &cSUBS &asauvegardé depuis votre inventaire!").replace("&", "§"));
        } else {
            sender.sendMessage((prefix + "&cÉquipe invalide. Utilisez: streamer ou sub").replace("&", "§"));
        }
    }

    /**
     * /event setorigin <event>
     * Définit l'origine du schematic à la position actuelle
     */
    private void handleSetOrigin(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        if (!(sender instanceof Player player)) {
            sender.sendMessage((prefix + "&cCette commande doit être exécutée par un joueur.").replace("&", "§"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage((prefix + "&cUtilisation: /event setorigin tntlive").replace("&", "§"));
            sender.sendMessage((prefix + "&7Votre position sera l'origine du schematic.").replace("&", "§"));
            return;
        }

        String gameName = args[1].toLowerCase();

        // Pour l'instant, seul TNTLive supporte cette commande
        if (!gameName.equals("tntlive")) {
            sender.sendMessage((prefix + "&cSeul l'event TNTLive supporte cette commande.").replace("&", "§"));
            return;
        }

        GameEvent game = plugin.getGameManager().getGame("tntlive");
        if (!(game instanceof TNTLiveGame tntLiveGame)) {
            sender.sendMessage((prefix + "&cEvent TNTLive introuvable!").replace("&", "§"));
            return;
        }

        tntLiveGame.setSchematicOrigin(player.getLocation());
        sender.sendMessage((prefix + "&aOrigine du schematic définie à votre position!").replace("&", "§"));
        sender.sendMessage((prefix + "&7Le schematic sera collé ici à la fin de l'event.").replace("&", "§"));
    }

    /**
     * /event status
     */
    private void handleStatus(CommandSender sender) {
        GameManager gameManager = plugin.getGameManager();
        GameEvent game = gameManager.getCurrentGame();

        sender.sendMessage("&6========== &eStatut Event &6==========".replace("&", "§"));

        if (game == null || game.getState() == GameState.WAITING) {
            sender.sendMessage("&7Aucun event en cours.".replace("&", "§"));
        } else {
            sender.sendMessage(("&7Event: &f" + game.getDisplayName()).replace("&", "§"));
            sender.sendMessage(("&7État: " + getStatusColor(game.getState())).replace("&", "§"));
            sender.sendMessage(("&7Participants: &f" + game.getParticipantCount() + "/" + game.getMaxPlayers()).replace("&", "§"));
            sender.sendMessage(("&7Monde: &f" + game.getWorldName()).replace("&", "§"));
        }

        // Info YouTube
        if (plugin.isConnected()) {
            sender.sendMessage("&7Live YouTube: &aConnecté".replace("&", "§"));
        } else {
            sender.sendMessage("&7Live YouTube: &cDéconnecté".replace("&", "§"));
        }

        sender.sendMessage("&6====================================".replace("&", "§"));
    }

    /**
     * /event reload
     */
    private void handleReload(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.event.admin")) return;

        plugin.getGameManager().reload();
        sender.sendMessage((prefix + "&aConfiguration des events rechargée!").replace("&", "§"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("&6========== &eCommandes Event &6==========".replace("&", "§"));
        sender.sendMessage("&e/event start <event> &7- Ouvrir un event".replace("&", "§"));
        sender.sendMessage("&e/event begin &7- Lancer l'event".replace("&", "§"));
        sender.sendMessage("&e/event stop &7- Arrêter l'event".replace("&", "§"));
        sender.sendMessage("&e/event list &7- Liste des events".replace("&", "§"));
        sender.sendMessage("&e/event join &7- Rejoindre l'event".replace("&", "§"));
        sender.sendMessage("&e/event leave &7- Quitter l'event".replace("&", "§"));
        sender.sendMessage("&e/event setspawn <event|spawn> &7- Définir le spawn".replace("&", "§"));
        sender.sendMessage("&e/event reset <event> &7- Reset l'event".replace("&", "§"));
        sender.sendMessage("&e/event status &7- Voir le statut".replace("&", "§"));
        sender.sendMessage("&e/event reload &7- Recharger les configs".replace("&", "§"));
        sender.sendMessage("&6---------- &eTNTLive &6----------".replace("&", "§"));
        sender.sendMessage("&e/event setstreamer <joueur> &7- Définir le streamer".replace("&", "§"));
        sender.sendMessage("&e/event setstuff tntlive <streamer|sub> &7- Sauver le stuff".replace("&", "§"));
        sender.sendMessage("&e/event setspawn tntlive <streamer|sub> &7- Spawns équipes".replace("&", "§"));
        sender.sendMessage("&e/event setorigin tntlive &7- Origine du schematic".replace("&", "§"));
        sender.sendMessage("&6========================================".replace("&", "§"));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage((prefix + "&cVous n'avez pas la permission.").replace("&", "§"));
            return false;
        }
        return true;
    }

    private String getGameNames() {
        StringBuilder sb = new StringBuilder();
        for (GameEvent game : plugin.getGameManager().getGames()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(game.getName());
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();

            // Commandes admin
            if (sender.hasPermission("youtubeevent.event.admin")) {
                subCommands.addAll(Arrays.asList("start", "begin", "stop", "list", "setspawn", "reset", "reload",
                        "setstreamer", "setstuff", "setorigin"));
            }

            // Commandes joueur
            subCommands.addAll(Arrays.asList("join", "leave", "status", "help"));

            String current = args[0].toLowerCase();
            for (String cmd : subCommands) {
                if (cmd.startsWith(current)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String current = args[1].toLowerCase();

            if (subCommand.equals("start") || subCommand.equals("reset") ||
                subCommand.equals("setstuff") || subCommand.equals("setorigin")) {
                for (GameEvent game : plugin.getGameManager().getGames()) {
                    if (game.getName().startsWith(current)) {
                        completions.add(game.getName());
                    }
                }
            } else if (subCommand.equals("setspawn")) {
                // Ajouter "spawn" pour le spawn de retour
                if ("spawn".startsWith(current)) {
                    completions.add("spawn");
                }
                // Ajouter les noms des events
                for (GameEvent game : plugin.getGameManager().getGames()) {
                    if (game.getName().startsWith(current)) {
                        completions.add(game.getName());
                    }
                }
            } else if (subCommand.equals("setstreamer")) {
                // Complétion des joueurs en ligne
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(current)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String current = args[2].toLowerCase();

            // Pour setspawn tntlive et setstuff tntlive: streamer|sub
            if ((subCommand.equals("setspawn") || subCommand.equals("setstuff"))
                && args[1].equalsIgnoreCase("tntlive")) {
                if ("streamer".startsWith(current)) completions.add("streamer");
                if ("sub".startsWith(current)) completions.add("sub");
            }
        }

        return completions;
    }
}
