package fr.arkyalys.event.events.triggers;

import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.youtube.models.ChatMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Donne des items au joueur
 */
public class ItemGiveTrigger implements ActionTrigger {

    private final YouTubeEventPlugin plugin;

    public ItemGiveTrigger(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Map<?, ?> config, ChatMessage message, Player target) {
        String itemName = getString(config, "item", "DIAMOND").toUpperCase();
        int amount = getInt(config, "amount", 1);
        String displayName = getString(config, "display-name", null);
        boolean drop = getBoolean(config, "drop", false);

        Material material;
        try {
            material = Material.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type d'item invalide: " + itemName);
            return;
        }

        ItemStack item = new ItemStack(material, amount);

        // Meta
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Nom custom
            if (displayName != null) {
                String name = replacePlaceholders(displayName, message, target);
                meta.setDisplayName(name.replace("&", "\u00A7"));
            }

            // Lore
            Object loreObj = config.get("lore");
            if (loreObj instanceof List<?> loreList) {
                List<String> lore = new ArrayList<>();
                for (Object line : loreList) {
                    String loreLine = replacePlaceholders(line.toString(), message, target);
                    lore.add(loreLine.replace("&", "\u00A7"));
                }
                meta.setLore(lore);
            }

            // Enchantements
            Object enchObj = config.get("enchantments");
            if (enchObj instanceof Map<?, ?> enchMap) {
                for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                    try {
                        Enchantment enchantment = Enchantment.getByName(entry.getKey().toString().toUpperCase());
                        int level = ((Number) entry.getValue()).intValue();
                        if (enchantment != null) {
                            meta.addEnchant(enchantment, level, true);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Enchantement invalide: " + entry.getKey());
                    }
                }
            }

            item.setItemMeta(meta);
        }

        // Donner ou drop l'item
        if (drop) {
            target.getWorld().dropItemNaturally(target.getLocation(), item);
        } else {
            // Essayer d'ajouter à l'inventaire, sinon drop
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack remaining : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), remaining);
                }
            }
        }

        plugin.getLogger().info("Item donne: " + amount + "x " + itemName + " pour " + message.getAuthorName());
    }
}
