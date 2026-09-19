# TODO_LIST — RemotePad

## 2026-09-19 — Fix conflit d'installation APK (signature mismatch)

### Fait
- [x] Diagnostic : cause = signatures différentes entre variantes `debug` (clé de debug par défaut, `CN=Android Debug`) et `release` (`remotepad-release.keystore`, `CN=RemotePad`) pour le même `applicationId=com.remotepad` → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Écarté : `DUPLICATE_PERMISSION` (aucune `<permission>` custom) et collision d'authority (aucun `<provider>`).
- [x] Fix `android/app/build.gradle.kts` : la variante `debug` réutilise la clé release (garde sur la présence du keystore) → signature unique debug+release.
- [x] `versionCode` 4 → 5, `versionName` 1.2.0 → 1.2.1 (patch).
- [x] Build APK debug : `android/app/build/outputs/apk/debug/app-debug.apk` (signé `CN=RemotePad`, SHA-256 `82663978…`).
- [x] CHANGELOG mis à jour (2026-09-19). Commit local (non poussé).

### Action requise côté utilisateur
- [ ] **Désinstaller l'ancienne app RemotePad du téléphone une dernière fois** (`adb uninstall com.remotepad`, ou Réglages → Applis → RemotePad → Désinstaller), puis installer le nouvel APK. Nécessaire car l'app déjà installée est signée avec une clé différente ; après ce reset, les mises à jour futures (debug ou release) partageront la même clé et s'installeront sans conflit.

### Suivi / dette (hors périmètre de ce fix)
- [ ] Mots de passe du keystore en clair dans `build.gradle.kts` (dépôt public). Inoffensif tant que le `.keystore` reste gitignoré, mais à externaliser (ex. `keystore.properties` / variables d'env / `~/.gradle/gradle.properties`) pour la propreté portfolio.
- [ ] Aucun device Android connecté pendant ce fix : `adb install` non vérifié en réel. Cohérence de signature prouvée via `apksigner verify --print-certs`.
