# Pods

Application Android gratuite et open source pour suivre vos AirPods et écouteurs Beats :
batterie gauche/droite/boîtier, charge, détection dans l'oreille. Interface Material You.

> Projet indépendant, non affilié à Apple. AirPods et Beats sont des marques d'Apple Inc.

## Stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (couleurs dynamiques, Android 12+)
- Android Gradle Plugin 9, Gradle 9.8, compileSdk 37, minSdk 31

## Modules

| Module | Contenu |
|---|---|
| `core/protocol` | Décodage des trames BLE « Proximity Pairing » d'Apple. Kotlin pur, sans Android, testé. |
| `app` | Application Android (UI Compose, service Bluetooth de fond). |

L'analyse du protocole est dans [docs/ANALYSE_BLE.md](docs/ANALYSE_BLE.md).

## Compiler

Tests du décodeur (juste un JDK 17 ou plus récent) :

```bash
./gradlew :core:protocol:test
```

Application (Android SDK requis, par exemple via Android Studio) :

```bash
./gradlew :app:assembleDebug
```

Captures d'écran de l'interface sans appareil (Robolectric + Roborazzi), dans `app/build/outputs/roborazzi` :

```bash
./gradlew :app:recordRoborazziDebug
```

## Feuille de route

1. ✅ Analyse d'OpenPods et portage du décodeur BLE
2. ✅ Structure du projet
3. ✅ Service Bluetooth de fond et permissions (scan seulement quand les AirPods sont connectés)
4. ✅ Écran principal Material You et pop-up de connexion
5. ✅ Protocole AAP sur L2CAP : détection d'oreille instantanée, pause/reprise auto, batterie exacte, ANC
6. ⬜ Widget d'écran d'accueil et tuile des réglages rapides
7. ⬜ Réglages (désactiver la pause auto, etc.)

> L'AAP utilise une API Android non publique. Il fonctionne sans root sur Android 17+.
> Sur les versions plus anciennes, l'app retombe sur le BLE seul (batterie à 10 % près, oreille avec ~5 s de retard).

## Licence

GPL-3.0. Le décodeur reprend l'algorithme d'[OpenPods](https://github.com/adolfintel/OpenPods)
(GPL-3.0, Federico Dossena et contributeurs). Le protocole AAP suit
[LibrePods](https://github.com/kavishdevar/librepods) (GPL-3.0).
Police Google Sans Flex sous SIL OFL 1.1 (`app/src/main/assets/licenses/GoogleSansFlex-OFL.txt`).
