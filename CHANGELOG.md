# Changelog

Tous les changements notables de ce projet sont documentés dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-08-02

### Fixed
- **Android**: après un échec d'authentification (PIN refusé par le serveur), le bouton Connexion restait grisé car `connectionState` demeurait à `CONNECTED`. Le ViewModel appelle maintenant `connection.disconnect()` après un refus de PIN, ce qui remet l'état à `DISCONNECTED` et réactive le bouton dès que l'utilisateur saisit un nouveau PIN valide.

## [1.1.0] - 2026-07-17

### Added
- **Android / Server** : zoom par pinch — écarter deux doigts sur la zone trackpad zoom IN, les rapprocher zoom OUT. Chaque palier de 30 px de variation de distance entre les doigts envoie un événement `zoom` au serveur, traduit côté serveur en Ctrl+molette via pynput. Le contrôle de volume (glisser deux doigts verticalement par leur centroïde) continue de fonctionner simultanément.

## [1.0.0] - 2026-04-30

### Added

- **Server**: serveur WebSocket Python (port 9876) avec authentification par PIN à 4 chiffres.
- **Server**: contrôleur d'entrée `pynput` pour souris (déplacement, clic, double-clic, scroll) et clavier (`key_press`, `key_combo`, `text_input`).
- **Server**: protocole JSON (`mouse_move`, `mouse_click`, `mouse_double_click`, `mouse_scroll`, `key_press`, `key_combo`, `text_input`, `auth`, `auth_response`).
- **Server**: gestion de la configuration via `config.json` et logging dual (fichier + console).
- **Server**: system tray (pystray) avec affichage du PIN, toggle debug, et quitter.
- **Server**: support Bluetooth (RFCOMM) côté serveur en complément du WiFi.
- **Server**: variable `__version__` dans `server/__init__.py`, exposée au démarrage (log), dans la tooltip du tray et dans le menu contextuel.
- **Android**: application Kotlin / Jetpack Compose ciblant API 26+.
- **Android**: écran de connexion (IP, port, PIN) avec validation des entrées.
- **Android**: écran trackpad (Compose) — déplacement souris, clic 1/2 doigts, scroll, double-tap.
- **Android**: capture clavier via `BasicTextField` invisible avec gestion d'`Enter` et `Backspace`.
- **Android**: barre de raccourcis navigation (flèches, Home/End, PgUp/PgDn, Tab, Enter, Escape) et raccourcis texte (Ctrl+A/C/V/X/Z/F/S, Alt+Tab, Alt+F4, Win).
- **Android**: client WebSocket (OkHttp) et client Bluetooth (RFCOMM) avec interface commune.
- **Android**: persistance des préférences (sensibilité souris/scroll, accélération, vibration haptique) via SharedPreferences.
- **Android**: footer affichant `v{BuildConfig.VERSION_NAME}` sur l'écran de connexion.
- **Project**: `CLAUDE.md` documentant les règles standing du fleet (versioning visible et changelog).
- **Project**: ce `CHANGELOG.md`.

[Unreleased]: https://example.com/compare/v1.0.0...HEAD
[1.0.0]: https://example.com/releases/tag/v1.0.0
