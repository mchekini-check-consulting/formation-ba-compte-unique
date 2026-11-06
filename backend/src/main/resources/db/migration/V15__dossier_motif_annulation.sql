-- CU-9 (US-04) : un seul profil client et un seul dossier d'ouverture en cours par personne.
-- motif_annulation : distingue une annulation par contrôle (bloque toute nouvelle ouverture en
--   ligne) d'une annulation par la cliente ou pour inactivité (ne bloque pas).
-- derniere_action_le : dernière action de la cliente sur le dossier (annulation après 30 jours).
-- attente_analyste : dossier en attente de l'analyste (PPE), jamais annulé pour inactivité.
alter table dossier
    add column motif_annulation   varchar(30),  -- CONTROLE_AUTOMATIQUE, DECISION_CONFORMITE, DEMANDE_CLIENTE,
                                                -- INACTIVITE, CODE_EXPIRE, TROIS_TENTATIVES
    add column annule_le          timestamp with time zone,
    add column derniere_action_le timestamp with time zone,
    add column attente_analyste   boolean not null default false;

update dossier set derniere_action_le = coalesce(controle_le, saisi_le, now());
alter table dossier
    alter column derniere_action_le set default now(),
    alter column derniere_action_le set not null;

-- Dossiers déjà annulés : motif retrouvé à partir des contrôles (CUB-8) ou de la signature (CU-12).
update dossier
set motif_annulation = 'CONTROLE_AUTOMATIQUE', annule_le = controle_le
where statut = 'ANNULE' and (controle_sanctions = 'NOK' or controle_pep = 'NOK');

update dossier d
set motif_annulation = case s.motif_annulation
        when 'Code expiré' then 'CODE_EXPIRE'
        when '3 tentatives dépassées' then 'TROIS_TENTATIVES'
        else 'DEMANDE_CLIENTE' end
from signature_convention s
where s.dossier_id = d.id and d.statut = 'ANNULE' and d.motif_annulation is null and s.statut_enveloppe = 'ANNULEE';

-- Tâche quotidienne : dossiers en cours sans action depuis 30 jours.
create index dossier_inactivite on dossier (statut, derniere_action_le);
