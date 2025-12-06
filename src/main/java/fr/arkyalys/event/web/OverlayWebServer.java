package fr.arkyalys.event.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import fr.arkyalys.event.YouTubeEventPlugin;
import fr.arkyalys.event.game.GameEvent;
import fr.arkyalys.event.game.GameState;
import fr.arkyalys.event.game.games.TNTLiveGame;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Serveur web intégré pour les overlays OBS
 * Fournit des endpoints HTML et JSON pour afficher les stats en temps réel
 */
public class OverlayWebServer {

    private final YouTubeEventPlugin plugin;
    private final Gson gson;
    private HttpServer server;
    private int port;
    private boolean running = false;

    // Milestones
    private static final int[] LIKE_MILESTONES = {10, 25, 50, 100, 200, 500, 1000};
    private static final int[] KILL_MILESTONES = {5, 10, 25, 50, 100};

    public OverlayWebServer(YouTubeEventPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Démarre le serveur web
     */
    public void start(int port) {
        if (running) {
            stop();
        }

        this.port = port;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            // Endpoints API JSON
            server.createContext("/api/stats", new StatsJsonHandler());
            server.createContext("/api/likes", new LikesJsonHandler());
            server.createContext("/api/kills", new KillsJsonHandler());

            // Endpoints Overlays HTML
            server.createContext("/overlay/likes", new LikesOverlayHandler());
            server.createContext("/overlay/kills", new KillsOverlayHandler());
            server.createContext("/overlay/stats", new StatsOverlayHandler());
            server.createContext("/overlay/milestone", new MilestoneOverlayHandler());
            server.createContext("/overlay/participants", new ParticipantsOverlayHandler());

            // Page d'accueil
            server.createContext("/", new IndexHandler());

            server.start();
            running = true;
            plugin.getLogger().info("Serveur web overlay demarre sur le port " + port);
            plugin.getLogger().info("Acces: http://localhost:" + port + "/");

        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de demarrer le serveur web: " + e.getMessage());
        }
    }

    /**
     * Arrête le serveur web
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            running = false;
            plugin.getLogger().info("Serveur web overlay arrete");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    // ==================== HELPERS ====================

    private GameStats getGameStats() {
        GameStats stats = new GameStats();

        GameEvent currentGame = plugin.getGameManager().getCurrentGame();
        if (currentGame == null) {
            stats.active = false;
            return stats;
        }

        stats.active = true;
        stats.gameName = currentGame.getDisplayName();
        stats.gameId = currentGame.getName();
        stats.status = currentGame.getState().name();

        if (currentGame instanceof TNTLiveGame) {
            TNTLiveGame tntGame = (TNTLiveGame) currentGame;
            stats.likes = tntGame.getTotalLikes();
            stats.kills = tntGame.getStreamerKills();
            stats.participants = tntGame.getAliveParticipants();
            stats.participantsTotal = tntGame.getTotalParticipants();
            stats.arrowsGiven = tntGame.getTotalArrowsGiven();
            stats.streamer = tntGame.getStreamerName();

            // Calcul des milestones
            stats.likesNext = getNextMilestone(stats.likes, LIKE_MILESTONES);
            stats.likesRemaining = stats.likesNext - stats.likes;
            stats.likesProgress = getMilestoneProgress(stats.likes, LIKE_MILESTONES);

            stats.killsNext = getNextMilestone(stats.kills, KILL_MILESTONES);
            stats.killsRemaining = stats.killsNext - stats.kills;
            stats.killsProgress = getMilestoneProgress(stats.kills, KILL_MILESTONES);
        }

        return stats;
    }

    private int getNextMilestone(int current, int[] milestones) {
        for (int milestone : milestones) {
            if (current < milestone) {
                return milestone;
            }
        }
        return milestones[milestones.length - 1] + 100;
    }

    private int getMilestoneProgress(int current, int[] milestones) {
        int previousMilestone = 0;
        for (int milestone : milestones) {
            if (current < milestone) {
                int range = milestone - previousMilestone;
                int progress = current - previousMilestone;
                return (int) ((progress / (double) range) * 100);
            }
            previousMilestone = milestone;
        }
        return 100;
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== DATA CLASSES ====================

    public static class GameStats {
        public boolean active = false;
        public String gameName = "Aucun";
        public String gameId = "none";
        public String status = "INACTIVE";
        public String streamer = "Aucun";
        public int likes = 0;
        public int likesNext = 10;
        public int likesRemaining = 10;
        public int likesProgress = 0;
        public int kills = 0;
        public int killsNext = 5;
        public int killsRemaining = 5;
        public int killsProgress = 0;
        public int participants = 0;
        public int participantsTotal = 0;
        public int arrowsGiven = 0;
    }

    // ==================== HANDLERS JSON ====================

    private class StatsJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            GameStats stats = getGameStats();
            sendResponse(exchange, 200, "application/json", gson.toJson(stats));
        }
    }

    private class LikesJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            GameStats stats = getGameStats();
            Map<String, Object> data = new HashMap<>();
            data.put("likes", stats.likes);
            data.put("next", stats.likesNext);
            data.put("remaining", stats.likesRemaining);
            data.put("progress", stats.likesProgress);
            sendResponse(exchange, 200, "application/json", gson.toJson(data));
        }
    }

    private class KillsJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            GameStats stats = getGameStats();
            Map<String, Object> data = new HashMap<>();
            data.put("kills", stats.kills);
            data.put("next", stats.killsNext);
            data.put("remaining", stats.killsRemaining);
            data.put("progress", stats.killsProgress);
            data.put("streamer", stats.streamer);
            sendResponse(exchange, 200, "application/json", gson.toJson(data));
        }
    }

    // ==================== HANDLERS HTML OVERLAYS ====================

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                    <meta charset="UTF-8">
                    <title>YouTubeEvent Overlays</title>
                    <style>
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
                            color: #fff;
                            padding: 40px;
                            min-height: 100vh;
                            margin: 0;
                        }
                        h1 { color: #ff4444; text-align: center; }
                        .container { max-width: 800px; margin: 0 auto; }
                        .card {
                            background: rgba(255,255,255,0.1);
                            border-radius: 15px;
                            padding: 20px;
                            margin: 20px 0;
                            backdrop-filter: blur(10px);
                        }
                        .card h2 { color: #ffd700; margin-top: 0; }
                        a {
                            color: #00d4ff;
                            text-decoration: none;
                            display: block;
                            padding: 10px;
                            margin: 5px 0;
                            background: rgba(0,212,255,0.1);
                            border-radius: 8px;
                            transition: all 0.3s;
                        }
                        a:hover {
                            background: rgba(0,212,255,0.3);
                            transform: translateX(10px);
                        }
                        .api { color: #00ff88; }
                        .api:hover { background: rgba(0,255,136,0.2); }
                        code {
                            background: rgba(0,0,0,0.3);
                            padding: 2px 8px;
                            border-radius: 4px;
                            font-size: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>YouTubeEvent - Overlays OBS</h1>

                        <div class="card">
                            <h2>Overlays HTML (pour OBS Browser Source)</h2>
                            <a href="/overlay/likes">/overlay/likes - Compteur de likes</a>
                            <a href="/overlay/kills">/overlay/kills - Compteur de kills</a>
                            <a href="/overlay/stats">/overlay/stats - Toutes les stats</a>
                            <a href="/overlay/milestone">/overlay/milestone - Prochain palier</a>
                            <a href="/overlay/participants">/overlay/participants - Participants</a>
                        </div>

                        <div class="card">
                            <h2>API JSON (pour developpeurs)</h2>
                            <a href="/api/stats" class="api">/api/stats - Toutes les stats</a>
                            <a href="/api/likes" class="api">/api/likes - Likes uniquement</a>
                            <a href="/api/kills" class="api">/api/kills - Kills uniquement</a>
                        </div>

                        <div class="card">
                            <h2>Utilisation OBS</h2>
                            <p>1. Ajouter une source <code>Navigateur</code></p>
                            <p>2. URL: <code>http://IP_SERVEUR:%d/overlay/likes</code></p>
                            <p>3. Largeur: 400, Hauteur: 150</p>
                            <p>4. Cocher "Actualiser le navigateur lorsque la scene devient active"</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(port);

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class LikesOverlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: transparent;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                        }
                        .container {
                            background: linear-gradient(135deg, rgba(255,68,68,0.9) 0%, rgba(200,50,50,0.9) 100%);
                            border-radius: 20px;
                            padding: 20px 40px;
                            text-align: center;
                            box-shadow: 0 10px 40px rgba(255,68,68,0.5);
                            animation: pulse 2s infinite;
                        }
                        @keyframes pulse {
                            0%, 100% { transform: scale(1); }
                            50% { transform: scale(1.02); }
                        }
                        .icon { font-size: 40px; margin-bottom: 5px; }
                        .count {
                            font-size: 72px;
                            font-weight: bold;
                            color: #fff;
                            text-shadow: 2px 2px 10px rgba(0,0,0,0.3);
                        }
                        .label {
                            font-size: 18px;
                            color: rgba(255,255,255,0.9);
                            text-transform: uppercase;
                            letter-spacing: 3px;
                        }
                        .updated { display: none; }
                        @keyframes pop {
                            0% { transform: scale(1); }
                            50% { transform: scale(1.3); }
                            100% { transform: scale(1); }
                        }
                        .pop { animation: pop 0.3s ease-out; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">❤️</div>
                        <div class="count" id="likes">0</div>
                        <div class="label">Likes</div>
                    </div>
                    <script>
                        let lastLikes = 0;
                        async function update() {
                            try {
                                const res = await fetch('/api/likes');
                                const data = await res.json();
                                const el = document.getElementById('likes');
                                if (data.likes !== lastLikes) {
                                    el.classList.remove('pop');
                                    void el.offsetWidth;
                                    el.classList.add('pop');
                                    lastLikes = data.likes;
                                }
                                el.textContent = data.likes;
                            } catch(e) {}
                        }
                        update();
                        setInterval(update, 1000);
                    </script>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class KillsOverlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: transparent;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                        }
                        .container {
                            background: linear-gradient(135deg, rgba(255,165,0,0.9) 0%, rgba(200,120,0,0.9) 100%);
                            border-radius: 20px;
                            padding: 20px 40px;
                            text-align: center;
                            box-shadow: 0 10px 40px rgba(255,165,0,0.5);
                        }
                        .icon { font-size: 40px; margin-bottom: 5px; }
                        .count {
                            font-size: 72px;
                            font-weight: bold;
                            color: #fff;
                            text-shadow: 2px 2px 10px rgba(0,0,0,0.3);
                        }
                        .label {
                            font-size: 18px;
                            color: rgba(255,255,255,0.9);
                            text-transform: uppercase;
                            letter-spacing: 3px;
                        }
                        .streamer {
                            font-size: 14px;
                            color: rgba(255,255,255,0.7);
                            margin-top: 5px;
                        }
                        @keyframes pop {
                            0% { transform: scale(1); }
                            50% { transform: scale(1.3); color: #ff0000; }
                            100% { transform: scale(1); }
                        }
                        .pop { animation: pop 0.3s ease-out; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">⚔️</div>
                        <div class="count" id="kills">0</div>
                        <div class="label">Kills</div>
                        <div class="streamer" id="streamer"></div>
                    </div>
                    <script>
                        let lastKills = 0;
                        async function update() {
                            try {
                                const res = await fetch('/api/kills');
                                const data = await res.json();
                                const el = document.getElementById('kills');
                                if (data.kills !== lastKills) {
                                    el.classList.remove('pop');
                                    void el.offsetWidth;
                                    el.classList.add('pop');
                                    lastKills = data.kills;
                                }
                                el.textContent = data.kills;
                                document.getElementById('streamer').textContent = data.streamer;
                            } catch(e) {}
                        }
                        update();
                        setInterval(update, 1000);
                    </script>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class StatsOverlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: transparent;
                            padding: 20px;
                        }
                        .container {
                            background: linear-gradient(135deg, rgba(26,26,46,0.95) 0%, rgba(22,33,62,0.95) 100%);
                            border-radius: 20px;
                            padding: 25px;
                            box-shadow: 0 10px 40px rgba(0,0,0,0.5);
                            border: 2px solid rgba(255,215,0,0.3);
                        }
                        .title {
                            text-align: center;
                            color: #ffd700;
                            font-size: 24px;
                            font-weight: bold;
                            margin-bottom: 20px;
                            text-shadow: 0 0 20px rgba(255,215,0,0.5);
                        }
                        .stats-grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 15px;
                        }
                        .stat-box {
                            background: rgba(255,255,255,0.1);
                            border-radius: 12px;
                            padding: 15px;
                            text-align: center;
                        }
                        .stat-icon { font-size: 24px; }
                        .stat-value {
                            font-size: 36px;
                            font-weight: bold;
                            color: #fff;
                        }
                        .stat-label {
                            font-size: 12px;
                            color: rgba(255,255,255,0.7);
                            text-transform: uppercase;
                        }
                        .likes .stat-value { color: #ff4444; }
                        .kills .stat-value { color: #ffa500; }
                        .participants .stat-value { color: #00d4ff; }
                        .arrows .stat-value { color: #00ff88; }
                        .streamer-bar {
                            margin-top: 15px;
                            text-align: center;
                            color: #fff;
                            font-size: 14px;
                        }
                        .streamer-name {
                            color: #ff4444;
                            font-weight: bold;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="title">⚡ TNT LIVE ⚡</div>
                        <div class="stats-grid">
                            <div class="stat-box likes">
                                <div class="stat-icon">❤️</div>
                                <div class="stat-value" id="likes">0</div>
                                <div class="stat-label">Likes</div>
                            </div>
                            <div class="stat-box kills">
                                <div class="stat-icon">⚔️</div>
                                <div class="stat-value" id="kills">0</div>
                                <div class="stat-label">Kills</div>
                            </div>
                            <div class="stat-box participants">
                                <div class="stat-icon">👥</div>
                                <div class="stat-value"><span id="participants">0</span>/<span id="total">0</span></div>
                                <div class="stat-label">En vie</div>
                            </div>
                            <div class="stat-box arrows">
                                <div class="stat-icon">🏹</div>
                                <div class="stat-value" id="arrows">0</div>
                                <div class="stat-label">Fleches</div>
                            </div>
                        </div>
                        <div class="streamer-bar">
                            Streamer: <span class="streamer-name" id="streamer">-</span>
                        </div>
                    </div>
                    <script>
                        async function update() {
                            try {
                                const res = await fetch('/api/stats');
                                const data = await res.json();
                                document.getElementById('likes').textContent = data.likes;
                                document.getElementById('kills').textContent = data.kills;
                                document.getElementById('participants').textContent = data.participants;
                                document.getElementById('total').textContent = data.participantsTotal;
                                document.getElementById('arrows').textContent = data.arrowsGiven;
                                document.getElementById('streamer').textContent = data.streamer;
                            } catch(e) {}
                        }
                        update();
                        setInterval(update, 1000);
                    </script>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class MilestoneOverlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: transparent;
                            padding: 20px;
                        }
                        .container {
                            background: linear-gradient(135deg, rgba(102,51,153,0.9) 0%, rgba(75,0,130,0.9) 100%);
                            border-radius: 20px;
                            padding: 25px;
                            box-shadow: 0 10px 40px rgba(102,51,153,0.5);
                        }
                        .milestone-title {
                            text-align: center;
                            color: #ffd700;
                            font-size: 16px;
                            margin-bottom: 10px;
                            text-transform: uppercase;
                            letter-spacing: 2px;
                        }
                        .milestone-target {
                            text-align: center;
                            font-size: 48px;
                            font-weight: bold;
                            color: #fff;
                            text-shadow: 0 0 20px rgba(255,255,255,0.5);
                        }
                        .progress-container {
                            margin-top: 15px;
                            background: rgba(0,0,0,0.3);
                            border-radius: 10px;
                            height: 20px;
                            overflow: hidden;
                        }
                        .progress-bar {
                            height: 100%;
                            background: linear-gradient(90deg, #ff4444, #ffd700);
                            border-radius: 10px;
                            transition: width 0.5s ease-out;
                            box-shadow: 0 0 20px rgba(255,215,0,0.5);
                        }
                        .progress-text {
                            text-align: center;
                            margin-top: 10px;
                            color: rgba(255,255,255,0.8);
                            font-size: 14px;
                        }
                        .current {
                            color: #ff4444;
                            font-weight: bold;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="milestone-title">🎯 Prochain Palier Likes</div>
                        <div class="milestone-target" id="target">10</div>
                        <div class="progress-container">
                            <div class="progress-bar" id="progress" style="width: 0%"></div>
                        </div>
                        <div class="progress-text">
                            <span class="current" id="current">0</span> / <span id="next">10</span> likes
                        </div>
                    </div>
                    <script>
                        async function update() {
                            try {
                                const res = await fetch('/api/likes');
                                const data = await res.json();
                                document.getElementById('target').textContent = data.next;
                                document.getElementById('current').textContent = data.likes;
                                document.getElementById('next').textContent = data.next;
                                document.getElementById('progress').style.width = data.progress + '%';
                            } catch(e) {}
                        }
                        update();
                        setInterval(update, 1000);
                    </script>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class ParticipantsOverlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Arial, sans-serif;
                            background: transparent;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                        }
                        .container {
                            background: linear-gradient(135deg, rgba(0,150,200,0.9) 0%, rgba(0,100,150,0.9) 100%);
                            border-radius: 20px;
                            padding: 20px 40px;
                            text-align: center;
                            box-shadow: 0 10px 40px rgba(0,150,200,0.5);
                        }
                        .icon { font-size: 40px; margin-bottom: 5px; }
                        .count {
                            font-size: 56px;
                            font-weight: bold;
                            color: #fff;
                            text-shadow: 2px 2px 10px rgba(0,0,0,0.3);
                        }
                        .total {
                            font-size: 28px;
                            color: rgba(255,255,255,0.7);
                        }
                        .label {
                            font-size: 16px;
                            color: rgba(255,255,255,0.9);
                            text-transform: uppercase;
                            letter-spacing: 2px;
                            margin-top: 5px;
                        }
                        .dead {
                            font-size: 14px;
                            color: #ff6b6b;
                            margin-top: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon">👥</div>
                        <div class="count">
                            <span id="alive">0</span><span class="total">/<span id="total">0</span></span>
                        </div>
                        <div class="label">Subs en vie</div>
                        <div class="dead">☠️ <span id="dead">0</span> elimines</div>
                    </div>
                    <script>
                        async function update() {
                            try {
                                const res = await fetch('/api/stats');
                                const data = await res.json();
                                document.getElementById('alive').textContent = data.participants;
                                document.getElementById('total').textContent = data.participantsTotal;
                                document.getElementById('dead').textContent = data.participantsTotal - data.participants;
                            } catch(e) {}
                        }
                        update();
                        setInterval(update, 1000);
                    </script>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, "text/html", html);
        }
    }
}
