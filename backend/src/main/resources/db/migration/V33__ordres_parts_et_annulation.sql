-- CU-376 (TECH-08), compléments : ordre en nombre de parts (rachat, arbitrage) et annulation d'un ordre.
-- Changements qui ajoutent seulement : les ordres déjà enregistrés (en euros, jamais annulés) restent valides.

-- Un ordre est exprimé soit en euros (montant), soit en nombre de parts du support vendu, jamais les deux.
-- Un ordre en parts bloque nombre de parts × dernière valeur liquidative du catalogue, recalculé à chaque lecture.
alter table ordres.ordre alter column montant drop not null;
alter table ordres.ordre add column nombre_parts numeric(18, 6) check (nombre_parts > 0);
alter table ordres.ordre add constraint ordre_montant_ou_parts check ((montant is null) <> (nombre_parts is null));

-- Date et heure métier de l'annulation (TECH-12) ; vide tant que l'ordre n'est pas annulé.
alter table ordres.ordre add column annule_le timestamp with time zone;
