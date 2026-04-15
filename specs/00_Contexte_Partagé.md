# RemotePad — Contexte Partagé (Document de Référence)

> **Ce document doit accompagner chaque tâche individuelle.**
> Il fournit le contexte global du projet. La tâche spécifique à réaliser est décrite dans le document de tâche associé.

## 1. Présentation du projet

**RemotePad** est une solution permettant de transformer un téléphone Android en trackpad et clavier virtuel pour contrôler un PC sous Windows 11 via le réseau local (WiFi).

| Propriété | Valeur |
|-----------|--------|
| Projet    | RemotePad |
| Version   | 1.0 |
| Date      | 15 février 2026 |
| Auteur    | Stéphane |

## 2. Architecture générale

Le système se compose de deux éléments communiquant via le réseau local :

| Composant | Description |
|-----------|-------------|
| App Android (Client) | Application native Kotlin. Envoie les événements tactiles et clavier au serveur via WebSocket. |
| Serveur Windows (Serveur) | Application Python avec system tray. Reçoit les événements et pilote souris/clavier via les APIs Windows. |

### 2.1 Protocole de communication

- **Transport** : WebSocket (TCP) pour la fiabilité et la faible latence
- **Format des messages** : JSON compact
- **Port par défaut** : 9876
- **Sécurité** : appairage par code PIN à 4 chiffres, généré par le serveur

### 2.2 Format des messages

Chaque message est un objet JSON avec un champ `"type"` discriminant :

| Type | Payload | Description |
|------|---------|-------------|
| `mouse_move` | `{dx, dy}` | Déplacement relatif de la souris |
| `mouse_click` | `{button: left\|right\|middle}` | Clic souris |
| `mouse_double_click` | `{button: left}` | Double-clic |
| `mouse_scroll` | `{dx, dy}` | Défilement horizontal/vertical |
| `key_press` | `{key, modifiers[]}` | Frappe clavier unique |
| `key_combo` | `{keys[]}` | Combinaison de touches (Ctrl+C...) |
| `text_input` | `{text}` | Saisie de texte brut (IME) |
| `auth` | `{pin}` | Authentification par PIN |
| `auth_response` | `{success, message}` | Réponse d'authentification |

## 3. Stack technique

| Couche | Android (Client) | Windows (Serveur) |
|--------|-------------------|-------------------|
| Langage | Kotlin | Python 3.11+ |
| Transport WiFi | OkHttp WebSocket | websockets (asyncio) |
| Transport BT (phase 3) | BluetoothSocket (RFCOMM) | PyBluez / socket |
| Contrôle E/S | N/A | pynput |
| UI | Jetpack Compose / View | pystray + Pillow |
| Packaging | APK (Gradle) | PyInstaller (.exe) |
| Config | SharedPreferences | JSON local |

## 4. Contraintes non fonctionnelles

### Performance
- Latence maximale souris : < 20ms (WiFi local)
- Fréquence d'envoi des événements souris : 60 Hz max (throttling)
- Taille des messages JSON : < 200 octets

### Compatibilité
- Windows 11 (serveur)
- Android 8.0+ / API 26 (client)
- Réseau WiFi local (même sous-réseau)

### Expérience utilisateur
- Connexion en moins de 5 secondes après saisie de l'IP et du PIN
- Reconnexion automatique en cas de perte temporaire du réseau
- Indicateur visuel permanent de l'état de connexion

## 5. Environnement de développement

Le développement est entièrement réalisé en ligne de commande, sans IDE. Claude développe en autonomie via un serveur MCP PowerShell intégré à Claude Desktop.

### 5.1 Serveur Python — Windows 11

- **Python** : 3.12.10 (appelé via `py -3.12`)
- **Venv** : `RemotePad\.venv`
- **Exécuter Python** : `RemotePad\.venv\Scripts\python.exe`
- **Installer un paquet** : `RemotePad\.venv\Scripts\pip.exe install <pkg>`
- **Lancer les tests** : `RemotePad\.venv\Scripts\pytest.exe tests/ -v`
- **Lancer avec couverture** : `RemotePad\.venv\Scripts\pytest.exe tests/ -v --cov=server`
- **Lancer le serveur** : `RemotePad\.venv\Scripts\python.exe -m server`
- **Dépendances** : websockets, pynput, pystray, Pillow, pytest, pytest-asyncio, pytest-mock, pytest-cov

> Note : on utilise les chemins absolus vers le venv plutôt que l'activation du venv, car les commandes sont exécutées via MCP dans des sessions PowerShell non interactives.

### 5.2 Client Android — Gradle CLI

- **JDK** : 17 (Temurin)
- **Android SDK** : `%ANDROID_HOME%` (ANDROID_HOME)
- **API level** : 34 / Build Tools 34.0.0
- **Build debug APK** : `gradlew.bat assembleDebug`
- **Tests unitaires** : `gradlew.bat testDebugUnitTest`
- **Installer sur téléphone** : `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- **Logs de l'app** : `adb logcat -s RemotePad:*`

### 5.3 Stratégie d'écriture de fichiers via MCP

- **Fichiers simples** : `Set-Content` avec échappement PowerShell
- **Fichiers complexes** (code avec backticks, quotes imbriquées) : encodage base64 côté sandbox Linux, décodage côté Windows via `[System.Convert]::FromBase64String()`
- **Vérification systématique** : `Get-Content -Head N` après chaque écriture

### 5.4 Appareil de test — Doogee V30T

- Le bouton d'alimentation est hors service. Le redémarrage se fait via `adb reboot`.
- Connexion ADB USB ou WiFi (`adb pair` / `adb connect`)

### 5.5 Réseau

- Pare-feu : `New-NetFirewallRule -DisplayName 'RemotePad Server' -Direction Inbound -Protocol TCP -LocalPort 9876 -Action Allow -Profile Private`
- IP locale : `(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias Wi-Fi).IPAddress`

## 6. Structure des projets

### Serveur Python
```
RemotePad\
  server\
    __init__.py
    __main__.py          # Point d'entrée
    messages.py           # Protocole JSON
    input_controller.py   # Wrapper pynput
    auth_manager.py       # Auth PIN
    log_manager.py        # Logging double sortie
    server.py             # Serveur WebSocket
    config.py             # Configuration JSON
    tray.py               # System tray (pystray)
    log_window.py         # Fenêtre de logs (tkinter)
  tests\
    __init__.py
    test_messages.py
    test_input_controller.py
    test_auth_manager.py
    test_log_manager.py
    test_server.py
    test_config.py
    test_e2e.py
  requirements.txt
  requirements-dev.txt
  config.json
```

### Client Android
```
RemotePad\android\
  app\
    src\main\
      java\com\remotepad\
        network\
          RemoteConnection.kt       # Interface commune WiFi/BT
          WebSocketClient.kt         # Implémentation WiFi
          MessageSerializer.kt       # Serde JSON
        input\
          TouchEventProcessor.kt     # Gestion des gestes
          KeyboardInputHandler.kt    # Gestion du clavier
        ui\
          ConnectionScreen.kt        # Écran de connexion
          TrackpadScreen.kt          # Écran principal
          SettingsScreen.kt          # Paramètres
        viewmodel\
          ConnectionViewModel.kt
          TrackpadViewModel.kt
        model\
          Events.kt                  # Data classes des événements
        MainActivity.kt
      res\                           # Layouts, drawables, strings
      AndroidManifest.xml
    src\test\                        # Tests unitaires JUnit + MockK
    build.gradle.kts
  build.gradle.kts                   # Build config racine
  settings.gradle.kts
  gradle.properties
  gradlew.bat
```

## 7. Méthodologie TDD

Chaque fonctionnalité suit le cycle : **RED** (tests écrits, échouent) → **GREEN** (implémentation minimale) → **REFACTOR** (restructuration).

## 8. Phases de développement

| Phase | Contenu | Livrable |
|-------|---------|----------|
| Phase 1 — MVP WiFi | Serveur Python + App Android basique. Trackpad, clics, clavier, scroll via WebSocket WiFi. | APK + script Python fonctionnels |
| Phase 2 — Polish | Raccourcis clavier, paramètres de sensibilité, vibration haptique, system tray complet, packaging exe. | APK final + exe Windows |
| Phase 3 — Bluetooth | Ajout du transport Bluetooth comme fallback. Détection automatique et bascule WiFi/BT. | Version complète |

## 9. Graphe de dépendances

| Étape | Dépend de |
|-------|-----------| 
| 1.1 Protocole messages | — (aucune) |
| 1.2 Contrôleur souris/clavier | 1.1 |
| 1.3 Logging | — (aucune) |
| 1.4 Auth PIN | 1.1, 1.3 |
| 1.5 Serveur WebSocket | 1.1, 1.2, 1.3, 1.4 |
| 1.6 Config + system tray | 1.3, 1.5 |
| 1.7 WebSocket client Android | 1.5 |
| 1.8 Écran connexion Android | 1.7 |
| 1.9 Trackpad + boutons | 1.7, 1.8 |
| 1.10 Clavier virtuel | 1.7 |
| 1.11 Tests E2E | 1.1 → 1.10 |
| 2.1 Raccourcis clavier | 1.10 |
| 2.2 Sensibilité + paramètres | 1.9 |
| 2.3 Fenêtre log + tray polish | 1.3, 1.6 |
| 2.4 Packaging | 2.1, 2.2, 2.3 |
| 3.1 Bluetooth serveur | 1.5 |
| 3.2 Bluetooth Android | 1.7, 3.1 |
| 3.3 Bascule auto WiFi/BT | 3.1, 3.2 |
