# Mini Volume

Bulle flottante déplaçable pour régler le volume (slider 0-100, boutons +/-),
ouverture de l'app depuis la bulle ou la notification, et menu Shizuku
(Reboot, Soft reboot, Recovery, Download).

Projet Gradle construit par GitHub Actions (le workflow est dans .github/workflows/build.yml).
Shizuku est inclus via Maven : plus besoin de jars à la main.

## Récupérer l'APK

1. Onglet Actions du dépôt : le build se lance à chaque push (ou bouton "Run workflow").
2. Quand il est vert : onglet Releases (à droite de la page du dépôt), release "Mini Volume (dernier build)",
   télécharger app-debug.apk. Sinon : dans le run, section Artifacts.

## Utiliser Shizuku

1. Installer et démarrer l'app Shizuku (débogage sans fil sur Android 11+).
2. Ouvrir Mini Volume, "Autoriser Shizuku", valider.

Limites : Soft reboot seulement en mode root ; Download = mode Samsung.
