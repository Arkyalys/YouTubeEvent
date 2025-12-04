package fr.arkyalys.event.events;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.api.events.*;
import fr.arkyalys.event.config.ConfigManager;
import fr.arkyalys.event.events.triggers.*;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le dispatch des événements YouTube vers les triggers in-game
 */
public class EventManager {

    private final YouTubeEventPlugin plugin;
    private final Random random = new Random();

    // Triggers disponibles
    private final Map<String, ActionTrigger> triggers = new HashMap<>();

    // Cooldowns par type d'événement (viewer -> timestamp)
    private final Map<String, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public EventManager(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        registerTriggers();
    }

    /**
     * Enregistre tous les triggers disponibles
     */
    private void registerTriggers() {
        triggers.put("SPAWN_MOB", new MobSpawnTrigger(plugin));
        triggers.put("GIVE_ITEM", new ItemGiveTrigger(plugin));
        triggers.put("EFFECT", new EffectTrigger(plugin));
        triggers.put("WORLD_EVENT", new WorldEventTrigger(plugin));
        triggers.put("COMMAND", new CommandTrigger(plugin));
        triggers.put("BROADCAST", new BroadcastTrigger(plugin));
        triggers.put("SOUND", new SoundTrigger(plugin));
        triggers.put("PARTICLE", new ParticleTrigger(plugin));
    }

    public void reload() {
        cooldowns.clear();
    }

    /**
     * Traite un message YouTube et déclenche les événements appropriés
     */
    public void handleMessage(ChatMessage message) {
        String liveId = plugin.getLiveChatPoller().getCurrentLiveId();

        // Déclencher l'événement Bukkit général (pour tous les messages)
        YouTubeChatMessageEvent chatEvent = new YouTubeChatMessageEvent(liveId, message);
        Bukkit.getPluginManager().callEvent(chatEvent);

        // Si annulé par un autre plugin, ne pas traiter
        if (chatEvent.isCancelled()) {
            return;
        }

        Player target = plugin.getTargetPlayer();
        if (target == null || !target.isOnline()) {
            return; // Pas de joueur cible
        }

        ConfigManager config = plugin.getConfigManager();

        // Traiter selon le type de message
        switch (message.getType()) {
            case TEXT_MESSAGE:
                handleChatMessage(message, target, config);
                break;

            case SUPER_CHAT:
                handleSuperChat(message, target, config, liveId);
                break;

            case SUPER_STICKER:
                handleSuperSticker(message, target, config, liveId);
                break;

            case NEW_MEMBER:
                handleNewMember(message, target, config, liveId);
                break;
        }

        // Toujours vérifier les mots-clés
        checkKeywords(message, target, config);
    }

    /**
     * Gère un message chat normal
     */
    private void handleChatMessage(ChatMessage message, Player target, ConfigManager config) {
        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("chat-message");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        if (!checkChanceAndCooldown(triggerConfig, "chat-message", message.getAuthorChannelId())) {
            return;
        }

        executeActions(triggerConfig, message, target);
    }

    /**
     * Gère un Super Chat
     */
    private void handleSuperChat(ChatMessage message, Player target, ConfigManager config, String liveId) {
        // Déclencher l'événement Bukkit spécifique
        YouTubeSuperChatEvent superChatEvent = new YouTubeSuperChatEvent(liveId, message);
        Bukkit.getPluginManager().callEvent(superChatEvent);
        if (superChatEvent.isCancelled()) return;

        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("super-chat");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        executeActions(triggerConfig, message, target);

        // Afficher le Super Chat au joueur
        String scMessage = config.getMessageSuperChat()
                .replace("%viewer%", message.getAuthorName())
                .replace("%message%", message.getMessage())
                .replace("%amount%", message.getAmountDisplay());
        target.sendMessage(config.getPrefix() + scMessage);
    }

    /**
     * Gère un Super Sticker
     */
    private void handleSuperSticker(ChatMessage message, Player target, ConfigManager config, String liveId) {
        // Déclencher l'événement Bukkit spécifique (utilise le même event que SuperChat)
        YouTubeSuperChatEvent superChatEvent = new YouTubeSuperChatEvent(liveId, message);
        Bukkit.getPluginManager().callEvent(superChatEvent);
        if (superChatEvent.isCancelled()) return;

        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("super-sticker");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        executeActions(triggerConfig, message, target);
    }

    /**
     * Gère un nouveau membre
     */
    private void handleNewMember(ChatMessage message, Player target, ConfigManager config, String liveId) {
        // Déclencher l'événement Bukkit spécifique
        YouTubeNewMemberEvent newMemberEvent = new YouTubeNewMemberEvent(liveId, message);
        Bukkit.getPluginManager().callEvent(newMemberEvent);
        if (newMemberEvent.isCancelled()) return;

        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("new-member");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        executeActions(triggerConfig, message, target);
    }

    /**
     * Gère les nouveaux likes
     */
    public void handleLike(long newLikes, long totalLikes) {
        String liveId = plugin.getLiveChatPoller().getCurrentLiveId();

        // Déclencher l'événement Bukkit
        YouTubeLikeEvent likeEvent = new YouTubeLikeEvent(liveId, newLikes, totalLikes);
        Bukkit.getPluginManager().callEvent(likeEvent);
        if (likeEvent.isCancelled()) return;

        Player target = plugin.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        ConfigManager config = plugin.getConfigManager();
        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("like");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        // Créer un faux message pour les placeholders
        ChatMessage likeMessage = ChatMessage.normalMessage(
                "like-" + System.currentTimeMillis(),
                "youtube",
                "+" + newLikes + " like(s)",
                "",
                String.valueOf(totalLikes),
                System.currentTimeMillis(),
                false, false, false // isModerator, isOwner, isSponsor
        );

        executeActions(triggerConfig, likeMessage, target);
    }

    /**
     * Gère les paliers de vues
     */
    public void handleViewMilestone(long viewCount) {
        String liveId = plugin.getLiveChatPoller().getCurrentLiveId();
        int milestone = plugin.getConfigManager().getViewMilestone();

        // Déclencher l'événement Bukkit
        YouTubeViewMilestoneEvent viewEvent = new YouTubeViewMilestoneEvent(liveId, viewCount, milestone);
        Bukkit.getPluginManager().callEvent(viewEvent);
        if (viewEvent.isCancelled()) return;

        Player target = plugin.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        ConfigManager config = plugin.getConfigManager();
        ConfigManager.TriggerConfig triggerConfig = config.getTrigger("view-milestone");
        if (triggerConfig == null || !triggerConfig.enabled) return;

        ChatMessage viewMessage = ChatMessage.normalMessage(
                "view-" + System.currentTimeMillis(),
                "youtube",
                viewCount + " vues!",
                "",
                String.valueOf(viewCount),
                System.currentTimeMillis(),
                false, false, false // isModerator, isOwner, isSponsor
        );

        executeActions(triggerConfig, viewMessage, target);
    }

    /**
     * Vérifie les mots-clés dans le message
     */
    private void checkKeywords(ChatMessage message, Player target, ConfigManager config) {
        if (message.getMessage() == null || message.getMessage().isEmpty()) return;

        String lowerMessage = message.getMessage().toLowerCase();

        for (Map.Entry<String, ConfigManager.TriggerConfig> entry : config.getKeywordTriggers().entrySet()) {
            String keyword = entry.getKey();
            ConfigManager.TriggerConfig triggerConfig = entry.getValue();

            if (!triggerConfig.enabled) continue;

            if (lowerMessage.contains(keyword)) {
                if (!checkChanceAndCooldown(triggerConfig, "keyword:" + keyword, message.getAuthorChannelId())) {
                    continue;
                }

                executeActions(triggerConfig, message, target);
            }
        }
    }

    /**
     * Vérifie la chance et le cooldown d'un trigger
     */
    private boolean checkChanceAndCooldown(ConfigManager.TriggerConfig config, String triggerType, String viewerId) {
        // Vérifier la chance
        if (config.chance < 100.0) {
            if (random.nextDouble() * 100 > config.chance) {
                return false;
            }
        }

        // Vérifier le cooldown
        if (config.cooldown > 0) {
            Map<String, Long> typeCooldowns = cooldowns.computeIfAbsent(triggerType, k -> new ConcurrentHashMap<>());
            Long lastTrigger = typeCooldowns.get(viewerId);

            if (lastTrigger != null) {
                long elapsed = System.currentTimeMillis() - lastTrigger;
                if (elapsed < config.cooldown * 1000L) {
                    return false;
                }
            }

            typeCooldowns.put(viewerId, System.currentTimeMillis());
        }

        return true;
    }

    /**
     * Exécute les actions d'un trigger
     */
    private void executeActions(ConfigManager.TriggerConfig config, ChatMessage message, Player target) {
        if (config.actions == null) return;

        for (Map<?, ?> actionMap : config.actions) {
            try {
                String type = (String) actionMap.get("type");
                ActionTrigger trigger = triggers.get(type);

                if (trigger == null) {
                    plugin.getLogger().warning("Type d'action inconnu: " + type);
                    continue;
                }

                // Vérifier le montant minimum pour les super chats
                if (actionMap.containsKey("min-amount") && message.isSuperChat()) {
                    double minAmount = ((Number) actionMap.get("min-amount")).doubleValue();
                    if (message.getAmountValue() < minAmount) {
                        continue;
                    }
                }

                // Exécuter l'action
                trigger.execute(actionMap, message, target);

            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de l'execution d'une action: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
