-- CU-9 (US-04) et CU-48 : le journal de sécurité reçoit aussi les doublons détectés à l'ouverture
-- et les événements d'authentification (connexion, échec, blocage, activation, réinitialisation,
-- déconnexion, expiration de session). Jamais de mot de passe, de jeton ni de numéro de pièce.
-- identifiant_connexion_id et compte_demande deviennent facultatifs : un doublon ou un échec sur
-- un identifiant inconnu ne concernent aucun client, seul l'accès à un compte (CU-20) a un compte demandé.
alter table journal_securite
    alter column identifiant_connexion_id drop not null,
    alter column compte_demande drop not null,
    add column identifiant_saisi varchar(32),
    add column adresse_ip        varchar(45),
    add column resultat          varchar(10),  -- REUSSITE, ECHEC
    add column motif             varchar(40);

-- CU-48 : échecs récents d'une adresse IP (fenêtre de 15 minutes) et purge après 6 mois.
create index journal_securite_ip_recents on journal_securite (adresse_ip, evenement, survenu_le);
create index journal_securite_survenu_le on journal_securite (survenu_le);
