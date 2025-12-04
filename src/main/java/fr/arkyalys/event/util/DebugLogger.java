package fr.arkyalys.event.util;

import fr.arkyalys.event.YouTubeEventPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger de debug qui écrit dans un fichier séparé
 */
public class DebugLogger {

    private static YouTubeEventPlugin plugin;
    private static File logFile;
    private static boolean enabled = true;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void init(YouTubeEventPlugin pluginInstance) {
        plugin = pluginInstance;
        logFile = new File(plugin.getDataFolder(), "debug.log");

        // Créer le fichier si nécessaire
        try {
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de créer le fichier debug.log");
        }

        log("=== Debug Logger initialisé ===");
    }

    public static void log(String message) {
        if (!enabled || logFile == null) return;

        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String line = "[" + timestamp + "] " + message;

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(line);
        } catch (IOException e) {
            // Silently fail
        }
    }

    public static void log(String category, String message) {
        log("[" + category + "] " + message);
    }

    public static void logEvent(String eventName, String action) {
        log("EVENT", eventName + " -> " + action);
    }

    public static void logDisplay(String action) {
        log("DISPLAY", action);
    }

    public static void logYouTube(String action) {
        log("YOUTUBE", action);
    }

    public static void logGame(String gameName, String action) {
        log("GAME:" + gameName, action);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Vide le fichier de log
     */
    public static void clear() {
        if (logFile == null) return;

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) {
            writer.println("=== Log cleared at " + LocalDateTime.now() + " ===");
        } catch (IOException e) {
            // Silently fail
        }
    }
}
