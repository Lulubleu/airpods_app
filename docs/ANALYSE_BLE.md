# Analyse BLE — trames « Proximity Pairing » des AirPods

Source analysée : [adolfintel/OpenPods](https://github.com/adolfintel/OpenPods) (GPL-3.0).
Le lien `adrielcafe/OpenPods` n'existe plus ; `adolfintel/OpenPods` est le dépôt d'origine.

Fichiers clés lus :

| Fichier OpenPods | Rôle |
|---|---|
| `pods/PodsStatusScanCallback.java` | Filtre de scan BLE, sélection du « bon » beacon, conversion hex |
| `pods/PodsStatus.java` | Décodage de la trame (batteries, charge, oreille, modèle) |
| `pods/Pod.java` | Conversion niveau brut → pourcentage |
| `pods/PodsService.java` | Service de fond, scanner, détection connexion (UUID) |
| `receivers/BluetoothReceiver.java` | Événements BT système (ON/OFF, ACL connect/disconnect) |

Le portage propre se trouve dans le module `core/protocol` (Kotlin pur, testé).

## 1. Où se trouvent les données

Les AirPods (et Beats) émettent en continu un advertisement BLE contenant un champ
**Manufacturer Specific Data** avec le company ID Apple **`0x004C` (76)**.
Android fournit ce champ **sans** le company ID : on reçoit un tableau de **27 octets**.

Filtre de scan utilisé (identique à OpenPods) : company ID 76, octets `[0]=0x07` et `[1]=0x19`
(masque sur ces deux octets uniquement).

## 2. Disposition des 27 octets

Les positions « hex » sont celles qu'utilise OpenPods (qui convertit la trame en chaîne hexadécimale,
2 caractères par octet). Le portage travaille directement sur les octets.

| Octet | Hex (OpenPods) | Contenu |
|---|---|---|
| 0 | 0–1 | Type de message : `0x07` = Proximity Pairing |
| 1 | 2–3 | Longueur restante : `0x19` = 25 |
| 2 | 4–5 | Préfixe (non utilisé) |
| 3–4 | 6–9 | **Modèle** (ex. `0E 20` = AirPods Pro) |
| 5 | 10 | Quartet haut : bit `0x2` → **orientation** (voir § 3) |
| 5 | 11 | Quartet bas : **détection oreille** (bit 1 et bit 3) |
| 6 | 12 / 13 | **Batteries** des deux écouteurs (un quartet chacun) |
| 7 | 14 | Quartet haut : **drapeaux de charge** (bit 0, bit 1 = écouteurs, bit 2 = boîtier) |
| 7 | 15 | Quartet bas : **batterie du boîtier** |
| 8–26 | — | Non décodé (compteur d'ouverture du couvercle, couleur, partie chiffrée…) |

### Valeur d'une batterie (un quartet)

| Valeur brute | Signification |
|---|---|
| 0 – 9 | ≈ `valeur × 10 + 5` % (OpenPods affiche 5 %, 15 %, … 95 %) |
| 10 | 100 % |
| 15 | Composant absent / déconnecté (écouteur hors portée, boîtier non signalé) |
| 11 – 14 | Non documenté : ni connecté ni déconnecté |

La précision est donc de **10 %** : c'est une limite du protocole public, pas du code.

## 3. Orientation (« flip ») gauche/droite

La trame est émise par **un seul** écouteur (le « primaire »). Les champs batterie, charge et oreille
sont rapportés « cet écouteur / l'autre », pas « gauche / droite ».
OpenPods déduit l'orientation du bit `0x20` de l'octet 5 :

- bit `0x20` **à 1** → non inversé
- bit `0x20` **à 0** → inversé (`flipped`)

| Champ | Non inversé | Inversé |
|---|---|---|
| Batterie gauche | quartet bas octet 6 | quartet haut octet 6 |
| Batterie droite | quartet haut octet 6 | quartet bas octet 6 |
| Charge gauche | bit 0 octet 7 (haut) | bit 1 |
| Charge droite | bit 1 | bit 0 |
| Gauche dans l'oreille | bit 1 octet 5 (bas) | bit 3 |
| Droite dans l'oreille | bit 3 | bit 1 |

Boîtier : batterie = quartet bas de l'octet 7, charge = bit 2 du quartet haut (jamais inversé).

## 4. Appareils à composant unique

AirPods Max et la plupart des Beats n'ont ni boîtier ni paire : la batterie est le **quartet bas
de l'octet 6** et la charge le **bit 0** des drapeaux, sans inversion.

## 5. Identification du modèle

OpenPods compare d'abord l'identifiant complet (octets 3–4), puis, pour certains appareils dont
l'identifiant complet n'est pas connu, uniquement le quartet bas de l'octet 3.

| Identifiant | Modèle | Type |
|---|---|---|
| `0220` | AirPods (1re gén.) | paire + boîtier |
| `0F20` | AirPods (2e gén.) | paire + boîtier |
| `1320` | AirPods (3e gén.) | paire + boîtier |
| `0E20` | AirPods Pro | paire + boîtier |
| `1420`, `2420` | AirPods Pro 2 | paire + boîtier |
| `2720` | AirPods Pro 3 | paire + boîtier |
| `?A??` | AirPods Max | unique |
| `?B??` | Powerbeats Pro | paire + boîtier |
| `0520` | BeatsX | unique |
| `1020` | Beats Flex | unique |
| `0620` | Beats Solo 3 | unique |
| `?9??` | Beats Studio 3 | unique |
| `0320` | Powerbeats 3 | unique |
| autre | Inconnu (traité comme paire + boîtier) | paire + boîtier |

Aucun identifiant complet de la liste ne finit par `A`, `B` ou `9` au 2e caractère : tester les
identifiants complets avant les quartets donne exactement le même résultat que l'ordre d'OpenPods.

## 6. Reconnaître **vos** AirPods

Android ne donne pas l'adresse réelle des émetteurs BLE et les AirPods changent d'adresse aléatoire
régulièrement. Impossible de savoir à coup sûr qu'une trame vient de vos écouteurs. Heuristique
d'OpenPods, reprise telle quelle dans `BeaconSelector` :

1. Garder les trames reçues pendant les **10 dernières secondes**.
2. Prendre la trame au **RSSI le plus fort** ; si elle vient de la même adresse que la trame
   courante, utiliser la trame courante (plus récente).
3. Ignorer le résultat si son RSSI est **inférieur à −60 dBm**.

Pour savoir si des AirPods sont **connectés** (et non juste à proximité), OpenPods écoute
`ACTION_ACL_CONNECTED/DISCONNECTED` et le proxy du profil HEADSET, puis vérifie que l'appareil
annonce l'un de ces UUID :

- `74ec2172-0bad-4d01-8f77-997b2be0722a`
- `2a72e02b-7b99-778f-014d-ad0b7221ec74`

Améliorations possibles plus tard : la fin de la trame (une partie des octets 8–26) est chiffrée avec une clé propre
à l'appareil. Avec cette clé, on obtiendrait la batterie au pourcent près et une identification
fiable de vos écouteurs. OpenPods ne le fait pas.

## 7. Paramètres du service de fond (OpenPods)

- Scan avec `ScanFilter` ci-dessus, mode `LOW_LATENCY` (ou `LOW_POWER` en économie de batterie),
  `setReportDelay(1)` (le commentaire d'origine précise de ne pas mettre 0).
- Service au premier plan (`foregroundServiceType="location"`), relancé au démarrage
  (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).
- Optionnel : arrêt du scan écran éteint.
- Permissions : `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT`,
  `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, plus la localisation pour les anciennes versions.

Ces points serviront à l'étape 3.

## 8. Limites du BLE mesurées, et passage à AAP

Mesures sur un Pixel 10a (Android 17) avec des AirPods 2 portés :

- Avec `setReportDelay(1)` (valeur d'OpenPods), Android regroupe les trames et les livre par
  paquets toutes les ~5 s. Corrigé avec `setReportDelay(0)`.
- Même sans regroupement, les AirPods portés n'émettent leur trame que **toutes les ~5 s**.
  Une détection d'oreille basée sur le BLE a donc jusqu'à 5 s de retard.

## 9. AAP sur L2CAP (oreille instantanée, batterie exacte, ANC)

L'ANC/transparence n'est pas dans les trames BLE. Ces informations, ainsi que la détection d'oreille
instantanée et la batterie au pourcent près, passent par le protocole d'Apple **AAP** sur un canal
**L2CAP classique, PSM 0x1001**. Implémentation reprise de
[LibrePods](https://github.com/kavishdevar/librepods) (GPL-3.0, `docs/AAP Definitions.md`), dans
`core/protocol/.../aap` et `app/.../bluetooth/AapConnection.kt`.

- Ouverture : handshake, feature flags (`4D 00`), abonnement aux notifications (`0F 00 FF FF FF FF`).
- Reçu : batterie (`04 00`, niveau 0–100 + état charge/déconnecté), oreille (`06 00 [primaire] [secondaire]`,
  0 = porté, 1 = retiré, 2 = boîtier), contrôle du bruit (`09 00 0D [mode]`).
- Envoyé : changement de mode (`09 00 0D [1 désactivé | 2 réduction | 3 transparence | 4 adaptatif]`).

Socket : `createUsingSocketSettings` (API publique d'Android 16+) refuse `TYPE_L2CAP` (« invalid
socketType - 3 ») et n'accepte que des PSM LE ≤ 255. On utilise donc le constructeur caché de `BluetoothSocket`, rendu accessible avec
[AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (Apache-2.0), comme
LibrePods. D'après LibrePods, le canal n'est accepté sans root que sur Android 17+ (ou certains
Android 16). Sinon, l'app retombe sur le BLE seul. Mesuré sur le Pixel 10a : pause ~30 ms après le retrait,
reprise ~30 ms après la remise.

Conséquence : l'accès à des API non publiques n'est pas accepté sur le Play Store. L'app vise
F-Droid ou l'installation directe.

## 10. Licence

OpenPods est sous **GPL-3.0**. Notre décodeur reprend son algorithme : le projet est donc publié sous
GPL-3.0, ce qui va dans le sens d'une app gratuite et open source.
