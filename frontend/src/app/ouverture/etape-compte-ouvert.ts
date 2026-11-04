import { Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { MESSAGE_INDISPONIBLE } from '../identification/regles';
import { Canal, OuvertureReponse, OuvertureService } from './ouverture';

/** Le bouton de renvoi reste désactivé 60 secondes après un clic (CU-13). */
export const DELAI_RENVOI_MS = 60_000;
/** Tant que la création est en cours (ou relancée), l'écran se met à jour seul. */
export const INTERVALLE_SUIVI_MS = 3_000;

// Étape 6 « Votre compte courant est ouvert » (CU-13) : compte créé par le système dès la
// signature, identifiant envoyé par e-mail, lien d'activation envoyé par SMS. En cas d'échec
// d'un envoi, la cliente peut le redemander.
@Component({
  selector: 'app-etape-compte-ouvert',
  templateUrl: './etape-compte-ouvert.html',
})
export class EtapeCompteOuvert implements OnInit, OnDestroy {
  private readonly api = inject(OuvertureService);

  readonly dossierId = input.required<string>();
  readonly valide = output<void>();

  protected readonly ouverture = signal<OuvertureReponse | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly renvoiBloque = signal<Record<Canal, boolean>>({ EMAIL: false, SMS: false });
  protected readonly confirmationRenvoi = signal<string | null>(null);

  private suivi: ReturnType<typeof setInterval> | undefined;
  private readonly minuteries: ReturnType<typeof setTimeout>[] = [];

  ngOnInit(): void {
    this.charger();
  }

  ngOnDestroy(): void {
    clearInterval(this.suivi);
    this.minuteries.forEach(clearTimeout);
  }

  protected renvoyer(canal: Canal): void {
    this.renvoiBloque.update((bloque) => ({ ...bloque, [canal]: true }));
    this.minuteries.push(
      setTimeout(() => this.renvoiBloque.update((bloque) => ({ ...bloque, [canal]: false })), DELAI_RENVOI_MS),
    );
    this.confirmationRenvoi.set(null);
    this.api.renvoyer(this.dossierId(), canal).subscribe({
      next: (ouverture) => {
        this.ouverture.set(ouverture);
        const envoi = canal === 'EMAIL' ? ouverture.envoiEmail : ouverture.envoiSms;
        this.confirmationRenvoi.set(
          envoi === 'ENVOYE'
            ? canal === 'EMAIL'
              ? 'Votre identifiant vous a été renvoyé par e-mail.'
              : 'Votre lien d\'activation vous a été renvoyé par SMS.'
            : 'Le renvoi a échoué. Réessayez dans une minute.',
        );
      },
      error: () => this.confirmationRenvoi.set('Le renvoi a échoué. Réessayez dans une minute.'),
    });
  }

  protected enEchec(resultat: string | null): boolean {
    return resultat === 'EN_ECHEC' || resultat === 'ECHEC';
  }

  private charger(): void {
    this.api.ouverture(this.dossierId()).subscribe({
      next: (ouverture) => {
        this.ouverture.set(ouverture);
        this.message.set(null);
        const enAttente =
          ouverture.statutDossier === 'CONVENTION_SIGNEE' ||
          (ouverture.statutDossier === 'COMPTE_OUVERT' && (!ouverture.envoiEmail || !ouverture.envoiSms));
        if (enAttente && !this.suivi) {
          this.suivi = setInterval(() => this.charger(), INTERVALLE_SUIVI_MS);
        } else if (!enAttente) {
          clearInterval(this.suivi);
          this.suivi = undefined;
        }
      },
      error: () => this.message.set(MESSAGE_INDISPONIBLE),
    });
  }
}
