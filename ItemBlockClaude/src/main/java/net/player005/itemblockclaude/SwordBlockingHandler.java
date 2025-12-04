package net.player005.itemblockclaude;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles adding/removing CONSUMABLE component with BLOCK animation to swords
 * Uses Paper's DataComponent API (1.21.4+)
 */
public class SwordBlockingHandler {

    private final JavaPlugin plugin;
    private boolean initialized = false;

    public SwordBlockingHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            // Vérifier que l'API Paper DataComponent est disponible
            Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            initialized = true;
            plugin.getLogger().info("Paper DataComponent API detected - sword blocking enabled!");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Paper DataComponent API not found! This plugin requires Paper 1.21.4+");
            initialized = false;
        }
    }

    /**
     * Add CONSUMABLE component with BLOCK animation to a sword
     */
    public void addBlockingComponent(ItemStack item) {
        if (!initialized || item == null || item.getType().isAir()) return;

        try {
            // Vérifier si déjà configuré
            if (item.hasData(DataComponentTypes.CONSUMABLE)) {
                return; // Already has component
            }

            // Créer un Consumable avec animation BLOCK et durée infinie
            Consumable consumable = Consumable.consumable()
                    .consumeSeconds(Float.MAX_VALUE) // Durée infinie (ne sera jamais consommé)
                    .animation(ItemUseAnimation.BLOCK) // Animation de blocage
                    .hasConsumeParticles(false) // Pas de particules
                    .build();

            // Appliquer le composant
            item.setData(DataComponentTypes.CONSUMABLE, consumable);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add blocking component: " + e.getMessage());
        }
    }

    /**
     * Remove CONSUMABLE component from a sword
     */
    public void removeBlockingComponent(ItemStack item) {
        if (!initialized || item == null || item.getType().isAir()) return;

        try {
            item.unsetData(DataComponentTypes.CONSUMABLE);
        } catch (Exception e) {
            // Ignore errors during removal
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
