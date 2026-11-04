import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { map, switchMap } from 'rxjs';
import { BouchonsIdentiteService, CasDeTest } from './bouchons-identite';
import { Controles, DossierService, controlesDe } from './dossier';
import {
  ChampIdentite,
  Erreurs,
  Identite,
  MESSAGE_ANNULATION,
  NATIONALITES,
  PAYS_ADRESSE,
  PAYS_NAISSANCE,
  TYPES_PIECE,
  sansErreur,
  validerIdentite,
  versIso,
} from './regles';

const VIDE: Identite = {
  civilite: 'Mme',
  nomNaissance: '',
  nomUsage: '',
  prenoms: '',
  dateNaissance: '',
  communeNaissance: '',
  paysNaissance: 'FRANCE',
  nationalite: 'FRANCE',
  voie: '',
  complement: '',
  codePostal: '',
  ville: '',
  paysAdresse: 'France',
  email: '',
  mobile: '',
  fixe: '',
  typePiece: 'CARTE_NATIONALE_IDENTITE',
  numeroPiece: '',
  dateExpiration: '',
};

/** Annulation par le contrôle sanctions ou PEP : message générique et résultat de chaque contrôle. */
export interface AnnulationControles {
  message: string;
  controles: Controles;
}

/** Cas de test du bouchon US-B7 (maquette, écran 5). */
export const CAS_DE_TEST: { code: CasDeTest; libelle: string }[] = [
  { code: 'CONFORME', libelle: 'Test Conforme' },
  { code: 'SANCTIONNE', libelle: 'Test Sanctionné' },
  { code: 'PEP', libelle: 'Test PEP' },
  { code: 'INDISPONIBLE', libelle: 'Mode indisponible' },
];

// Écran « Mon identité » (CUB-8, maquette écrans 1, 4 et 5) : contrôles de saisie,
// enregistrement du dossier (backend), contrôle sanctions et PEP (bouchon US-B7), puis
// enregistrement du résultat des contrôles et du statut du dossier (backend).
@Component({
  selector: 'app-etape-identite',
  templateUrl: './etape-identite.html',
})
export class EtapeIdentite {
  private readonly bouchons = inject(BouchonsIdentiteService);
  private readonly dossiers = inject(DossierService);

  readonly dossierId = input.required<string>();
  /** Saisie déjà validée, reprise quand la cliente revient à cette étape. */
  readonly initiale = input<Identite | null>(null);
  readonly valide = output<Identite>();
  readonly annule = output<AnnulationControles>();
  readonly retour = output<void>();

  protected readonly paysNaissance = PAYS_NAISSANCE;
  protected readonly nationalites = NATIONALITES;
  protected readonly typesPiece = TYPES_PIECE;
  protected readonly paysAdresse = PAYS_ADRESSE;
  protected readonly casDeTest = CAS_DE_TEST;

  protected readonly identite = linkedSignal<Identite>(() => ({ ...(this.initiale() ?? VIDE) }));
  protected readonly erreurs = signal<Erreurs>({});
  protected readonly resultat = signal<string | null>(null);
  protected readonly enCours = signal(false);
  /** Écran 4 : le contrôle sanctions/PEP ou l'enregistrement n'a pas répondu. */
  protected readonly indisponible = signal(false);
  protected readonly cas = signal<CasDeTest>('CONFORME');

  protected maj(champ: ChampIdentite, event: Event): void {
    const valeur = (event.target as HTMLInputElement | HTMLSelectElement).value;
    this.identite.update((i) => ({ ...i, [champ]: valeur }));
  }

  protected continuer(event: Event): void {
    event.preventDefault();
    const identite = this.identite();
    const erreurs = validerIdentite(identite);
    this.erreurs.set(erreurs);
    if (!sansErreur(erreurs)) {
      this.resultat.set('Corrigez les champs signalés pour continuer.');
      const premier = Object.keys(erreurs)[0];
      document.getElementById(`id-${premier}`)?.focus();
      return;
    }
    this.resultat.set(null);
    this.enCours.set(true);
    const reference = this.dossierId();
    this.dossiers
      .enregistrerIdentite(reference, identite)
      .pipe(
        switchMap(() =>
          this.bouchons.controlerSanctions({
            nomNaissance: identite.nomNaissance.trim(),
            prenoms: identite.prenoms.trim(),
            dateNaissance: versIso(identite.dateNaissance),
            nationalite: identite.nationalite,
            casDeTest: this.cas(),
          }),
        ),
        switchMap((reponse) => {
          const controles = controlesDe(reponse.resultat);
          return this.dossiers
            .enregistrerControles(reference, controles)
            .pipe(map(() => ({ reponse, controles })));
        }),
      )
      .subscribe({
        next: ({ reponse, controles }) => {
          this.enCours.set(false);
          if (reponse.suite === 'ANNULER') {
            this.annule.emit({ message: reponse.message ?? MESSAGE_ANNULATION, controles });
          } else {
            this.valide.emit(identite);
          }
        },
        error: () => {
          // CUB-8 : pas de relance automatique, le dossier reste « En cours de vérification ».
          this.enCours.set(false);
          this.indisponible.set(true);
        },
      });
  }
}
