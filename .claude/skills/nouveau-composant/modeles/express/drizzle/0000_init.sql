-- Point de départ Drizzle : aucun changement de schéma. Règles : une migration
-- appliquée ne se modifie plus ; l'écrire de façon idempotente (IF NOT EXISTS) et
-- compatible avec la version précédente (2 réplicas en preprod/prod pendant un déploiement).
-- Nouvelle migration : modifier src/db/schema.ts puis npm run db:generate.
SELECT 1;
