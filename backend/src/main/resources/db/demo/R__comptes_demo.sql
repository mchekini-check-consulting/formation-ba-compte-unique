-- Comptes de test de CU-15, en attendant CU-13 et CU-14. Chargés en local, dev et qualif
-- uniquement (SPRING_FLYWAY_LOCATIONS, helm.yaml) : jamais en preprod ni en prod.
-- Compte fictif, sans donnée réelle : identifiant TEST-ACTIF, mot de passe Demo-Connexion1!
insert into identifiant_connexion (identifiant, mot_de_passe_hache, statut, mot_de_passe_defini_le)
values ('TEST-ACTIF', '$argon2id$v=19$m=19456,t=2,p=1$F9EBJ5M+w6iRouSP4fTqLw$mszuIh0THXtxwJXxiPuBv2aRVrwsKVdZGq9750nhk2k', 'ACTIF', now())
on conflict (identifiant) do update
set mot_de_passe_hache = excluded.mot_de_passe_hache,
    statut = excluded.statut,
    mot_de_passe_defini_le = excluded.mot_de_passe_defini_le;

insert into identifiant_connexion (identifiant, statut)
values ('TEST-ATTENTE', 'EN_ATTENTE_ACTIVATION')
on conflict (identifiant) do nothing;

-- CU-20 et CU-19 : clientes fictives ayant chacune un compte, même mot de passe que TEST-ACTIF.
-- A : 1 240,50 €. B : ouvert sans versement. CLOTURE : compte clôturé. NEGATIF : solde refusé.
insert into identifiant_connexion (identifiant, mot_de_passe_hache, statut, mot_de_passe_defini_le)
select v.identifiant, '$argon2id$v=19$m=19456,t=2,p=1$F9EBJ5M+w6iRouSP4fTqLw$mszuIh0THXtxwJXxiPuBv2aRVrwsKVdZGq9750nhk2k', 'ACTIF', now()
from (values ('TEST-CLIENTE-A'), ('TEST-CLIENTE-B'), ('TEST-CLIENTE-CLOTURE'), ('TEST-CLIENTE-NEGATIF')) as v(identifiant)
on conflict (identifiant) do update
set mot_de_passe_hache = excluded.mot_de_passe_hache,
    statut = excluded.statut,
    mot_de_passe_defini_le = excluded.mot_de_passe_defini_le;

insert into dossier (reference, civilite, nom_naissance, prenoms, date_naissance, commune_naissance, pays_naissance,
    indice_americain, nationalite, voie, code_postal, ville, pays_adresse, email, mobile, type_piece, numero_piece,
    date_expiration_piece, residence_francaise, saisi_le, controle_sanctions, controle_pep, controle_le, statut, customer_id)
select v.reference, 'Mme', v.nom, 'Test', date '1990-01-01', 'Paris', 'FRANCE', false, 'FRANCE', '1 rue de Test', '75001',
    'Paris', 'France', v.email, '0600000000', 'CARTE_NATIONALE_IDENTITE', v.piece, date '2035-01-01', true, now(), 'OK', 'OK',
    now(), 'COMPTE_OUVERT', v.customer_id
from (values
    ('demo-cliente-a', 'Cliente-A', 'cliente-a@example.test', 'DEMOA', '00000000-0000-4000-8000-00000000000a'::uuid),
    ('demo-cliente-b', 'Cliente-B', 'cliente-b@example.test', 'DEMOB', '00000000-0000-4000-8000-00000000000b'::uuid),
    ('demo-cliente-cloture', 'Cliente-Cloture', 'cliente-cloture@example.test', 'DEMOC', '00000000-0000-4000-8000-00000000000c'::uuid),
    ('demo-cliente-negatif', 'Cliente-Negatif', 'cliente-negatif@example.test', 'DEMOD', '00000000-0000-4000-8000-00000000000d'::uuid)
) as v(reference, nom, email, piece, customer_id)
on conflict do nothing;

insert into compte (dossier_id, customer_id, iban, numero_compte, solde_centimes, solde_mis_a_jour_le, statut, ouvert_le, identifiant_connexion_id)
select d.id, d.customer_id, v.iban, v.numero_compte, v.solde_centimes, v.solde_mis_a_jour_le, v.statut,
    timestamptz '2026-10-01 09:00:00+02', i.id
from (values
    ('demo-cliente-a', 'TEST-CLIENTE-A', 'FR7699999000019900000000105', '99000000001', 124050, now(), 'ACTIF'),
    ('demo-cliente-b', 'TEST-CLIENTE-B', 'FR7699999000019900000000202', '99000000002', 0, timestamptz '2026-10-01 09:00:00+02', 'ACTIF'),
    ('demo-cliente-cloture', 'TEST-CLIENTE-CLOTURE', 'FR7699999000019900000000396', '99000000003', 0, now(), 'CLOTURE'),
    ('demo-cliente-negatif', 'TEST-CLIENTE-NEGATIF', 'FR7699999000019900000000493', '99000000004', -500, now(), 'ACTIF')
) as v(reference, identifiant, iban, numero_compte, solde_centimes, solde_mis_a_jour_le, statut)
join dossier d on d.reference = v.reference
join identifiant_connexion i on i.identifiant = v.identifiant
on conflict (dossier_id) do update
set solde_centimes = excluded.solde_centimes,
    solde_mis_a_jour_le = excluded.solde_mis_a_jour_le,
    statut = excluded.statut,
    ouvert_le = excluded.ouvert_le;
