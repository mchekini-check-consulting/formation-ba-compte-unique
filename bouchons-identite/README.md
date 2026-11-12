# bouchons-identite

Bouchons des partenaires d'identification, pour développer et tester le parcours
d'ouverture de compte sans attendre les contrats. Aucune image n'est reçue ni
conservée : les pièces et selfies sont désignés par un **code de test**.

Via le gateway : `https://gateway.<dépôt>.<env>.check-consulting.net/bouchons-identite/...`

## Contrôle sanctions et PEP (CUB-70)

`POST /sanctions-pep/controles` `{nomNaissance, prenoms, dateNaissance, nationalite, casDeTest?}`

`casDeTest` (facultatif) : `CONFORME`, `SANCTIONNE`, `PEP` ou `INDISPONIBLE`, choisi dans
le panneau de test de l'écran « Mon identité ». S'il est présent, il remplace la
recherche par nom ci-dessous.

| Prénoms + nom | Résultat |
|---|---|
| Test Sanctionné | `CORRESPONDANCE_SANCTIONS`, dossier annulé |
| Test PEP | `CORRESPONDANCE_PEP`, dossier annulé |
| Test Conforme (et toute autre identité, même proche) | `AUCUNE_CORRESPONDANCE` |
| Test Indisponible | mode indisponible : pas de réponse avant 5 s, puis erreur 503 |

Rien n'est enregistré par ce bouchon.

## Pièce d'identité Stripe Identity (CUB-63)

`POST /stripe-identity/verifications-document`
`{dossierId, recto, verso, saisie: {nomNaissance, prenoms, dateNaissance, dateExpiration?}}`

| Code (recto et verso) | Résultat |
|---|---|
| `PIECE_CONFORME_A_LA_SAISIE` | pièce valide qui porte l'identité saisie : `VERIFIE` avec n'importe quelle identité (expire à la date saisie, sinon dans 10 ans ; date saisie dépassée : `REJETE`) |
| `PIECE_VALIDE` | Dupont / Marie Claire / 14/05/1990 / expire le 30/06/2031 : `VERIFIE` si la saisie concorde, sinon `REJETE` (« les informations de la pièce ne correspondent pas à la saisie ») et dossier annulé |
| `PIECE_VALIDE_AUTRE_PERSONNE` | Martin / Paul / 03/02/1985 / expire le 15/09/2030 |
| `PIECE_EXPIREE` | `REJETE`, « document expiré » |
| `IMAGE_ILLISIBLE` | `A_REPRENDRE` avec le motif |
| `ERREUR_TECHNIQUE` | `ERREUR_TECHNIQUE` : réessayer, dossier « En cours de vérification » |
| `SERVICE_INDISPONIBLE` | `INDISPONIBLE` après 2 relances : « Vérification impossible, réessayez plus tard » |

Enregistré : statut, motif, date, concordance par champ.

## Selfie Stripe Identity (CUB-64)

`POST /stripe-identity/verifications-selfie` `{dossierId, selfie}`

| Code | Résultat |
|---|---|
| `SELFIE_CONFORME` | `CORRESPONDANCE`, score 96 |
| `SELFIE_DIFFERENT` | `NON_CORRESPONDANCE`, score 42, dossier annulé |
| `SCORE_<n>` (0 à 100) | correspondance si n ≥ 90, sinon dossier annulé |
| `PHOTO_ECRAN` | `ECHEC_VERIFICATION_PRESENCE`, dossier annulé |
| `SERVICE_INDISPONIBLE` | `INDISPONIBILITE_TECHNIQUE` après 2 relances |

Enregistré : résultat, score, statut du dossier, date.

Chaque réponse indique aussi `suite` (`POURSUIVRE`, `REPRENDRE`, `REESSAYER`,
`REESSAYER_PLUS_TARD`, `ANNULER`), `statutDossier` et le `message` à afficher.

## Signature Docusign (CU-89)

Simule l'enveloppe de signature, l'envoi du code SMS et le résultat, sans aucun appel à
Docusign ni envoi de SMS.

| Appel | Réponse |
|---|---|
| `POST /docusign/enveloppes` `{dossierId}` | `ENVELOPPE_ENVOYEE` et un `enveloppeId` (le code SMS est « envoyé ») |
| `POST /docusign/enveloppes/{enveloppeId}/code` `{code: "111111"}` | `SIGNATURE_VALIDE` |
| `POST /docusign/enveloppes/{enveloppeId}/code` `{code: "000000"}` (et tout autre code à 6 chiffres) | `CODE_FAUX` |
| `POST /docusign/enveloppes/{enveloppeId}/code` `{code: "999999"}` | `CODE_EXPIRE` |
| `POST /docusign/enveloppes/{enveloppeId}/annulation` | `ANNULATION_DEMANDEE` |

Enregistré : chaque appel (date, dossier, enveloppe, code reçu, résultat).

## Administration fiscale, Livret A (CU-295)

Simule l'interrogation du fichier FICOBA pour le contrôle « un seul Livret A par personne »
(CU-31), sans aucun appel réel.

`POST /administration-fiscale/interrogations`
`{demandeId, nom, prenoms, sexe (F ou M), dateNaissance, lieuNaissance, partageDetailAutorise, casDeTest?}`

`casDeTest` est choisi dans le panneau de test de l'écran « Ouvrir mon Livret A ». Sans cas :
`SANS_LIVRET_A`.

| `casDeTest` | Réponse (`resultat`) |
|---|---|
| `SANS_LIVRET_A` | `AUCUN_LIVRET_A` : « aucun autre Livret A », l'ouverture est validée |
| `DETENU_AILLEURS` | `LIVRET_A_EXISTANT`, avec `detail` (Banque Exemple, Agence Paris Centre, 02/03/2015) seulement si `partageDetailAutorise` vaut `true`, sinon sans détail |
| `SANS_REPONSE` | `SANS_REPONSE` : pas de réponse, la demande reste en attente (délai de 2 jours ouvrés) |
| `INDISPONIBLE` | `INDISPONIBLE` : service indisponible, la demande reste en attente |

Pour simuler l'expiration du délai de 2 jours ouvrés sans attendre, utiliser le bouton du
panneau de test, qui appelle le backend :
`POST /backend/test/livret-a/demandes/{reference}/expiration-delai`.

Enregistré : chaque appel (date, référence de la demande, cas de test, résultat, détail
communiqué ou non). Jamais l'identité transmise.

Actif seulement en local, dev et qualif (`BOUCHON_FISCAL_ACTIF`, `helm.yaml`) : en preprod et
prod, l'adresse n'existe pas (404).

## En local

- Base : `docker compose up -d` (PostgreSQL sur le port **5433**, base `bouchons_identite`)
- Application : `SERVER_PORT=8081 ./mvnw spring-boot:run` → http://localhost:8081

## Contrôles

`make lint`, `make test-unit`, `make test-integration` (Docker requis).
