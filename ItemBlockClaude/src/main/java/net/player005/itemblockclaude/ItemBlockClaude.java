package net.player005.itemblockclaude;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ItemBlockClaude - Sword blocking with animation for Minecraft 1.21.1+
 * Uses CONSUMABLE component with BLOCK animation
 */
public class ItemBlockClaude extends JavaPlugin implements Listener {

    private double damageMultiplier = 0.5;
    private boolean debug = false;
    private SwordBlockingHandler blockingHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        damageMultiplier = getConfig().getDouble("sword-blocking-damage-multiplier", 0.5);
        debug = getConfig().getBoolean("debug", false);

        if (damageMultiplier < 0.0 || damageMultiplier > 1.0) {
            getLogger().warning("Invalid damage multiplier: " + damageMultiplier + ". Using default 0.5");
            damageMultiplier = 0.5;
        }

        // Initialize the blocking handler (uses Paper DataComponent API)
        blockingHandler = new SwordBlockingHandler(this);
        if (!blockingHandler.isInitialized()) {
            getLogger().severe("The plugin will not work correctly! Requires Paper 1.21.4+");
            return;
        }
        getLogger().info("Sword blocking handler initialized successfully!");

        getServer().getPluginManager().registerEvents(this, this);

        // Apply blocking to all online players (in case of reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            applySwordBlocking(player);
        }

        getLogger().info("ItemBlockClaude v1.1.0 enabled!");
        getLogger().info("Damage multiplier: " + damageMultiplier);
    }

    @Override
    public void onDisable() {
        // Remove blocking components from all online players
        if (blockingHandler != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeSwordBlocking(player);
            }
        }
        getLogger().info("ItemBlockClaude disabled.");
    }

    /**
     * When a player joins, make all their swords blockable
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySwordBlocking(event.getPlayer());
    }

    /**
     * When a player leaves, remove blocking components
     */
    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        removeSwordBlocking(event.getPlayer());
    }

    /**
     * When player changes held item, update sword components
     */
    @EventHandler
    public void onItemChange(PlayerItemHeldEvent event) {
        // Delay to ensure item is properly in hand
        Bukkit.getScheduler().runTaskLater(this, () -> {
            applySwordBlocking(event.getPlayer());
        }, 1L);
    }

    /**
     * Reduce damage when blocking with sword
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Check if player is blocking (using item / "eating" the sword)
        if (!isBlocking(player)) {
            return;
        }

        double originalDamage = event.getDamage();
        double newDamage = originalDamage * damageMultiplier;
        event.setDamage(newDamage);

        if (debug) {
            getLogger().info("[DEBUG] " + player.getName() + " blocked! Damage: " + originalDamage + " -> " + newDamage);
        }
    }

    /**
     * Check if player is currently blocking (using item with sword)
     */
    private boolean isBlocking(Player player) {
        if (!player.isHandRaised()) {
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        return isSword(item);
    }

    /**
     * Check if item is a sword
     */
    private boolean isSword(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("_SWORD");
    }

    /**
     * Apply blocking components to all swords in player's inventory
     */
    private void applySwordBlocking(Player player) {
        if (blockingHandler == null) return;

        PlayerInventory inv = player.getInventory();

        // Main hand
        ItemStack mainHand = inv.getItemInMainHand();
        if (isSword(mainHand)) {
            blockingHandler.addBlockingComponent(mainHand);
        }

        // Off hand
        ItemStack offHand = inv.getItemInOffHand();
        if (isSword(offHand)) {
            blockingHandler.addBlockingComponent(offHand);
        }

        // All inventory slots
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isSword(item)) {
                blockingHandler.addBlockingComponent(item);
            }
        }
    }

    /**
     * Remove blocking components from all swords in player's inventory
     */
    private void removeSwordBlocking(Player player) {
        if (blockingHandler == null) return;

        try {
            PlayerInventory inv = player.getInventory();

            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (isSword(item)) {
                    blockingHandler.removeBlockingComponent(item);
                }
            }

            ItemStack offHand = inv.getItemInOffHand();
            if (isSword(offHand)) {
                blockingHandler.removeBlockingComponent(offHand);
            }
        } catch (Exception e) {
            // Ignore errors during disconnect
        }
    }
}
