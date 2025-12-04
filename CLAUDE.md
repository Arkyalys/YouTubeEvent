# YouTubeEvent Plugin

Plugin Minecraft connectant YouTube Live au serveur pour des events interactifs.

## Serveur Event (Deployment)

- **Host**: 89.82.2.154
- **Port**: 2022
- **User**: raynise.94a7ead7
- **Password**: Guiguidu16/01/2002+##+
- **Directory**: /plugins

### Commande de deploiement

```bash
sshpass -p 'Guiguidu16/01/2002+##+' sftp -o StrictHostKeyChecking=no -P 2022 raynise.94a7ead7@89.82.2.154 <<EOF
cd /plugins
put target/YouTubeEvent-1.0.0.jar
bye
EOF
```

## Build

```bash
mvn clean package
```

Le JAR se trouve dans `target/YouTubeEvent-1.0.0.jar`

---

## Architecture du Systeme d'Events

### Principes

- **Code propre** : Pas de code mort, imports minimaux
- **Modularite** : Chaque event = 1 classe Java + 1 fichier .yml configurable
- **Extensibilite** : Ajouter un event = creer une classe qui extends `GameEvent`

### Structure des fichiers

```
src/main/java/fr/arkyalys/event/
├── YouTubeEventPlugin.java      # Main class
├── game/
│   ├── GameState.java           # Enum: WAITING, OPEN, RUNNING, ENDED
│   ├── GameEvent.java           # Classe abstraite pour tous les events
│   ├── GameManager.java         # Gestionnaire + protections joueurs
│   └── games/
│       └── FeuilleGame.java     # Event "Feuille" (exemple)
├── commands/
│   ├── YouTubeCommand.java      # /youtube
│   └── EventCommand.java        # /event
├── youtube/
│   ├── YouTubeChatPoller.java   # Polling YouTube Live
│   ├── YouTubeDisplay.java      # BossBar/ActionBar
│   └── models/
│       └── ChatMessage.java     # Model message YouTube
└── config/
    └── ConfigManager.java       # Gestion config principale
```

### Fichiers de configuration

```
plugins/YouTubeEvent/
├── config.yml                   # Config principale (YouTube API, prefix, etc.)
└── events/
    └── feuille.yml              # Config specifique a l'event Feuille
```

---

## Creer un Nouvel Event

### 1. Creer la classe Java

```java
public class MonEventGame extends GameEvent {

    public MonEventGame(YouTubeEventPlugin plugin) {
        super(plugin, "monevent"); // Nom interne (lowercase)
    }

    @Override
    protected void loadEventConfig(YamlConfiguration config) {
        // Charger les configs specifiques depuis monevent.yml
    }

    @Override
    protected void saveDefaultEventConfig(YamlConfiguration config) {
        // Sauvegarder les configs par defaut
    }

    @Override
    protected void onOpen() { /* Event ouvert, joueurs peuvent join */ }

    @Override
    protected void onBegin() { /* Event demarre */ }

    @Override
    protected void onStop() { /* Event arrete */ }

    @Override
    protected void onPlayerJoin(Player player) { /* Joueur rejoint */ }

    @Override
    protected void onPlayerLeave(Player player) { /* Joueur quitte */ }

    @Override
    protected void onPlayerEliminated(Player player) { /* Joueur elimine */ }

    @Override
    protected void onWin(Player winner) { /* Gagnant declare */ }
}
```

### 2. Enregistrer dans GameManager

```java
// Dans GameManager.registerDefaultGames()
registerGame(new MonEventGame(plugin));
```

### 3. Le fichier .yml est cree automatiquement

Lors du premier chargement, `events/monevent.yml` sera cree avec les valeurs par defaut.

---

## Event Feuille - Implementation

### Concept
Les feuilles disparaissent via `randomTickSpeed` eleve. Dernier survivant gagne.
Les likes YouTube accelerent temporairement la disparition (boost).

### Mecaniques

| Fonctionnalite | Implementation |
|----------------|----------------|
| Disparition feuilles | `randomTickSpeed` du monde (pas de scan manuel) |
| Boost (like) | Augmente temporairement `randomTickSpeed` |
| Regeneration | WorldGuard regions - remplace AIR par feuilles |
| No drop | `LeavesDecayEvent` annule et remplace par AIR |

### Configuration (events/feuille.yml)

```yaml
# Timings
feuille:
  start-delay: 100              # Ticks avant debut disparition (5 sec)
  normal-tick-speed: 100        # Vitesse normale
  boost-tick-speed: 300         # Vitesse pendant boost
  boost-duration: 60            # Duree boost en ticks (3 sec)
  default-tick-speed: 3         # Reset a la fin (defaut MC)

  # WorldGuard regions pour regenerer les feuilles
  regions:
    - feuille1
    - feuille2

  # Type de feuille a regenerer
  leaf-material: OAK_LEAVES

# Triggers YouTube
youtube-triggers:
  like:
    - "broadcast &a+1 Like!"
    - "eco give %participant% 50"
  super-chat:
    - "broadcast &d[SUPER CHAT] &f%viewer% &7(%amount%)"
    - "eco give %participant% 500"
```

---

## Protections Joueurs (GameManager)

Quand un joueur est participant (et non-OP) :

| Protection | Description |
|------------|-------------|
| No PvP | `EntityDamageByEntityEvent` annule |
| No hunger | `FoodLevelChangeEvent` annule (perte seulement) |
| No block break | `BlockBreakEvent` annule |
| No block place | `BlockPlaceEvent` annule |
| No commands | Sauf `/event leave` et ses alias |

**Bypass** : Joueurs OP ignorent toutes les protections.

---

## Commandes

### /youtube
```
/youtube start              # Connecter au live
/youtube stop               # Deconnecter
/youtube status             # Voir statut connexion
/youtube setchannel <ID>    # Definir channel ID
/youtube settarget          # Se definir comme cible des triggers
/youtube auto start         # Detection auto du live
/youtube reload             # Recharger config
```

### /event
```
/event start <event>        # Ouvrir un event (2e appel = begin)
/event begin                # Lancer l'event ouvert
/event stop                 # Arreter l'event
/event list                 # Liste des events
/event join                 # Rejoindre l'event
/event leave                # Quitter l'event
/event setspawn <event>     # Definir le spawn
/event status               # Voir statut
/event reload               # Recharger configs
```

---

## YouTube API

- **API Key**: AIzaSyADjv7gbO5h0SdHiVofQouldA64h12PP5Q
- **Channel**: @RayniseG
- **Channel ID**: UCjezM7oxQpcZmR542BfcmFw

### Types de triggers YouTube

| Type | Variable | Description |
|------|----------|-------------|
| `message` | `%viewer%` | Message chat |
| `like` | `%amount%` (total) | Like sur le live |
| `super-chat` | `%viewer%`, `%amount%` | Super Chat |
| `super-sticker` | `%viewer%`, `%amount%` | Super Sticker |
| `new-member` | `%viewer%` | Nouveau membre |

### Placeholders dans les commandes

- `%viewer%` : Nom du viewer YouTube
- `%amount%` : Montant (super chat) ou total (likes)
- `%participant%` : Execute pour chaque participant de l'event
- `%all%` : Execute pour tous les joueurs du serveur

---

## BossBar/ActionBar

- **Visible uniquement aux participants** quand un event est actif
- **Le streamer (target)** voit toujours l'affichage
- Affiche : viewers, likes, messages du chat

---

## Dependencies

### Required
- Spigot/Paper 1.21+
- Java 21

### Optional (softdepend)
- **WorldGuard** : Regeneration des regions
- **Multiverse-Core** : Gestion des mondes

---

## Notes Techniques

### randomTickSpeed
- Valeur par defaut Minecraft : 3
- Valeur normale event : 100 (feuilles tombent progressivement)
- Valeur boost : 300 (feuilles tombent rapidement)
- Reset a la fin de l'event

### WorldGuard Integration
- Les regions doivent exister dans le monde de l'event
- Regeneration : parcourt tous les blocs AIR et les remplace par le materiau configure
- Supporte plusieurs regions (feuille1, feuille2, etc.)

### Cycle de vie d'un Event
```
WAITING -> OPEN (joueurs peuvent join) -> RUNNING (jeu en cours) -> ENDED
                                              |
                                              v
                                     Dernier joueur = Winner
```
