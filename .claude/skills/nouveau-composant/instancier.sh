#!/usr/bin/env bash
# Crée un composant à partir d'un modèle du skill nouveau-composant.
#   bash .claude/skills/nouveau-composant/instancier.sh <modèle> <dossier> CLE=valeur...
#   ex. bash .claude/skills/nouveau-composant/instancier.sh spring contrats SLUG=contrats TITRE="Contrats" ...
# - copie modeles/<modèle>/ dans <dossier>/ (qui ne doit pas exister) ;
# - renomme dot-xxx en .xxx et remplace les jetons __CLE__ dans les noms de fichiers et de dossiers ;
# - remplace les jetons __CLE__ dans le contenu des fichiers ;
# - échoue si un jeton reste sans valeur (hors mvnw, qui a ses propres __MVNW_...__).
set -euo pipefail

ici="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
modele="${1:?modèle : spring | express | django | angular | react}"
dest="${2:?dossier du composant à créer}"
shift 2

source_modele="$ici/modeles/$modele"
[ -d "$source_modele" ] || { echo "modèle inconnu : $modele" >&2; exit 1; }
[ ! -e "$dest" ] || { echo "$dest existe déjà : rien n'est écrasé" >&2; exit 1; }

declare -A valeurs=()
for paire in "$@"; do
  cle="${paire%%=*}"
  valeurs["$cle"]="${paire#*=}"
done

# Frontend : BACKENDS="contrats:8081,sinistres:3001" (backends appelés et leur port local)
# -> relais du serveur de dev /api/<backend> -> http://localhost:<port> (Angular et Vite)
if [ -n "${valeurs[BACKENDS]+x}" ]; then
  angular="" vite=""
  IFS=',' read -ra liste <<< "${valeurs[BACKENDS]}"
  for entree in "${liste[@]}"; do
    [ -n "$entree" ] || continue
    nom="${entree%%:*}" port="${entree#*:}"
    angular+="${angular:+,}"$'\n'"  \"/api/$nom\": {"$'\n'"    \"target\": \"http://localhost:$port\","$'\n'"    \"pathRewrite\": { \"^/api/$nom\": \"\" }"$'\n'"  }"
    vite+=$'\n'"      '/api/$nom': {"$'\n'"        target: 'http://localhost:$port',"$'\n'"        rewrite: (path) => path.replace(/^\\/api\\/$nom/, ''),"$'\n'"      },"
  done
  valeurs[PROXY_ANGULAR]="{${angular:+$angular$'\n'}}"
  valeurs[PROXY_VITE]="{${vite:+$vite$'\n'    }}"
  unset 'valeurs[BACKENDS]'
fi

cp -R "$source_modele" "$dest"

# Noms : dot-xxx -> .xxx, puis jetons (les dossiers les plus profonds d'abord)
find "$dest" -depth \( -name '*__*__*' -o -name 'dot-*' \) | while IFS= read -r chemin; do
  nom="$(basename "$chemin")"
  nouveau="$nom"
  [[ "$nouveau" == dot-* ]] && nouveau=".${nouveau#dot-}"
  for cle in "${!valeurs[@]}"; do
    nouveau="${nouveau//__${cle}__/${valeurs[$cle]}}"
  done
  if [ "$nouveau" != "$nom" ]; then
    cible="$(dirname "$chemin")/$nouveau"
    mkdir -p "$(dirname "$cible")"
    mv "$chemin" "$cible"
  fi
done

# Contenu : uniquement les fichiers texte qui contiennent un jeton
grep -rlIZ '__[A-Z_]*__' "$dest" 2>/dev/null | while IFS= read -r -d '' fichier; do
  for cle in "${!valeurs[@]}"; do
    VALEUR="${valeurs[$cle]}" CLE="__${cle}__" perl -0pi -e 's/\Q$ENV{CLE}\E/$ENV{VALEUR}/g' "$fichier"
  done
done

[ -f "$dest/mvnw" ] && chmod +x "$dest/mvnw"

restants="$(grep -rnoI --exclude='mvnw*' '__[A-Z][A-Z_]*__' "$dest" || true)"
if [ -n "$restants" ]; then
  echo "jetons sans valeur :" >&2
  echo "$restants" >&2
  exit 1
fi
echo "$dest créé depuis le modèle $modele"
