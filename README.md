# YouTubeEvent

Plugin Minecraft Spigot/Paper qui connecte YouTube Live a votre serveur pour declencher des evenements in-game bases sur les interactions des viewers.

## Fonctionnalites

- **Connexion au chat YouTube Live** en temps reel
- **Zero quota API** grace a InnerTube (methode gratuite)
- **Detection automatique** quand la chaine est en live
- **Triggers configurables** pour tous les types d'interactions :
  - Messages chat normaux
  - Super Chats / Super Stickers
  - Nouveaux membres
  - Likes
  - Paliers de vues
  - Mots-cles specifiques
- **Actions variees** : spawn mobs, give items, effets, commandes, broadcasts, sons, particules
- **API publique** pour integration avec d'autres plugins
- **Evenements Bukkit** pour les developpeurs

## Installation

1. Telecharger `YouTubeEvent-1.0.0.jar`
2. Placer dans le dossier `plugins/` de votre serveur
3. Redemarrer le serveur
4. Configurer la cle API YouTube dans `config.yml` ou avec `/youtube setkey <cle>`

## Configuration Rapide

```bash
# 1. Configurer la cle API YouTube
/youtube setkey AIzaSy...

# 2. Configurer l'ID de votre chaine (pour detection auto)
/youtube setchannel UCjezM7oxQpcZmR542BfcmFw

# 3. Vous definir comme joueur cible
/youtube settarget

# 4. Activer la detection automatique
/youtube auto start
```

Le plugin detectera automatiquement quand vous etes en live et se connectera.

## Commandes

| Commande | Description | Permission |
|----------|-------------|------------|
| `/youtube start <liveId>` | Connecter a un live manuellement | `youtubeevent.admin` |
| `/youtube stop` | Arreter la connexion | `youtubeevent.admin` |
| `/youtube status` | Voir le statut actuel | `youtubeevent.use` |
| `/youtube setkey <cle>` | Definir la cle API YouTube | `youtubeevent.admin` |
| `/youtube setchannel <id>` | Definir l'ID de la chaine | `youtubeevent.admin` |
| `/youtube settarget [joueur]` | Definir le joueur cible | `youtubeevent.admin` |
| `/youtube auto start\|stop` | Activer/desactiver detection auto | `youtubeevent.admin` |
| `/youtube reload` | Recharger la configuration | `youtubeevent.admin` |
| `/youtube test <type>` | Tester un evenement | `youtubeevent.admin` |
| `/youtube scoreboard` | Toggle l'affichage | `youtubeevent.use` |

### Types de test disponibles
- `message` - Message chat normal
- `superchat` - Super Chat
- `supersticker` - Super Sticker
- `member` - Nouveau membre
- `like [nombre] [total]` - Like(s)
- `viewmilestone [nombre]` - Palier de vues

## Configuration (config.yml)

### YouTube API

```yaml
youtube:
  # Cle API YouTube (Google Cloud Console)
  api-key: "YOUR_API_KEY_HERE"

  # ID de votre chaine YouTube
  channel-id: "UCjezM7oxQpcZmR542BfcmFw"

  # Intervalle detection auto (secondes, 0 = desactive)
  auto-detect-interval: 30

  # Intervalle de polling chat (secondes)
  poll-interval: 3

  # InnerTube (0 quota) en priorite
  prefer-innertube: true

  # Fallback sur Data API si InnerTube echoue
  fallback-to-api: true
```

### Triggers

Les triggers definissent les actions a executer quand un evenement YouTube se produit.

```yaml
triggers:
  # Messages chat normaux
  chat-message:
    enabled: true
    chance: 5        # 5% de chance par message
    cooldown: 10     # Cooldown en secondes par viewer
    actions:
      - type: SPAWN_MOB
        mob: ZOMBIE
        amount: 1
        radius: 5
        name: "&c%viewer%"

  # Super Chats
  super-chat:
    enabled: true
    actions:
      - type: GIVE_ITEM
        item: DIAMOND
        amount: 1
        display-name: "&bDiamant de %viewer%"
      - type: SPAWN_MOB
        mob: WITHER
        amount: 1
        min-amount: 20.00  # Seulement si >= 20 euros

  # Nouveaux membres
  new-member:
    enabled: true
    actions:
      - type: BROADCAST
        message: "&a[NOUVEAU MEMBRE] %viewer%"
      - type: EFFECT
        effect: REGENERATION
        duration: 30
        amplifier: 1

  # Likes
  like:
    enabled: true
    actions:
      - type: COMMAND
        command: "eco give * 100"
        executor: CONSOLE
      - type: BROADCAST
        message: "&c+1 Like! Tous les joueurs recoivent 100$!"

  # Paliers de vues
  view-milestone:
    enabled: true
    actions:
      - type: BROADCAST
        broadcast-type: TITLE
        message: "&a%message%"
        subtitle: "&7Merci pour votre soutien!"

  # Mots-cles
  keywords:
    creeper:
      enabled: true
      chance: 50
      cooldown: 30
      actions:
        - type: SPAWN_MOB
          mob: CREEPER
          amount: 3
```

### Types d'Actions

#### SPAWN_MOB
```yaml
- type: SPAWN_MOB
  mob: ZOMBIE           # Type de mob
  amount: 1             # Nombre
  radius: 5             # Rayon autour du joueur
  name: "&cNom"         # Nom custom (optionnel)
  hostile: true         # Si hostile
```

#### GIVE_ITEM
```yaml
- type: GIVE_ITEM
  item: DIAMOND         # Type d'item
  amount: 1             # Quantite
  display-name: "Nom"   # Nom custom
  drop: false           # Drop au sol au lieu d'inventaire
  lore:                 # Description
    - "Ligne 1"
    - "Ligne 2"
  enchantments:         # Enchantements
    SHARPNESS: 5
```

#### EFFECT
```yaml
- type: EFFECT
  effect: SPEED         # Type d'effet
  duration: 10          # Duree en secondes
  amplifier: 1          # Niveau (0 = I, 1 = II)
  particles: true       # Afficher particules
```

#### WORLD_EVENT
```yaml
- type: WORLD_EVENT
  event: LIGHTNING      # LIGHTNING, EXPLOSION, TNT, RAIN, STORM, CLEAR, DAY, NIGHT, FIRE, METEOR
  radius: 3             # Rayon
  damage: true          # Faire des degats
  power: 2.0            # Puissance (explosions)
```

#### COMMAND
```yaml
- type: COMMAND
  command: "give %player% diamond 1"
  executor: CONSOLE     # CONSOLE, PLAYER, ou OP
  # OU liste de commandes:
  commands:
    - "command1"
    - "command2"
```

#### BROADCAST
```yaml
- type: BROADCAST
  message: "&aMessage"
  broadcast-type: CHAT  # CHAT, ACTIONBAR, TITLE, PLAYER
  subtitle: "..."       # Pour TITLE seulement
  broadcast: true       # Pour TITLE (tous les joueurs)
```

#### SOUND
```yaml
- type: SOUND
  sound: ENTITY_EXPERIENCE_ORB_PICKUP
  volume: 1.0
  pitch: 1.0
  broadcast: false      # Jouer pour tous
```

#### PARTICLE
```yaml
- type: PARTICLE
  particle: HEART
  count: 10
  offset-x: 0.5
  offset-y: 0.5
  offset-z: 0.5
  speed: 0.1
```

## Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%viewer%` | Nom du viewer YouTube |
| `%message%` | Message du viewer |
| `%player%` | Nom du joueur cible |
| `%amount%` | Montant affiche (Super Chat) |
| `%amount_value%` | Valeur numerique du montant |
| `%all%` | Execute pour chaque joueur en ligne |
| `%author_role%` | Role (OWNER, MODERATOR, SPONSOR, VIEWER) |
| `%author_roles%` | Tous les roles |
| `%is_owner%` | true/false |
| `%is_moderator%` | true/false |
| `%is_sponsor%` | true/false |

---

# API pour Developpeurs

## Dependance Maven

```xml
<dependency>
    <groupId>fr.arkyalys</groupId>
    <artifactId>YouTubeEvent</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## plugin.yml

```yaml
depend: [YouTubeEvent]
# ou
softdepend: [YouTubeEvent]
```

## Utilisation de l'API

```java
import fr.arkyalys.event.api.YouTubeEventAPI;

public class MonPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Verifier si YouTubeEvent est disponible
        if (!YouTubeEventAPI.isAvailable()) {
            getLogger().warning("YouTubeEvent non trouve!");
            return;
        }

        // Recuperer l'API
        YouTubeEventAPI api = YouTubeEventAPI.getInstance();

        // Verifier la connexion
        if (api.isConnected()) {
            // Recuperer les stats
            long likes = api.getLikeCount();
            long views = api.getViewCount();
            int messages = api.getTotalMessagesReceived();
            String uptime = api.getUptimeFormatted();

            // Joueur cible
            Player target = api.getTargetPlayer();

            // Provider info
            String provider = api.getProviderName(); // "InnerTube" ou "Data API v3"
            boolean quotaFree = api.isQuotaFree();
        }

        // Controler la connexion
        api.startLive("VIDEO_ID");
        api.stopLive();
        api.setTargetPlayer(player);
    }
}
```

## Evenements Bukkit

Le plugin declenche des evenements Bukkit que vous pouvez ecouter :

### YouTubeChatMessageEvent
Declenche pour **tous** les messages (chat, super chat, membres, etc.)

```java
@EventHandler
public void onChatMessage(YouTubeChatMessageEvent event) {
    ChatMessage message = event.getMessage();
    String author = event.getAuthorName();
    String content = event.getMessageContent();
    ChatMessage.MessageType type = event.getMessageType();

    // Infos auteur
    boolean isOwner = event.isOwner();
    boolean isMod = event.isModerator();
    boolean isSponsor = event.isSponsor();

    // Annuler le traitement par YouTubeEvent
    event.setCancelled(true);
}
```

### YouTubeSuperChatEvent
Declenche pour les Super Chats et Super Stickers

```java
@EventHandler
public void onSuperChat(YouTubeSuperChatEvent event) {
    String viewer = event.getAuthorName();
    double amount = event.getAmountValue();    // Ex: 5.00
    String display = event.getAmountDisplay(); // Ex: "5,00 EUR"
    String currency = event.getCurrency();     // Ex: "EUR"
    String message = event.getMessageContent();

    boolean isSticker = event.isSuperSticker();

    // Faire quelque chose selon le montant
    if (amount >= 10.0) {
        // Gros super chat!
    }
}
```

### YouTubeNewMemberEvent
Declenche quand un viewer rejoint la chaine

```java
@EventHandler
public void onNewMember(YouTubeNewMemberEvent event) {
    String memberName = event.getMemberName();
    String channelId = event.getMemberChannelId();

    // Bienvenue au nouveau membre!
}
```

### YouTubeLikeEvent
Declenche quand de nouveaux likes sont detectes

```java
@EventHandler
public void onLike(YouTubeLikeEvent event) {
    long newLikes = event.getNewLikes();   // Nouveaux likes depuis la derniere verif
    long total = event.getTotalLikes();    // Total de likes

    // Recompenser les joueurs
}
```

### YouTubeViewMilestoneEvent
Declenche quand un palier de vues est atteint

```java
@EventHandler
public void onViewMilestone(YouTubeViewMilestoneEvent event) {
    long views = event.getViewCount();      // Nombre de vues actuel
    int milestone = event.getMilestone();   // Palier configure (ex: 100)
    int number = event.getMilestoneNumber(); // Numero du palier (ex: 3 = 300 vues)
}
```

### YouTubeConnectionEvent
Declenche quand la connexion change d'etat

```java
@EventHandler
public void onConnection(YouTubeConnectionEvent event) {
    String liveId = event.getLiveId();
    YouTubeConnectionEvent.ConnectionState state = event.getState();
    String provider = event.getProviderName();

    if (event.isConnected()) {
        // Connecte au live!
    } else {
        // Deconnecte
    }
}
```

## Classes de modeles

### ChatMessage
```java
public class ChatMessage {
    String getMessageId();
    String getAuthorChannelId();
    String getAuthorName();
    String getAuthorProfileImage();
    String getMessage();
    long getPublishedAt();
    MessageType getType();

    // Super Chat
    String getAmountDisplay();
    long getAmountMicros();
    String getCurrency();
    double getAmountValue();

    // Roles
    boolean isModerator();
    boolean isOwner();
    boolean isSponsor();
    String getAuthorRole();    // "OWNER", "MODERATOR", "SPONSOR", "VIEWER"
    String getAuthorRoles();   // "OWNER, MODERATOR"

    // Helpers
    boolean isSuperChat();
    boolean isSuperSticker();
    boolean isNewMember();

    enum MessageType {
        TEXT_MESSAGE,
        SUPER_CHAT,
        SUPER_STICKER,
        NEW_MEMBER,
        MEMBERSHIP_GIFT
    }
}
```

---

## InnerTube vs Data API

Le plugin utilise deux methodes pour recuperer le chat YouTube :

| | InnerTube | Data API v3 |
|---|-----------|-------------|
| **Quota** | 0 (gratuit) | 10,000/jour |
| **Fiabilite** | Bonne | Excellente |
| **Vitesse** | Rapide | Rapide |
| **Risque** | Peut changer | Stable |

Par defaut, InnerTube est utilise avec fallback automatique sur Data API si necessaire.

## Obtenir une cle API YouTube

1. Aller sur [Google Cloud Console](https://console.cloud.google.com/)
2. Creer un nouveau projet
3. Activer "YouTube Data API v3"
4. Creer des identifiants > Cle API
5. Copier la cle dans la config

## Trouver son Channel ID

1. Aller sur [YouTube Settings](https://www.youtube.com/account_advanced)
2. Copier l'ID de chaine (format: UCxxxxxxxxxxxxxxxxxx)

---

## Support

- GitHub: https://github.com/Arkyalys/YouTubeEvent
- Serveur: play.arkyalys.net

## Licence

Propriete de Arkyalys. Tous droits reserves.
