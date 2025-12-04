# YouTubeEvent Plugin

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

## YouTube API

- **API Key**: AIzaSyADjv7gbO5h0SdHiVofQouldA64h12PP5Q
- **Channel**: @RayniseG
- **Channel ID**: UCjezM7oxQpcZmR542BfcmFw

## Commandes In-Game

```
/youtube setchannel <UCxxxxxxxx>   # Configurer la chaine
/youtube settarget                  # Te definir comme cible
/youtube auto start                 # Activer detection auto
/youtube status                     # Voir le statut
```

## Build

```bash
mvn clean package
```

Le JAR se trouve dans `target/YouTubeEvent-1.0.0.jar`
