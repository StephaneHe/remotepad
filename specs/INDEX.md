# RemotePad — Index des Tâches

## Mode d'emploi

Chaque tâche est conçue pour être donnée **indépendamment** à un prompt/agent distinct.

**Pour chaque tâche, fournissez toujours 2 fichiers :**
1. `00_Contexte_Partagé.md` — Le contexte global (toujours inclus)
2. Le fichier de tâche spécifique (ex: `01_Serveur_Protocol_Messages.md`)

## Liste des tâches

| # | Fichier | Module(s) | Étape(s) CdC | Dépend de |
|---|---------|-----------|-------------|-----------|
| 00 | `00_Contexte_Partagé.md` | — | — | (inclure avec toutes les tâches) |
| 01 | `01_Serveur_Protocol_Messages.md` | `messages.py` | 1.1 | Aucune |
| 02 | `02_Serveur_Input_Controller.md` | `input_controller.py` | 1.2 | Tâche 01 |
| 03 | `03_Serveur_Logging.md` | `log_manager.py` + `log_window.py` | 1.3 + 2.3 | Aucune |
| 04 | `04_Serveur_Auth_PIN.md` | `auth_manager.py` | 1.4 | Tâches 01, 03 |
| 05 | `05_Serveur_WebSocket.md` | `server.py` | 1.5 | Tâches 01, 02, 03, 04 |
| 06 | `06_Serveur_Config_Tray.md` | `config.py` + `tray.py` + `__main__.py` | 1.6 | Tâches 03, 05 |
| 07 | `07_Android_Réseau.md` | `WebSocketClient.kt` + `MessageSerializer.kt` + `Events.kt` | 1.7 | Tâche 05 (serveur fonctionnel) |
| 08 | `08_Android_Connexion.md` | `ConnectionScreen.kt` + `ConnectionViewModel.kt` | 1.8 | Tâche 07 |
| 09 | `09_Android_Trackpad.md` | `TrackpadScreen.kt` + `TouchEventProcessor.kt` | 1.9 | Tâches 07, 08 |
| 10 | `10_Android_Clavier_Raccourcis.md` | `KeyboardInputHandler.kt` + `ShortcutBar` | 1.10 + 2.1 | Tâche 07 |
| 11 | `11_Android_Paramètres.md` | `SettingsScreen.kt` + `MotionProcessor` | 2.2 | Tâche 09 |
| 12 | `12_Tests_E2E.md` | `test_e2e.py` | 1.11 | Tâches 01–10 |
| 13 | `13_Packaging.md` | PyInstaller + APK Release | 2.4 | Toutes tâches précédentes |
| 14 | `14_Bluetooth.md` | `bt_server.py` + `BluetoothClient.kt` + `ConnectionManager.kt` | 3.1 + 3.2 + 3.3 | Tâches 05, 07 |

## Ordre d'exécution recommandé

### Phase 1 — MVP WiFi (parallélisable en partie)

```
Parallèle 1 :  Tâche 01 (Protocol)    Tâche 03 (Logging)
                    │                       │
Parallèle 2 :  Tâche 02 (Input Ctrl)       │
                    │                       │
Séquentiel  :  Tâche 04 (Auth) ←───────────┘
                    │
Séquentiel  :  Tâche 05 (WebSocket Server)
                    │
              ┌─────┴──────┐
Parallèle 3 :│ Tâche 06    │ Tâche 07 (Android Réseau)
              │ (Config/Tray)│         │
              └─────────────┘    ┌─────┴──────┐
Parallèle 4 :              Tâche 08    Tâche 10
                           (Connexion)  (Clavier)
                                │
Séquentiel  :              Tâche 09 (Trackpad)
                                │
Séquentiel  :              Tâche 12 (Tests E2E)
```

### Phase 2 — Polish

```
Parallèle :   Tâche 11 (Paramètres)   Tâche 03 partie 2 (Log Window)
                                │
Séquentiel :               Tâche 13 (Packaging)
```

### Phase 3 — Bluetooth

```
Séquentiel :  Tâche 14 (Bluetooth complet)
```

## Notes

- Les tâches 01 et 03 peuvent démarrer **en parallèle** (pas de dépendances)
- Les tâches 07 et 06 peuvent démarrer **en parallèle** après la tâche 05
- Les tâches 08 et 10 peuvent démarrer **en parallèle** après la tâche 07
- Chaque fichier de tâche contient les APIs des modules dont il dépend, ce qui le rend autonome
