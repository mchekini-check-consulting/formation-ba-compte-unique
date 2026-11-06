-- CU-16 (US-11) : blocage temporaire des connexions à un identifiant après 3 erreurs consécutives.
-- Une ligne par identifiant saisi, qu'il existe ou non (même blocage pour un identifiant inconnu,
-- pour ne pas révéler quels identifiants existent). Remise à zéro à la connexion réussie et à la
-- fin du blocage. Jamais de mot de passe.
create table blocage_identifiant (
    identifiant           varchar(32) primary key,
    erreurs_consecutives  integer     not null default 0,
    bloque_jusqu_au       timestamp with time zone
);
