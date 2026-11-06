-- CU-48 : blocage d'une adresse IP pendant 15 minutes après 20 échecs de connexion en 15 minutes.
-- Les échecs de la fenêtre glissante sont comptés dans journal_securite (événements CONNEXION_ECHOUEE
-- de l'adresse) ; seule la date de fin de blocage est gardée ici.
create table blocage_adresse_ip (
    adresse_ip       varchar(45) primary key,
    bloque_jusqu_au  timestamp with time zone not null
);
