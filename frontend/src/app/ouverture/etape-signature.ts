import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { switchMap } from 'rxjs';
import { BouchonsIdentiteService } from '../identification/bouchons-identite';
import { MESSAGE_INDISPONIBLE } from '../identification/regles';
import { OuvertureService, SignatureReponse, memoriserDossierEnSignature } from './ouverture';

/** Codes de test du bouchon Docusign (CU-89), rappelés dans le panneau du prototype. */
export const CODES_DE_TEST = [
  { code: '111111', libelle: 'signature valide' },
  { code: '000000', libelle: 'code faux' },
  { code: '999999', libelle: 'code expiré' },
];

export const MESSAGE_SIGNATURE_ANNULEE = 'La signature de votre convention est annulée';

// Étape 5 « Signer ma convention de compte » (CU-12). Le code SMS est le second facteur de la
// signature : il est valable 5 minutes et à usage unique. L'écran appelle Docusign (bouchon
// CU-89) puis transmet le résultat au backend, qui applique les règles et le statut du dossier.
// La référence du dossier est gardée par le navigateur : après une fermeture, la signature
// reprend au même endroit (landing.ts).
@Component({
  selector: 'app-etape-signature',
  templateUrl: './etape-signature.html',
})
export class EtapeSignature implements OnInit {
  private readonly bouchons = inject(BouchonsIdentiteService);
  private readonly api = inject(OuvertureService);

  readonly dossierId = input.required<string>();
  readonly valide = output<void>();
  readonly annule = output<string>();
  readonly retour = output<void>();

  protected readonly codesDeTest = CODES_DE_TEST;
  protected readonly signature = signal<SignatureReponse | null>(null);
  protected readonly code = signal('');
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly confirmationAnnulation = signal(false);

  ngOnInit(): void {
    this.api.signature(this.dossierId()).subscribe({
      next: (signature) => this.signature.set(signature),
      error: () => this.message.set(MESSAGE_INDISPONIBLE),
    });
  }

  protected saisirCode(event: Event): void {
    this.code.set((event.target as HTMLInputElement).value.trim());
  }

  /** Premier code, ou nouveau code après expiration : nouvelle enveloppe Docusign. */
  protected recevoirCode(): void {
    this.demarrer();
    this.bouchons
      .envoyerEnveloppe(this.dossierId())
      .pipe(switchMap((enveloppe) => this.api.enveloppeEnvoyee(this.dossierId(), enveloppe.enveloppeId)))
      .subscribe({
        next: (signature) => {
          memoriserDossierEnSignature(this.dossierId());
          this.terminer(signature, null);
        },
        error: () => this.echec(),
      });
  }

  protected signer(): void {
    const enveloppe = this.signature()?.enveloppeId;
    if (!/^\d{6}$/.test(this.code())) {
      this.message.set('Saisissez les 6 chiffres du code reçu par SMS.');
      return;
    }
    if (!enveloppe) {
      return;
    }
    this.demarrer();
    this.bouchons
      .saisirCode(enveloppe, this.code())
      .pipe(
        switchMap((reponse) =>
          this.api.resultatSignature(
            this.dossierId(),
            reponse.resultat === 'ENVELOPPE_ENVOYEE' ? 'CODE_FAUX' : reponse.resultat,
          ),
        ),
      )
      .subscribe({ next: (signature) => this.suite(signature), error: () => this.echec() });
  }

  protected demanderAnnulation(): void {
    this.confirmationAnnulation.set(true);
  }

  protected garderLaSignature(): void {
    this.confirmationAnnulation.set(false);
  }

  protected confirmerAnnulation(): void {
    const enveloppe = this.signature()?.enveloppeId;
    if (!enveloppe) {
      return;
    }
    this.demarrer();
    this.bouchons
      .annulerSignature(enveloppe)
      .pipe(switchMap(() => this.api.resultatSignature(this.dossierId(), 'ANNULATION_DEMANDEE')))
      .subscribe({ next: (signature) => this.suite(signature), error: () => this.echec() });
  }

  private suite(signature: SignatureReponse): void {
    this.code.set('');
    this.confirmationAnnulation.set(false);
    switch (signature.etat) {
      case 'SIGNEE':
        memoriserDossierEnSignature(null);
        this.terminer(signature, null);
        this.valide.emit();
        break;
      case 'ANNULEE':
        memoriserDossierEnSignature(null);
        this.terminer(signature, null);
        this.annule.emit(`${MESSAGE_SIGNATURE_ANNULEE} : ${signature.motifAnnulation ?? 'Annulation demandée'}.`);
        break;
      case 'EXPIREE':
        this.terminer(signature, 'Code expiré. Demandez un nouveau code.');
        break;
      default:
        this.terminer(signature, signature.codesFaux > 0 ? messageCodeFaux(signature.essaisRestants) : null);
    }
  }

  private demarrer(): void {
    this.enCours.set(true);
    this.message.set(null);
  }

  private terminer(signature: SignatureReponse, message: string | null): void {
    this.enCours.set(false);
    this.signature.set(signature);
    this.message.set(message);
  }

  private echec(): void {
    this.enCours.set(false);
    this.message.set(MESSAGE_INDISPONIBLE);
  }
}

export function messageCodeFaux(essaisRestants: number): string {
  if (essaisRestants <= 0) {
    return 'Code incorrect. Une nouvelle tentative annulera la signature.';
  }
  return `Code incorrect. Il vous reste ${essaisRestants} essai${essaisRestants > 1 ? 's' : ''}.`;
}
