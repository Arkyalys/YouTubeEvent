package fr.arkyalys.event.commands;

import fr.arkyalys.event.YouTubeEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YouTubeCommand implements CommandExecutor, TabCompleter {

    private final YouTubeEventPlugin plugin;
    private final String prefix;

    public YouTubeCommand(YouTubeEventPlugin plugin) {
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
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            case "setkey" -> handleSetKey(sender, args);
            case "setchannel" -> handleSetChannel(sender, args);
            case "setusername" -> handleSetUsername(sender, args);
            case "settarget" -> handleSetTarget(sender, args);
            case "auto" -> handleAuto(sender, args);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender, args);
            case "scoreboard", "sb" -> handleScoreboard(sender, args);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage(prefix + "&cCommande inconnue. Utilisez /youtube help".replace("&", "\u00A7"));
            }
        }

        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (args.length < 2) {
            sender.sendMessage(prefix + "&cUtilisation: /youtube start <liveId ou URL>".replace("&", "\u00A7"));
            return;
        }

        if (plugin.getConfigManager().getApiKey().isEmpty() ||
            plugin.getConfigManager().getApiKey().equals("YOUR_API_KEY_HERE")) {
            sender.sendMessage(prefix + "&cCle API non configuree! Utilisez /youtube setkey <cle>".replace("&", "\u00A7"));
            return;
        }

        if (plugin.isConnected()) {
            sender.sendMessage(prefix + "&cDeja connecte a un live! Utilisez /youtube stop d'abord.".replace("&", "\u00A7"));
            return;
        }

        // Si le sender est un joueur et qu'il n'y a pas de cible, le définir comme cible
        if (sender instanceof Player player && plugin.getTargetPlayer() == null) {
            plugin.setTargetPlayer(player);
            sender.sendMessage(prefix + "&aVous etes defini comme joueur cible des evenements.".replace("&", "\u00A7"));
        }

        String liveId = args[1];
        sender.sendMessage(prefix + "&eConnexion au live YouTube...".replace("&", "\u00A7"));

        if (plugin.startLive(liveId)) {
            sender.sendMessage(prefix + "&aConnexion en cours, veuillez patienter...".replace("&", "\u00A7"));
        } else {
            sender.sendMessage(prefix + "&cErreur lors de la connexion au live.".replace("&", "\u00A7"));
        }
    }

    private void handleStop(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (!plugin.isConnected()) {
            sender.sendMessage(prefix + "&cPas de connexion active.".replace("&", "\u00A7"));
            return;
        }

        plugin.stopLive();
        sender.sendMessage(prefix + "&aConnexion arretee.".replace("&", "\u00A7"));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("&6========== &eYouTubeEvent Status &6==========".replace("&", "\u00A7"));

        boolean connected = plugin.isConnected();
        sender.sendMessage(("&7Statut: " + (connected ? "&aConnecte" : "&cDeconnecte")).replace("&", "\u00A7"));

        if (connected) {
            String liveId = plugin.getLiveChatPoller().getCurrentLiveId();
            String uptime = plugin.getLiveChatPoller().getUptimeFormatted();
            int messages = plugin.getLiveChatPoller().getTotalMessagesReceived();

            sender.sendMessage(("&7Live ID: &f" + liveId).replace("&", "\u00A7"));
            sender.sendMessage(("&7Uptime: &f" + uptime).replace("&", "\u00A7"));
            sender.sendMessage(("&7Messages recus: &f" + messages).replace("&", "\u00A7"));
        }

        Player target = plugin.getTargetPlayer();
        sender.sendMessage(("&7Joueur cible: " + (target != null ? "&a" + target.getName() : "&cAucun")).replace("&", "\u00A7"));

        String apiStatus = plugin.getConfigManager().getApiKey().isEmpty() ||
                           plugin.getConfigManager().getApiKey().equals("YOUR_API_KEY_HERE")
                           ? "&cNon configuree" : "&aConfiguree";
        sender.sendMessage(("&7Cle API: " + apiStatus).replace("&", "\u00A7"));

        // Afficher le channel username pour la detection
        String username = plugin.getConfigManager().getChannelUsername();
        sender.sendMessage(("&7@Username: " + (username.isEmpty() ? "&cNon configure" : "&a@" + username)).replace("&", "\u00A7"));

        String channelId = plugin.getConfigManager().getChannelId();
        sender.sendMessage(("&7Channel ID: " + (channelId.isEmpty() ? "&cNon configure" : "&f" + channelId)).replace("&", "\u00A7"));

        sender.sendMessage("&6==========================================".replace("&", "\u00A7"));
    }

    private void handleSetKey(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (args.length < 2) {
            sender.sendMessage(prefix + "&cUtilisation: /youtube setkey <cle API>".replace("&", "\u00A7"));
            return;
        }

        String apiKey = args[1];
        plugin.getConfigManager().setApiKey(apiKey);
        sender.sendMessage(prefix + "&aCle API configuree avec succes!".replace("&", "\u00A7"));
    }

    private void handleSetChannel(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (args.length < 2) {
            sender.sendMessage(prefix + "&cUtilisation: /youtube setchannel <channel ID>".replace("&", "\u00A7"));
            sender.sendMessage(prefix + "&7Trouvez votre ID sur: &fhttps://www.youtube.com/account_advanced".replace("&", "\u00A7"));
            sender.sendMessage(prefix + "&7Format: &fUCxxxxxxxxxxxxxxxxxxxxxxxx".replace("&", "\u00A7"));
            return;
        }

        String channelId = args[1];

        // Vérifier le format
        if (!channelId.startsWith("UC") || channelId.length() != 24) {
            sender.sendMessage(prefix + "&eAttention: L'ID de chaine devrait commencer par 'UC' et faire 24 caracteres.".replace("&", "\u00A7"));
        }

        plugin.getConfigManager().setChannelId(channelId);
        sender.sendMessage(prefix + "&aChannel ID configure: &f" + channelId.replace("&", "\u00A7"));
        sender.sendMessage(prefix + "&7Utilisez &f/youtube auto start &7pour activer la detection automatique.".replace("&", "\u00A7"));
    }

    private void handleSetUsername(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (args.length < 2) {
            sender.sendMessage(prefix + "&cUtilisation: /youtube setusername <@username>".replace("&", "\u00A7"));
            sender.sendMessage(prefix + "&7Exemple: &f/youtube setusername RayniseG".replace("&", "\u00A7"));
            sender.sendMessage(prefix + "&7(le @ est optionnel)".replace("&", "\u00A7"));
            return;
        }

        String username = args[1];
        // Enlever le @ si présent
        if (username.startsWith("@")) {
            username = username.substring(1);
        }

        plugin.getConfigManager().setChannelUsername(username);
        sender.sendMessage(prefix + "&aUsername configure: &f@" + username.replace("&", "\u00A7"));
        sender.sendMessage(prefix + "&7La detection automatique utilisera cette methode (plus fiable que l'API).".replace("&", "\u00A7"));
    }

    private void handleAuto(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        String action = args.length > 1 ? args[1].toLowerCase() : "status";

        switch (action) {
            case "start" -> {
                String channelId = plugin.getConfigManager().getChannelId();
                if (channelId.isEmpty()) {
                    sender.sendMessage(prefix + "&cChannel ID non configure! Utilisez /youtube setchannel <id>".replace("&", "\u00A7"));
                    return;
                }

                if (plugin.getAutoDetector().isRunning()) {
                    sender.sendMessage(prefix + "&eDetection automatique deja active.".replace("&", "\u00A7"));
                    return;
                }

                // S'assurer qu'il y a une cible
                if (sender instanceof Player player && plugin.getTargetPlayer() == null) {
                    plugin.setTargetPlayer(player);
                }

                plugin.getAutoDetector().start();
                sender.sendMessage(prefix + "&aDetection automatique activee!".replace("&", "\u00A7"));
                sender.sendMessage(prefix + "&7Le plugin se connectera automatiquement quand tu seras en live.".replace("&", "\u00A7"));
            }
            case "stop" -> {
                if (!plugin.getAutoDetector().isRunning()) {
                    sender.sendMessage(prefix + "&eDetection automatique deja desactivee.".replace("&", "\u00A7"));
                    return;
                }

                plugin.getAutoDetector().stop();
                sender.sendMessage(prefix + "&cDetection automatique desactivee.".replace("&", "\u00A7"));
            }
            default -> {
                boolean running = plugin.getAutoDetector().isRunning();
                String channelId = plugin.getConfigManager().getChannelId();

                sender.sendMessage("&6=== Detection Automatique ===".replace("&", "\u00A7"));
                sender.sendMessage(("&7Statut: " + (running ? "&aActive" : "&cDesactive")).replace("&", "\u00A7"));
                sender.sendMessage(("&7Channel ID: " + (channelId.isEmpty() ? "&cNon configure" : "&f" + channelId)).replace("&", "\u00A7"));
                sender.sendMessage("&7Commandes: &f/youtube auto start|stop".replace("&", "\u00A7"));
            }
        }
    }

    private void handleSetTarget(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (args.length < 2) {
            // Si le sender est un joueur, le définir comme cible
            if (sender instanceof Player player) {
                plugin.setTargetPlayer(player);
                sender.sendMessage(prefix + "&aVous etes maintenant la cible des evenements.".replace("&", "\u00A7"));
            } else {
                sender.sendMessage(prefix + "&cUtilisation: /youtube settarget <joueur>".replace("&", "\u00A7"));
            }
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(prefix + "&cJoueur non trouve: " + playerName.replace("&", "\u00A7"));
            return;
        }

        plugin.setTargetPlayer(target);
        sender.sendMessage((prefix + "&a" + target.getName() + " est maintenant la cible des evenements.").replace("&", "\u00A7"));
    }

    private void handleReload(CommandSender sender) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        plugin.reload();
        sender.sendMessage(prefix + "&aConfiguration rechargee!".replace("&", "\u00A7"));
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "youtubeevent.admin")) return;

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + "&cCette commande doit etre executee par un joueur.".replace("&", "\u00A7"));
            return;
        }

        String testType = args.length > 1 ? args[1].toLowerCase() : "message";

        sender.sendMessage(prefix + "&eSimulation d'un evenement YouTube...".replace("&", "\u00A7"));

        // S'assurer que le joueur est la cible
        plugin.setTargetPlayer(player);

        switch (testType) {
            case "superchat" -> {
                var fakeMessage = fr.arkyalys.event.youtube.models.ChatMessage.superChat(
                        "test-" + System.currentTimeMillis(),
                        "test-channel",
                        "TestViewer",
                        "",
                        "Super Chat de test!",
                        System.currentTimeMillis(),
                        "10,00 €",
                        10_000_000L,
                        "EUR",
                        false, false, true // isModerator, isOwner, isSponsor (SuperChat = sponsor)
                );
                plugin.getEventManager().handleMessage(fakeMessage);
            }
            case "supersticker" -> {
                var fakeMessage = fr.arkyalys.event.youtube.models.ChatMessage.superSticker(
                        "test-" + System.currentTimeMillis(),
                        "test-channel",
                        "TestViewer",
                        "",
                        System.currentTimeMillis(),
                        "5,00 €",
                        5_000_000L,
                        "EUR",
                        false, false, true // isModerator, isOwner, isSponsor
                );
                plugin.getEventManager().handleMessage(fakeMessage);
            }
            case "member" -> {
                var fakeMessage = fr.arkyalys.event.youtube.models.ChatMessage.newMember(
                        "test-" + System.currentTimeMillis(),
                        "test-channel",
                        "TestMember",
                        "",
                        System.currentTimeMillis(),
                        false, false, true // isModerator, isOwner, isSponsor (nouveau membre = sponsor)
                );
                plugin.getEventManager().handleMessage(fakeMessage);
            }
            case "like" -> {
                // Simule 1 nouveau like avec un total de 100 likes
                long newLikes = args.length > 2 ? Long.parseLong(args[2]) : 1;
                long totalLikes = args.length > 3 ? Long.parseLong(args[3]) : 100;
                plugin.getEventManager().handleLike(newLikes, totalLikes);
                sender.sendMessage((prefix + "&7Simule: +" + newLikes + " like(s), total: " + totalLikes).replace("&", "\u00A7"));
            }
            case "viewmilestone" -> {
                // Simule un palier de vues atteint
                long viewCount = args.length > 2 ? Long.parseLong(args[2]) : 1000;
                plugin.getEventManager().handleViewMilestone(viewCount);
                sender.sendMessage((prefix + "&7Simule: " + viewCount + " vues atteintes!").replace("&", "\u00A7"));
            }
            default -> {
                // Message normal
                var fakeMessage = fr.arkyalys.event.youtube.models.ChatMessage.normalMessage(
                        "test-" + System.currentTimeMillis(),
                        "test-channel",
                        "TestViewer",
                        "",
                        args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Message de test!",
                        System.currentTimeMillis(),
                        false, false, false // isModerator, isOwner, isSponsor
                );
                plugin.getEventManager().handleMessage(fakeMessage);
            }
        }

        sender.sendMessage((prefix + "&aEvenement simule: &f" + testType).replace("&", "\u00A7"));
    }

    private void handleScoreboard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + "&cCette commande doit etre executee par un joueur.".replace("&", "\u00A7"));
            return;
        }

        String action = args.length > 1 ? args[1].toLowerCase() : "toggle";

        switch (action) {
            case "on", "show" -> {
                plugin.getDisplay().show(player);
                sender.sendMessage((prefix + "&aAffichage active!").replace("&", "\u00A7"));
            }
            case "off", "hide" -> {
                plugin.getDisplay().hide(player);
                sender.sendMessage((prefix + "&eAffichage desactive.").replace("&", "\u00A7"));
            }
            case "all" -> {
                if (!hasPermission(sender, "youtubeevent.admin")) return;
                plugin.getDisplay().showAll();
                sender.sendMessage((prefix + "&aAffichage active pour tous les joueurs!").replace("&", "\u00A7"));
            }
            default -> {
                // Toggle
                plugin.getDisplay().toggle(player);
                if (plugin.getDisplay().isViewing(player)) {
                    sender.sendMessage((prefix + "&aAffichage active!").replace("&", "\u00A7"));
                } else {
                    sender.sendMessage((prefix + "&eAffichage desactive.").replace("&", "\u00A7"));
                }
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("&6========== &eYouTubeEvent &6==========".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube start <liveId> &7- Connecter a un live".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube stop &7- Arreter la connexion".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube status &7- Voir le statut".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube setkey <cle> &7- Definir la cle API".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube setchannel <id> &7- Definir ta chaine (UCxxxx)".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube setusername <@user> &7- Definir ton @username &a(recommande!)".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube auto [start|stop] &7- Detection auto".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube settarget [joueur] &7- Definir la cible".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube reload &7- Recharger la config".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube test [type] &7- Tester un evenement".replace("&", "\u00A7"));
        sender.sendMessage("&7  Types: message, superchat, supersticker, member, like, viewmilestone".replace("&", "\u00A7"));
        sender.sendMessage("&e/youtube scoreboard &7- Toggle le scoreboard".replace("&", "\u00A7"));
        sender.sendMessage("&6=====================================".replace("&", "\u00A7"));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(prefix + "&cVous n'avez pas la permission.".replace("&", "\u00A7"));
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = Arrays.asList("start", "stop", "status", "setkey", "setchannel", "setusername", "auto", "settarget", "reload", "test", "scoreboard", "help");
            String current = args[0].toLowerCase();
            for (String cmd : commands) {
                if (cmd.startsWith(current)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("settarget")) {
                String current = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(current)) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCommand.equals("test")) {
                List<String> types = Arrays.asList("message", "superchat", "supersticker", "member", "like", "viewmilestone");
                String current = args[1].toLowerCase();
                for (String type : types) {
                    if (type.startsWith(current)) {
                        completions.add(type);
                    }
                }
            } else if (subCommand.equals("auto")) {
                List<String> actions = Arrays.asList("start", "stop", "status");
                String current = args[1].toLowerCase();
                for (String action : actions) {
                    if (action.startsWith(current)) {
                        completions.add(action);
                    }
                }
            } else if (subCommand.equals("scoreboard") || subCommand.equals("sb")) {
                List<String> actions = Arrays.asList("on", "off", "all");
                String current = args[1].toLowerCase();
                for (String action : actions) {
                    if (action.startsWith(current)) {
                        completions.add(action);
                    }
                }
            }
        }

        return completions;
    }
}
