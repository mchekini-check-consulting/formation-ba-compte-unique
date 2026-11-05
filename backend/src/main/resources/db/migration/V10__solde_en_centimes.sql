-- CU-19 (US-14) : solde en centimes (nombre entier) et date de sa dernière mise à jour.
-- Les soldes existants sont convertis avant la suppression de l'ancien champ en euros.
-- La date de mise à jour vaut la date d'ouverture tant qu'aucun versement n'a eu lieu.
alter table compte
    add column solde_centimes       bigint,
    add column solde_mis_a_jour_le  timestamp with time zone;

update compte
set solde_centimes = round(solde * 100),
    solde_mis_a_jour_le = ouvert_le;

alter table compte
    alter column solde_centimes set not null,
    alter column solde_centimes set default 0,
    alter column solde_mis_a_jour_le set not null,
    drop column solde;
