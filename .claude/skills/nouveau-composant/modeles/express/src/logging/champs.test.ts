import { describe, expect, it } from 'vitest';
import { erreurs, extraire, personnel, verifier } from './champs.js';

describe('champs', () => {
  const demande = {
    reference: 'ref-1',
    formule: 'confort',
    options: ['objets-valeur', 'assistance'],
    logement: { surface: 60, adresse: '12 rue des Lilas' },
    creeLe: new Date('2026-11-07T10:00:00Z'),
  };

  it('extrait les valeurs simples, listes jointes, taille, dates', () => {
    expect(
      extraire(
        [
          'demande.formule',
          'demande.options',
          'demande.options.taille',
          'demande.logement.surface',
          'demande.creeLe',
          'resultat.ok',
        ],
        { demande, resultat: { ok: true } },
      ),
    ).toEqual({
      'demande.formule': 'confort',
      'demande.options': 'objets-valeur,assistance',
      'demande.options.taille': '2',
      'demande.logement.surface': '60',
      'demande.creeLe': '2026-11-07T10:00:00.000Z',
      'resultat.ok': 'true',
    });
  });

  it('ignore les chemins absents, non simples ou en erreur', () => {
    const enErreur = {
      get statut() {
        throw new Error('chargement impossible');
      },
    };
    expect(
      extraire(['demande.logement', 'demande.absent.x', 'autre.x', 'objet.statut', 'vide.x'], {
        demande,
        objet: enErreur,
        vide: null,
      }),
    ).toEqual({});
  });

  it('refuse données personnelles, noms réservés et racines inconnues', () => {
    expect(
      erreurs(['demande.logement.adresse', 'status', 'inconnu.x', 'demande.formule'], ['demande']),
    ).toEqual([
      'demande.logement.adresse : donnée personnelle ou secret (adresse), interdit dans les logs',
      'status : nom réservé à la plateforme',
      'inconnu.x : racine inconnue (inconnu), attendu : demande',
    ]);
    expect(() => verifier('ContratService.creer', ['email'], ['email'])).toThrow(
      /ContratService.creer :\nemail : donnée personnelle/,
    );
    expect(() => verifier('x', ['demande.formule'], ['demande'])).not.toThrow();
  });

  it.each(['nom', 'nomClient', 'email', 'telephone', 'codePostal', 'dateNaissance', 'motDePasse', 'ip'])(
    '%s est une donnée personnelle',
    (segment) => expect(personnel(segment)).toBe(true),
  );

  it.each(['nombrePieces', 'formule', 'reference', 'description', 'surface'])(
    "%s n'est pas une donnée personnelle",
    (segment) => expect(personnel(segment)).toBe(false),
  );
});
