-- CU-144 (TECH-05) : virement sortant simulé (bouchon de test). Le mouvement d'un virement sortant porte le
-- bénéficiaire (nom et IBAN) ; l'émetteur ne concerne que les virements entrants et devient donc facultatif.
-- Ajouts seulement : l'ancien code (virements entrants) continue de fonctionner pendant un déploiement.
alter table mouvement
    alter column emetteur drop not null,
    add column beneficiaire       varchar(70),
    add column iban_beneficiaire  varchar(34);

-- Chaque mouvement a sa contrepartie : l'émetteur d'un virement entrant, le bénéficiaire d'un virement sortant.
alter table mouvement
    add constraint mouvement_contrepartie check (
        (type = 'VIREMENT_ENTRANT' and emetteur is not null)
        or (type = 'VIREMENT_SORTANT' and beneficiaire is not null and iban_beneficiaire is not null)
    );
