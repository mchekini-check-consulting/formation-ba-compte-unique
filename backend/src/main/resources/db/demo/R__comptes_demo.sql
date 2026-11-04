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
