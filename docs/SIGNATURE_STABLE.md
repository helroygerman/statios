# Signature stable de l'APK (optionnel)

Par défaut, le workflow produit un APK **debug** signé avec une clé de debug éphémère (voir le README). Pour pouvoir **mettre à jour par-dessus** une installation précédente, il faut signer toujours avec la même clé. Voici comment, **sans jamais committer de clé privée**.

> Ce n'est pas activé dans le dépôt : rien ne fonctionne tant que vous n'avez pas créé la clé et les secrets ci-dessous.

## 1. Créer le keystore (une seule fois, sur votre machine)

```bash
keytool -genkeypair -v -keystore statusprep.jks -alias statusprep \
  -keyalg RSA -keysize 4096 -validity 10000
```

Conservez `statusprep.jks` et ses mots de passe en lieu sûr (gestionnaire de mots de passe). **Perdre la clé = impossibilité de mettre à jour** les installations existantes. Ne la committez pas (`*.jks` est ignoré par `.gitignore`).

## 2. Encoder et enregistrer les secrets GitHub

```bash
base64 -w0 statusprep.jks > statusprep.jks.b64    # macOS : base64 -i statusprep.jks
```

Dans GitHub : **Settings → Secrets and variables → Actions → New repository secret** :

| Secret | Contenu |
|---|---|
| `STATUSPREP_KEYSTORE_BASE64` | contenu de `statusprep.jks.b64` |
| `STATUSPREP_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `STATUSPREP_KEY_ALIAS` | `statusprep` |
| `STATUSPREP_KEY_PASSWORD` | mot de passe de la clé |

Supprimez ensuite le fichier `.b64` local.

## 3. Workflow de release signée

`app/build.gradle.kts` active déjà la configuration de signature `stable` pour le build **release** quand la variable `STATUSPREP_KEYSTORE_PATH` est définie. Créez un workflow manuel dédié, par exemple `.github/workflows/android-release-signed.yml` :

```yaml
name: Android signed release
on: workflow_dispatch
permissions:
  contents: read
jobs:
  release:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4.2.2
      - uses: gradle/actions/wrapper-validation@v4.4.4
      - uses: actions/setup-java@v4.7.1
        with: { distribution: temurin, java-version: "17" }
      - uses: android-actions/setup-android@v3.2.2
        with: { packages: "platform-tools platforms;android-36 build-tools;36.0.0" }
      - uses: gradle/actions/setup-gradle@v4.4.4
      - name: Décoder le keystore
        env:
          KEYSTORE_B64: ${{ secrets.STATUSPREP_KEYSTORE_BASE64 }}
        run: echo "$KEYSTORE_B64" | base64 -d > "$RUNNER_TEMP/statusprep.jks"
      - name: Construire la release signée
        env:
          STATUSPREP_KEYSTORE_PATH: ${{ runner.temp }}/statusprep.jks
          STATUSPREP_KEYSTORE_PASSWORD: ${{ secrets.STATUSPREP_KEYSTORE_PASSWORD }}
          STATUSPREP_KEY_ALIAS: ${{ secrets.STATUSPREP_KEY_ALIAS }}
          STATUSPREP_KEY_PASSWORD: ${{ secrets.STATUSPREP_KEY_PASSWORD }}
        run: ./gradlew --no-daemon assembleRelease
      - uses: actions/upload-artifact@v4.6.2
        with:
          name: StatusPrep-release
          path: app/build/outputs/apk/release/*.apk
          if-no-files-found: error
```

Notes :
- Les secrets ne sont pas transmis aux workflows déclenchés par des pull requests de forks : n'exécutez ce workflow qu'à la main (`workflow_dispatch`) sur vos branches.
- Si les secrets manquent, la release n'est pas signée (`app-release-unsigned.apk`) et n'est pas installable : vérifiez les noms des secrets.
- Un APK release signé avec votre clé et un APK debug ont des signatures différentes : désinstallez la version debug avant d'installer la première version signée.
