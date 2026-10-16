# StatusPrep

Application Android personnelle pour **préparer localement une vidéo avant de la publier en statut WhatsApp** : réduction de la résolution et du débit avec un bon compromis netteté / poids / compatibilité.

- Kotlin, Jetpack Compose, Material 3, interface en français, thèmes clair et sombre.
- Traitement avec [Media3 Transformer](https://developer.android.com/media/media3/transformer) (accélération matérielle quand l'appareil la propose). Pas de FFmpeg.
- 100 % local : pas de WebView, serveur, compte, publicité ni analytics. **Aucune permission Internet.**
- Android 10 (API 29) minimum.

> StatusPrep s'inspire de l'usage de PureStatus, dont le fonctionnement interne est inconnu. Il **ne reproduit pas son algorithme**, ne contourne pas la compression de WhatsApp et **ne garantit pas une publication sans perte**. WhatsApp recompresse ce qu'il reçoit ; l'objectif est seulement de lui fournir un fichier propre et raisonnable.

## Fonctionnement

1. Choisir une vidéo avec le sélecteur système (aucune permission de stockage).
2. Aperçu et caractéristiques : durée, dimensions, poids, codec, débit, cadence, audio.
3. Choisir un profil (voir ci-dessous).
4. Lancer : progression réelle quand Media3 la fournit (sinon barre indéterminée), annulation possible.
5. Résultat : aperçu, comparaison avant/après mesurée sur le fichier produit.
6. Enregistrer dans la galerie (`Films/StatusPrep`) ou partager vers WhatsApp / WhatsApp Business / autre application. Vous choisissez vous-même « Mon statut » dans WhatsApp.

L'original n'est jamais modifié. Si le résultat est plus lourd que l'original, l'application le signale et propose de partager l'original.

### Profils (expérimentaux, pas des réglages officiels WhatsApp)

| Profil | Résolution max. | Débit vidéo cible | Audio |
|---|---|---|---|
| Équilibré | 720p | ≈ 2,5 Mb/s | AAC 96 kb/s |
| Détaillé | 1080p | ≈ 5 Mb/s | AAC 128 kb/s |
| Personnalisé | 480p / 720p / 1080p | 1 à 8 Mb/s | AAC 128 kb/s |

Règles communes : sortie MP4, H.264, AAC si la source a de l'audio, 30 images/s maximum (une cadence plus faible n'est jamais augmentée), proportions et orientation conservées, aucun recadrage / étirement / bande / agrandissement.

- « 720p » signifie « tient dans un cadre 1280×720 » en paysage et **720×1280 en portrait** ; une vidéo carrée tient dans 720×720.
- Le débit est une **cible, pas une garantie** : l'encodeur matériel peut produire une autre valeur. L'écran résultat affiche la valeur réellement mesurée.
- **Copie sans ré-encodage :** une source déjà conforme (H.264, dans le cadre du profil, ≤ 30 images/s, débit ≤ cible +10 %, audio AAC ou absent, pas de HDR) est simplement recopiée en MP4, sans perte supplémentaire. Sinon la vidéo est ré-encodée.
- Réglages d'encodeur : débit variable (VBR), images clés toutes les 3 s (au lieu de 1 s par défaut, ce qui libère du débit pour l'image) ; le profil H.264 High est choisi par Media3 sur Android 10+. Les B-frames ne sont pas activées (non fiables sur certains appareils).
- Le débit n'est pas relevé au-delà de celui de la source quand il est connu (avec une marge pour les sources HEVC/VP9/AV1, plus efficaces que H.264).
- Source HDR : conversion SDR par tone mapping (MediaCodec, puis OpenGL en repli). Si l'appareil ne sait pas le faire, le traitement est refusé avec un message explicite plutôt que de produire des couleurs délavées.

**Où modifier les réglages après essais :** [`app/src/main/java/app/statusprep/core/EncodingConfig.kt`](app/src/main/java/app/statusprep/core/EncodingConfig.kt) (débits, résolutions, cadence max, bornes du profil personnalisé, durée de conservation des fichiers temporaires). Les calculs sont dans `OutputPlanner.kt` et couverts par des tests.

### Traitement en arrière-plan : mécanisme et limites

Le traitement s'exécute dans un **service de premier plan** (`TranscodeService`) avec notification permanente (bouton « Annuler ») et un `WakeLock` partiel. Il continue si vous quittez l'écran ou changez d'application ; la rotation ne l'interrompt pas (l'état vit au niveau du processus).

Limites à connaître :
- Android 13+ : la notification n'est visible que si vous acceptez l'autorisation de notification (demandée une fois). Le traitement fonctionne même si vous refusez.
- Android 15+ : les services de type `mediaProcessing` ont une durée maximale cumulée ; si elle est atteinte, le système arrête le service et l'application affiche une erreur (le fichier partiel est supprimé).
- « Forcer l'arrêt », un redémarrage du téléphone ou une économie d'énergie très agressive du constructeur peuvent tuer le traitement. Il n'est pas repris automatiquement.
- Le service doit être démarré depuis l'application au premier plan (règle Android 12+).

Fichiers temporaires : le résultat vit dans le cache privé de l'application (`cache/outputs`), partagé via `FileProvider` avec autorisation de lecture temporaire. Il n'est **pas** supprimé après un partage ; les fichiers de plus de 3 jours et tout fichier partiel d'un traitement interrompu sont nettoyés au démarrage.

## 1. Lancer le workflow depuis l'onglet Actions

Le workflow [`android-build.yml`](.github/workflows/android-build.yml) se lance automatiquement à chaque `push` et `pull_request`. Pour le lancer à la main :

1. Ouvrez le dépôt sur GitHub, onglet **Actions**.
2. Dans la colonne de gauche, choisissez **Android build**.
3. Cliquez sur **Run workflow**, sélectionnez la branche, puis **Run workflow**.
4. Attendez la fin (quelques minutes). Il exécute : tests unitaires, lint, puis `assembleDebug`.

Aucun secret n'est nécessaire.

## 2. Télécharger et extraire l'APK

1. Dans **Actions**, ouvrez l'exécution terminée (coche verte).
2. Tout en bas, section **Artifacts**, cliquez sur **StatusPrep-debug** (fichier `.zip`).
3. Extrayez le zip (sur le téléphone : application Fichiers, appui long → *Extraire* ; sur ordinateur : clic droit → *Extraire tout*).
4. Vous obtenez `app-debug.apk`.

En cas d'échec, un second artifact **StatusPrep-reports** contient les rapports de tests et de lint.

## 3. Installer l'APK sur le téléphone

1. Copiez `app-debug.apk` sur le téléphone (ou téléchargez-le directement dessus).
2. Ouvrez-le depuis l'application Fichiers ou Téléchargements.
3. Android demande d'autoriser l'installation d'applications inconnues pour l'application que vous utilisez (Fichiers, Chrome…) : **Paramètres → Autoriser depuis cette source**, puis revenez en arrière.
4. Touchez **Installer**. Si Play Protect avertit d'une application non reconnue, choisissez *Installer quand même* (le projet est le vôtre et son code est dans ce dépôt).

Variante avec ordinateur : `adb install -r app-debug.apk` (débogage USB activé).

## 4. Limites de signature des builds debug

L'APK debug est signé avec la **clé de debug générée automatiquement** par Android Gradle Plugin. Sur un runner GitHub éphémère, cette clé est recréée à chaque exécution : **deux builds successifs peuvent avoir des signatures différentes**. Conséquence : Android refuse d'installer un build par-dessus un autre (« application non installée / conflit de signature ») ; il faut alors **désinstaller l'ancienne version** (ses réglages locaux sont perdus ; l'application ne stocke de toute façon pas de données importantes).

Aucune signature stable n'est promise sans configuration. Pour obtenir des mises à jour par-dessus une installation existante, suivez [docs/SIGNATURE_STABLE.md](docs/SIGNATURE_STABLE.md) (keystore + GitHub Secrets, sans clé dans le dépôt).

## 5. Comparer les profils après publication réelle sur WhatsApp

Les chiffres des profils sont des hypothèses. Pour savoir ce qui marche vraiment :

1. Prenez **la même vidéo source** (idéalement 20-30 s avec du mouvement, du texte et des détails fins).
2. Produisez-la avec chaque profil (Équilibré, Détaillé, et un ou deux Personnalisés, par exemple 720p à 4 Mb/s et 1080p à 3 Mb/s).
3. Notez pour chacun, dans l'écran résultat : poids, dimensions et débit **mesuré**.
4. Publiez chaque version en statut WhatsApp (sur un statut visible seulement par un contact de test, ou un second compte), dans le même ordre et avec la même connexion.
5. Regardez chaque statut **depuis un autre téléphone**, en plein écran, sur le même appareil pour tous. Comparez : netteté du texte, blocs dans les aplats/dégradés, fluidité, temps d'envoi, et dites si WhatsApp a re-décompressé visiblement.
6. Comparez aussi à la publication de l'**original** (le bouton « Partager l'original » existe pour ça).
7. Reportez vos conclusions dans `EncodingConfig.kt` (débits et résolutions) et relancez le workflow.

Un tableau simple suffit : `profil | poids | débit mesuré | netteté /5 | artefacts | verdict`.

## Développement

- Versions épinglées dans [`gradle/libs.versions.toml`](gradle/libs.versions.toml) : AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.00, Media3 1.8.0, Gradle 8.14.3 (wrapper). Aucune dépendance dynamique.
- Build local (JDK 17 et SDK Android requis) : `./gradlew testDebugUnitTest lintDebug assembleDebug`. L'APK est dans `app/build/outputs/apk/debug/`.
- Tests unitaires (calculs de dimensions, profils, proportions, formatage) : `app/src/test`.
