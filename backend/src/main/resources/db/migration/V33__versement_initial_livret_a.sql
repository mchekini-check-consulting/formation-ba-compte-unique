-- CU-33 (US-24) : versement initial choisi par la cliente une fois l'ouverture de son Livret A validée. Seul le
-- montant est enregistré dans la demande, avec le compte courant à débiter et la date de saisie : le compte
-- courant n'est débité et le Livret A n'est créé qu'à la signature (CU-34). Champs vides tant que le montant
-- n'est pas choisi (et pour les demandes déjà existantes).
alter table demande_livret_a
    add column versement_initial_centimes bigint,
    add column compte_debit_id            bigint references compte (id),
    add column versement_saisi_le         timestamp with time zone;
