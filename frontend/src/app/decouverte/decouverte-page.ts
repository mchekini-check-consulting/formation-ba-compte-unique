import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { CodeDocument, DecouverteService, EtatDecouverte, Produit } from './decouverte';

type Etat = 'chargement' | 'affiche' | 'indisponible';

interface Presentation {
  nom: string;
  accroche: string;
  regles: { titre: string; texte: string }[];
}

const PRESENTATIONS: Record<Produit, Presentation> = {
  PER: {
    nom: 'le PER',
    accroche: 'Le plan d’épargne retraite prépare vos revenus de demain, avec un avantage fiscal dès aujourd’hui.',
    regles: [
      {
        titre: 'Blocage jusqu’à la retraite',
        texte:
          'L’épargne est disponible au départ à la retraite, en capital ou en rente. Déblocage anticipé possible pour l’achat de la résidence principale ou en cas d’accident de la vie.',
      },
      {
        titre: 'Fiscalité',
        texte:
          'Les versements volontaires sont déductibles de votre revenu imposable, dans la limite de votre plafond. Ils sont imposés à la sortie.',
      },
    ],
  },
  AV: {
    nom: 'l’assurance vie',
    accroche: 'L’assurance vie fait fructifier votre épargne, disponible à tout moment, et prépare sa transmission.',
    regles: [
      {
        titre: 'Rachat',
        texte: 'Votre épargne reste disponible : vous pouvez faire un rachat partiel ou total à tout moment.',
      },
      {
        titre: 'Fiscalité',
        texte: 'Seuls les gains sont imposés lors d’un rachat, avec un abattement annuel après 8 ans de détention.',
      },
    ],
  },
};

// CU-311 (PER-02) et CU-331 (AV-02) : présentation du PER ou de l'assurance vie avant de s'engager (?produit=PER|AV).
// Chaque document s'ouvre dans la page (ouverture enregistrée par le backend) ; sa case « J'ai pris connaissance »
// reste désactivée tant qu'il n'est pas ouvert, et « Continuer » tant que les deux cases ne sont pas cochées.
@Component({
  selector: 'app-decouverte-page',
  imports: [RouterLink],
  template: `
    <main class="decouverte">
      <a routerLink="/espace" fragment="epargne">← Mon espace</a>
      <h1>Découvrir {{ presentation.nom }}</h1>
      <p class="lead">{{ presentation.accroche }}</p>

      <div class="regles">
        @for (r of presentation.regles; track r.titre) {
          <section class="carte">
            <h2>{{ r.titre }}</h2>
            <p>{{ r.texte }}</p>
          </section>
        }
      </div>

      <h2>Documents à lire avant de souscrire</h2>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="erreur" role="alert">Les documents sont momentanément indisponibles. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          @for (d of donnees()!.documents; track d.code) {
            <section class="carte document" [attr.data-test]="'document-' + d.code">
              <div class="entete">
                <h3>{{ d.titre }}</h3>
                <span class="version">Version {{ d.version }}</span>
              </div>
              @if (ouverts().includes(d.code)) {
                <p class="contenu" data-test="contenu">{{ d.contenu }}</p>
              } @else {
                <button class="btn o" type="button" data-test="ouvrir" [disabled]="envoi()" (click)="ouvrir(d.code)">
                  Ouvrir le document
                </button>
              }
              <label class="case" [class.inactive]="!d.ouvert">
                <input
                  type="checkbox"
                  data-test="case"
                  [checked]="d.lu"
                  [disabled]="!d.ouvert || d.lu || envoi()"
                  (change)="cocher(d.code, $event)"
                />
                J'ai pris connaissance {{ d.code === 'DIC' ? 'du document d’informations clés' : 'de la fiche produit' }}
              </label>
            </section>
          }
          @if (erreur()) {
            <p class="erreur" role="alert">{{ erreur() }}</p>
          }
          <button class="btn p" type="button" data-test="continuer" [disabled]="!donnees()!.peutContinuer" (click)="continuer()">
            Continuer
          </button>
        }
      }
    </main>
  `,
  styles: `
    .decouverte { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1, h2, h3 { color: #0b2545; }
    h1 { margin: 24px 0 8px; }
    h3 { margin: 0; font-size: 17px; }
    .lead { line-height: 1.6; color: #5d6887; }
    .regles { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .carte { border: 1px solid #d9dee6; border-radius: 14px; padding: 16px; background: #fff; line-height: 1.6; }
    .regles h2 { margin: 0 0 4px; font-size: 18px; }
    .document { margin-bottom: 16px; display: grid; gap: 12px; }
    .entete { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; align-items: baseline; }
    .version { font-size: 12px; color: #5d6887; }
    .contenu { margin: 0; padding: 12px; border-radius: 10px; background: #f4f6fa; }
    .case { display: flex; gap: 8px; align-items: center; font-weight: 700; }
    .case.inactive { color: #98a2b3; }
    .btn { justify-self: start; padding: 10px 18px; border-radius: 999px; font-weight: 700; cursor: pointer; font: inherit; }
    .btn.o { border: 1px solid #0b2545; background: #fff; color: #0b2545; }
    .btn.p { border: 0; background: #0b2545; color: #fff; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .erreur { color: #b42318; font-weight: 700; }
  `,
})
export class DecouvertePage {
  private readonly service = inject(DecouverteService);
  private readonly router = inject(Router);
  protected readonly produit: Produit =
    inject(ActivatedRoute).snapshot.queryParamMap.get('produit') === 'PER' ? 'PER' : 'AV';
  protected readonly presentation = PRESENTATIONS[this.produit];
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<EtatDecouverte | null>(null);
  /** Documents dont le contenu est affiché dans cette visite (chaque ouverture est enregistrée). */
  protected readonly ouverts = signal<CodeDocument[]>([]);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal('');

  constructor() {
    this.service.etat(this.produit).subscribe({
      next: (donnees) => {
        this.donnees.set(donnees);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }

  protected ouvrir(document: CodeDocument): void {
    this.envoyer(this.service.ouvrir(this.produit, document), () => this.ouverts.update((o) => [...o, document]));
  }

  protected cocher(document: CodeDocument, event: Event): void {
    const caseACocher = event.target as HTMLInputElement;
    // Case non enregistrée : elle redevient décochée.
    this.envoyer(this.service.cocher(this.produit, document), undefined, () => (caseACocher.checked = false));
  }

  // CU-314 / CU-338 : étape suivante, le choix du mode de gestion (la souscription PER-04 / AV-01 viendra ensuite).
  protected continuer(): void {
    void this.router.navigate(['/gestion'], { queryParams: { produit: this.produit } });
  }

  private envoyer(
    appel: Observable<EtatDecouverte>,
    ensuite: () => void = () => undefined,
    siEchec: () => void = () => undefined,
  ): void {
    this.envoi.set(true);
    this.erreur.set('');
    appel.subscribe({
      next: (donnees) => {
        this.donnees.set(donnees);
        this.envoi.set(false);
        ensuite();
      },
      error: () => {
        this.envoi.set(false);
        this.erreur.set('Votre action n’a pas pu être enregistrée. Réessayez.');
        siEchec();
      },
    });
  }
}
