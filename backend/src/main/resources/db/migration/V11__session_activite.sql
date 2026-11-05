-- CU-17 : déconnexion et fermeture de la session après 10 minutes sans activité.
-- derniere_activite_le : dernière action de la cliente (un rafraîchissement automatique ne compte pas).
-- revoquee_le : date de la déconnexion ou de l'expiration ; une session révoquée ne sert plus jamais.
alter table session_client add column derniere_activite_le timestamp with time zone;
update session_client set derniere_activite_le = cree_le;
alter table session_client alter column derniere_activite_le set not null;

alter table session_client add column revoquee_le timestamp with time zone;
