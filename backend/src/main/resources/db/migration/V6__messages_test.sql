-- CU-87 : dernier e-mail et dernier SMS envoyés dans un environnement de test (local, dev,
-- qualif), affichés sur les pages /test/emails et /test/sms. Une ligne par canal : chaque
-- nouveau message remplace le précédent. Rien n'est enregistré en preprod ni en prod.
create table message_test (
    canal         varchar(5)    primary key,  -- EMAIL, SMS
    destinataire  varchar(254)  not null,
    objet         varchar(200),
    texte         varchar(2000) not null,
    envoye_le     timestamp with time zone not null
);
