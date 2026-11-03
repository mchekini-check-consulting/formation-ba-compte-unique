import { IDENTITE_EXEMPLE as IDENTITE_VALIDE } from './exemple';
import {
  Identite,
  Reponses,
  TAILLE_MAX_OCTETS,
  lireDate,
  refusFichier,
  sansErreur,
  validerIdentite,
  validerReponses,
  versIso,
} from './regles';


const AUJOURDHUI = new Date(2026, 10, 3);

describe("Règles de l'étape « M'identifier »", () => {
  it('lit les dates JJ/MM/AAAA et refuse les dates impossibles', () => {
    expect(lireDate('14/05/1990')).toEqual(new Date(1990, 4, 14));
    expect(lireDate('31/02/2020')).toBeNull();
    expect(lireDate('1990-05-14')).toBeNull();
    expect(versIso('14/05/1990')).toBe('1990-05-14');
  });

  it('accepte une identité complète', () => {
    expect(validerIdentite(IDENTITE_VALIDE, AUJOURDHUI)).toEqual({});
  });

  it('signale chaque champ obligatoire manquant (CUB-8)', () => {
    const vide = Object.fromEntries(Object.keys(IDENTITE_VALIDE).map((c) => [c, ''])) as unknown as Identite;
    const erreurs = validerIdentite(vide, AUJOURDHUI);
    expect(Object.keys(erreurs).sort()).toEqual(
      [
        'communeNaissance', 'codePostal', 'dateExpiration', 'dateNaissance', 'email', 'mobile',
        'nomNaissance', 'numeroPiece', 'prenoms', 'ville', 'voie',
      ].sort(),
    );
  });

  it("refuse un e-mail, un mobile, un fixe ou un code postal mal formés", () => {
    const erreurs = validerIdentite(
      { ...IDENTITE_VALIDE, email: 'marie@', mobile: '01 23', fixe: '12', codePostal: '6900' },
      AUJOURDHUI,
    );
    expect(erreurs).toEqual({
      email: 'Adresse e-mail mal formée',
      mobile: 'Numéro mobile mal formé',
      fixe: 'Numéro de téléphone mal formé',
      codePostal: 'Code postal à 5 chiffres',
    });
  });

  it("n'impose pas 5 chiffres au code postal hors de France", () => {
    expect(validerIdentite({ ...IDENTITE_VALIDE, paysAdresse: 'Belgique', codePostal: '1000' }, AUJOURDHUI)).toEqual({});
  });

  it('refuse une cliente mineure', () => {
    expect(validerIdentite({ ...IDENTITE_VALIDE, dateNaissance: '04/11/2008' }, AUJOURDHUI).dateNaissance).toBe(
      'Vous devez avoir au moins 18 ans',
    );
    expect(validerIdentite({ ...IDENTITE_VALIDE, dateNaissance: '03/11/2008' }, AUJOURDHUI)).toEqual({});
    expect(validerIdentite({ ...IDENTITE_VALIDE, dateNaissance: '1990' }, AUJOURDHUI).dateNaissance).toBe(
      'Format attendu : JJ/MM/AAAA',
    );
  });

  it("refuse une date d'expiration dépassée, comme une erreur de saisie", () => {
    expect(validerIdentite({ ...IDENTITE_VALIDE, dateExpiration: '03/11/2026' }, AUJOURDHUI).dateExpiration).toBe(
      "Date d'expiration dépassée : corrigez la date saisie",
    );
    expect(validerIdentite({ ...IDENTITE_VALIDE, dateExpiration: '2031' }, AUJOURDHUI).dateExpiration).toBe(
      'Format attendu : JJ/MM/AAAA',
    );
  });
});

describe('Règles du fichier de la pièce (CUB-9)', () => {
  it('accepte PNG, JPEG, BMP, GIF ou PDF de 2 Mo maximum', () => {
    expect(refusFichier({ name: 'recto.jpg', type: 'image/jpeg', size: TAILLE_MAX_OCTETS })).toBeNull();
    expect(refusFichier({ name: 'piece.PDF', type: '', size: 1000 })).toBeNull();
  });

  it('refuse un autre format ou un fichier trop lourd, avec le motif', () => {
    expect(refusFichier({ name: 'piece.docx', type: 'application/msword', size: 1000 })).toBe(
      'Format non accepté : PNG, JPEG, BMP, GIF ou PDF uniquement',
    );
    expect(refusFichier({ name: 'piece.png', type: 'image/png', size: 3.4 * 1024 * 1024 })).toBe(
      'Fichier trop lourd (3,4 Mo) : 2 Mo maximum',
    );
  });
});

describe('Règles des questions obligatoires (CUB-12)', () => {
  const complet: Reponses = {
    origineFonds: 'Salaire',
    origineAutre: '',
    objetRelation: 'Épargne',
    pep: 'Non',
    residenceEtranger: 'Non',
    residences: [{ pays: '', nif: '' }],
    consentementIdentite: true,
    consentementSituation: false,
  };

  it('accepte des réponses complètes, sans le consentement facultatif', () => {
    expect(sansErreur(validerReponses(complet))).toBe(true);
  });

  it('signale chaque question sans réponse et le consentement obligatoire', () => {
    const erreurs = validerReponses({
      ...complet,
      origineFonds: '',
      objetRelation: '',
      pep: '',
      residenceEtranger: '',
      consentementIdentite: false,
    });
    expect(Object.keys(erreurs).sort()).toEqual(
      ['consentementIdentite', 'objetRelation', 'origineFonds', 'pep', 'residenceEtranger'].sort(),
    );
  });

  it('demande de préciser « Autre »', () => {
    expect(validerReponses({ ...complet, origineFonds: 'Autre' }).origineFonds).toBe("Précisez l'origine des fonds");
    expect(validerReponses({ ...complet, origineFonds: 'Autre', origineAutre: 'Gain au jeu' })).toEqual({});
  });

  it('demande chaque pays et numéro fiscal si résidence hors de France', () => {
    const erreurs = validerReponses({
      ...complet,
      residenceEtranger: 'Oui',
      residences: [{ pays: 'Belgique', nif: '' }, { pays: '', nif: '123' }],
    });
    expect(erreurs.residences).toEqual([
      { nif: 'Ce champ est obligatoire' },
      { pays: 'Indiquez chaque pays de résidence fiscale' },
    ]);
    expect(
      validerReponses({ ...complet, residenceEtranger: 'Oui', residences: [{ pays: 'Belgique', nif: 'BE1' }] }),
    ).toEqual({});
  });
});
