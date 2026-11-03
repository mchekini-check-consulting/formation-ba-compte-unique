import { Component, ElementRef, OnDestroy, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { BouchonsIdentiteService } from './bouchons-identite';
import { MESSAGE_ANNULATION, MESSAGE_INDISPONIBLE } from './regles';

/** Selfies de test du bouchon Stripe Identity (bouchons-identite/README.md). */
export const SCENARIOS_SELFIE = [
  { code: 'SELFIE_CONFORME', libelle: 'Selfie conforme (score 96 %)' },
  { code: 'SELFIE_DIFFERENT', libelle: 'Selfie différent (score 42 %)' },
  { code: 'SCORE_89', libelle: 'Score 89 % (juste sous le seuil de 90 %)' },
  { code: 'PHOTO_ECRAN', libelle: 'Photo imprimée ou vidéo présentée à la caméra' },
  { code: 'SERVICE_INDISPONIBLE', libelle: 'Service indisponible' },
];

type Etat = 'guidage' | 'camera' | 'refusee';

// Écran « Vérification de présence » (CUB-10, maquette écrans 2 et 3) : la caméra est
// demandée au navigateur ; si elle est refusée, rien n'est transmis et la vérification peut
// être relancée. Le bouchon ne reçoit pas d'image : le scénario de test la remplace.
@Component({
  selector: 'app-etape-selfie',
  templateUrl: './etape-selfie.html',
})
export class EtapeSelfie implements OnDestroy {
  private readonly bouchons = inject(BouchonsIdentiteService);

  readonly dossierId = input.required<string>();
  readonly valide = output<void>();
  readonly annule = output<string>();
  readonly retour = output<void>();

  protected readonly scenarios = SCENARIOS_SELFIE;
  protected readonly scenario = signal(SCENARIOS_SELFIE[0].code);
  protected readonly etat = signal<Etat>('guidage');
  protected readonly message = signal<string | null>(null);
  protected readonly enCours = signal(false);

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private flux: MediaStream | null = null;

  constructor() {
    effect(() => {
      const video = this.video()?.nativeElement;
      if (video && this.flux) {
        video.srcObject = this.flux;
      }
    });
  }

  protected async demarrerCamera(): Promise<void> {
    this.message.set(null);
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('caméra non disponible');
      }
      this.flux = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } });
      this.etat.set('camera');
    } catch {
      // CUB-10 : ni annulation ni échec de contrôle, le dossier reste « En cours de vérification ».
      this.etat.set('refusee');
    }
  }

  protected changerScenario(event: Event): void {
    this.scenario.set((event.target as HTMLSelectElement).value);
  }

  protected verifier(): void {
    this.enCours.set(true);
    this.message.set(null);
    this.bouchons.verifierSelfie(this.dossierId(), this.scenario()).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        if (reponse.suite === 'POURSUIVRE') {
          this.arreterCamera();
          this.valide.emit();
        } else if (reponse.suite === 'ANNULER') {
          this.arreterCamera();
          this.annule.emit(reponse.message ?? MESSAGE_ANNULATION);
        } else {
          this.message.set(reponse.message ?? MESSAGE_INDISPONIBLE);
        }
      },
      error: () => {
        this.enCours.set(false);
        this.message.set(MESSAGE_INDISPONIBLE);
      },
    });
  }

  protected revenir(): void {
    this.arreterCamera();
    this.retour.emit();
  }

  ngOnDestroy(): void {
    this.arreterCamera();
  }

  private arreterCamera(): void {
    this.flux?.getTracks().forEach((piste) => piste.stop());
    this.flux = null;
  }
}
