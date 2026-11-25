import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from '../api';
import { ResultatFiscal } from '../identification/bouchons-identite';

export type StatutDemande =
  | 'EN_COURS_DE_VERIFICATION'
  | 'OUVERTURE_VALIDEE'
  | 'LIVRET_A_EXISTANT'
  // CU-296 : Livret A détenu ailleurs.
  | 'EN_ATTENTE_CERTIFICAT'
  | 'CERTIFICAT_A_VERIFIER'
  | 'ANNULEE'
  | 'RENONCEE';

/** CU-296 : « Je clôture moi-même mon ancien livret » ou « Je renonce à l'ouverture ». */
export type SuiteLivretAExistant = 'CLOTURE_PAR_CLIENTE' | 'RENONCIATION';

/** CU-296 : détail du Livret A détenu ailleurs (date AAAA-MM-JJ), communiqué seulement si le partage est autorisé. */
export interface DetailLivretA {
  etablissement: string;
  agence: string;
  dateOuverture: string;
}

/** CU-296 : dernier certificat de clôture déposé. decision null = à vérifier par un conseiller. */
export interface CertificatCloture {
  nomFichier: string;
  deposeLe: string;
  decision: 'VALIDE' | 'REFUSE' | null;
  motifRefus: string | null;
}

/** CU-296 : une demande annulée ou renoncée ne bloque plus une nouvelle demande. */
export function demandeTerminee(demande: DemandeLivretA): boolean {
  return demande.statut === 'ANNULEE' || demande.statut === 'RENONCEE';
}

/** Identité à transmettre à l'administration fiscale (date au format AAAA-MM-JJ). */
export interface Transmission {
  nom: string;
  prenoms: string;
  sexe: string;
  dateNaissance: string;
  lieuNaissance: string;
}

// CU-31 : demande d'ouverture de Livret A telle qu'affichée. message : texte à montrer à la cliente.
export interface DemandeLivretA {
  reference: string;
  statut: StatutDemande;
  message: string;
  attestationLe: string;
  partageDetailAutorise: boolean;
  choixPartageLe: string;
  interrogationLe: string | null;
  reponseFiscale: ResultatFiscal | null;
  retard: boolean;
  delai: string;
  transmission: Transmission | null;
  // CU-296 (absents des réponses antérieures : facultatifs).
  detail?: DetailLivretA | null;
  suiteChoisie?: SuiteLivretAExistant | null;
  dateLimiteCertificat?: string | null;
  certificat?: CertificatCloture | null;
  delaiVerification?: string;
}

/** CU-28 : coordonnées modifiables par la cliente (complément d'adresse facultatif). */
export interface Coordonnees {
  voie: string;
  complement: string | null;
  codePostal: string;
  ville: string;
  paysAdresse: string;
  email: string;
  mobile: string;
}

/** CU-28 : identité pré-remplie depuis le dossier client (date au format AAAA-MM-JJ). */
export interface IdentiteLivretA {
  civilite: string;
  nom: string;
  prenoms: string;
  dateNaissance: string;
  coordonnees: Coordonnees;
  residenceFiscale: string;
}

export type StatutControleIdentite = 'EN_COURS' | 'VALIDE' | 'BLOQUE' | 'ATTENTE_RESPONSABLE' | 'EN_ATTENTE';
export type ResultatSanctions = 'AUCUNE_CORRESPONDANCE' | 'CORRESPONDANCE_SANCTIONS' | 'CORRESPONDANCE_PEP' | 'SANS_REPONSE';

/** CU-28 : contrôle d'identité. aControler : identité à envoyer au contrôle sanctions/PPE tant qu'il est en cours. */
export interface ControleIdentite {
  reference: string;
  statut: StatutControleIdentite;
  message: string;
  utilisable: boolean;
  aControler: { nomNaissance: string; prenoms: string; dateNaissance: string; nationalite: string } | null;
}

/** CU-29 : pièce d'identité (le passeport est accepté pour renouveler une pièce expirée). */
export type TypePiece = 'CARTE_NATIONALE_IDENTITE' | 'PASSEPORT' | 'TITRE_SEJOUR';
export type StatutPiece = 'PIECE_A_VERIFIER' | 'SELFIE_A_FAIRE' | 'VALIDE' | 'BLOQUE' | 'EN_ATTENTE';
export type ResultatDocument = 'VERIFIE' | 'A_REPRENDRE' | 'REJETE' | 'ERREUR_TECHNIQUE' | 'SANS_REPONSE';
export type ResultatSelfie = 'CORRESPONDANCE' | 'NON_CORRESPONDANCE' | 'ECHEC_VERIFICATION_PRESENCE' | 'SANS_REPONSE';

/** CU-29 : nouvelle pièce saisie par la cliente (date AAAA-MM-JJ). */
export interface NouvellePiece {
  typePiece: TypePiece;
  numeroPiece: string;
  dateExpiration: string;
}

/** CU-29 : vérification de la nouvelle pièce. aVerifier : identité à envoyer à l'analyse tant qu'elle l'attend. */
export interface VerificationPiece {
  reference: string;
  statut: StatutPiece;
  message: string;
  typePiece: TypePiece;
  dateExpiration: string;
  aVerifier: { nomNaissance: string; prenoms: string; dateNaissance: string; dateExpiration: string } | null;
}

/** CU-29 : nouvelle pièce à fournir (pièce du dossier expirée) et dernière vérification. */
export interface EtapePiece {
  requise: boolean;
  dateExpirationActuelle: string;
  verification: VerificationPiece | null;
}

// CU-31 : routes /livret-a du backend (GET /livret-a/demande, POST /livret-a/demandes,
// POST /livret-a/demandes/{reference}/reponse-fiscale), avec le cookie de session. CU-295 critère 5 :
// POST /test/livret-a/demandes/{reference}/expiration-delai (environnement de test seulement).
@Injectable({ providedIn: 'root' })
export class LivretAService {
  private readonly http = inject(HttpClient);
  private readonly base = apiBase('backend');

  etat(): Observable<{ demande: DemandeLivretA | null }> {
    return this.http.get<{ demande: DemandeLivretA | null }>(`${this.base}/livret-a/demande`, { withCredentials: true });
  }

  demander(attestation: boolean, partageDetailAutorise: boolean): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes`,
      { attestation, partageDetailAutorise },
      { withCredentials: true },
    );
  }

  /** CU-296 : detail du livret existant transmis tel que reçu (le backend ne le garde que si le partage est autorisé). */
  transmettreReponse(
    reference: string,
    interrogationId: string | null,
    resultat: ResultatFiscal,
    detail: DetailLivretA | null = null,
  ): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes/${encodeURIComponent(reference)}/reponse-fiscale`,
      { interrogationId, resultat, detail },
      { withCredentials: true },
    );
  }

  /** CU-296 : choix de la cliente quand un Livret A est détenu ailleurs. */
  choisirSuite(reference: string, choix: SuiteLivretAExistant): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes/${encodeURIComponent(reference)}/suite`,
      { choix },
      { withCredentials: true },
    );
  }

  /** CU-296 : dépôt du certificat de clôture (envoi en multipart, champ « fichier »). */
  deposerCertificat(reference: string, fichier: File): Observable<DemandeLivretA> {
    const formulaire = new FormData();
    formulaire.append('fichier', fichier, fichier.name);
    return this.http.post<DemandeLivretA>(
      `${this.base}/livret-a/demandes/${encodeURIComponent(reference)}/certificats`,
      formulaire,
      { withCredentials: true },
    );
  }

  /** CU-28 : identité pré-remplie et dernier contrôle d'identité. */
  identite(): Observable<{ identite: IdentiteLivretA; controle: ControleIdentite | null }> {
    return this.http.get<{ identite: IdentiteLivretA; controle: ControleIdentite | null }>(`${this.base}/livret-a/identite`, {
      withCredentials: true,
    });
  }

  confirmerIdentite(coordonnees: Coordonnees, residenceFiscaleConfirmee: boolean): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/livret-a/identite`,
      { coordonnees, residenceFiscaleConfirmee },
      { withCredentials: true },
    );
  }

  transmettreControle(reference: string, resultat: ResultatSanctions, tentatives: number): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/livret-a/identite/${encodeURIComponent(reference)}/resultat`,
      { resultat, tentatives },
      { withCredentials: true },
    );
  }

  /** CU-28 critère 9 (environnement de test) : un responsable valide une personne politiquement exposée. */
  simulerValidationResponsable(reference: string): Observable<ControleIdentite> {
    return this.http.post<ControleIdentite>(
      `${this.base}/test/livret-a/identite/${encodeURIComponent(reference)}/validation-responsable`,
      {},
      { withCredentials: true },
    );
  }

  /** CU-29 : pièce du dossier expirée ou non, et dernière vérification de nouvelle pièce. */
  piece(): Observable<EtapePiece> {
    return this.http.get<EtapePiece>(`${this.base}/livret-a/piece`, { withCredentials: true });
  }

  soumettrePiece(piece: NouvellePiece): Observable<VerificationPiece> {
    return this.http.post<VerificationPiece>(`${this.base}/livret-a/piece`, piece, { withCredentials: true });
  }

  transmettreDocument(reference: string, resultat: ResultatDocument, tentatives: number): Observable<VerificationPiece> {
    return this.http.post<VerificationPiece>(
      `${this.base}/livret-a/piece/${encodeURIComponent(reference)}/document`,
      { resultat, tentatives },
      { withCredentials: true },
    );
  }

  transmettreSelfie(
    reference: string,
    resultat: ResultatSelfie,
    score: number | null,
    tentatives: number,
  ): Observable<VerificationPiece> {
    return this.http.post<VerificationPiece>(
      `${this.base}/livret-a/piece/${encodeURIComponent(reference)}/selfie`,
      { resultat, score, tentatives },
      { withCredentials: true },
    );
  }

  simulerExpirationDelai(reference: string): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/test/livret-a/demandes/${encodeURIComponent(reference)}/expiration-delai`,
      {},
      { withCredentials: true },
    );
  }

  /** CU-296 (environnement de test) : un conseiller valide le certificat de clôture. */
  simulerValidationCertificat(reference: string): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/test/livret-a/demandes/${encodeURIComponent(reference)}/certificat/validation`,
      {},
      { withCredentials: true },
    );
  }

  /** CU-296 (environnement de test) : un conseiller refuse le certificat de clôture, avec un motif. */
  simulerRefusCertificat(reference: string, motif: string): Observable<DemandeLivretA> {
    return this.http.post<DemandeLivretA>(
      `${this.base}/test/livret-a/demandes/${encodeURIComponent(reference)}/certificat/refus`,
      { motif },
      { withCredentials: true },
    );
  }
}
