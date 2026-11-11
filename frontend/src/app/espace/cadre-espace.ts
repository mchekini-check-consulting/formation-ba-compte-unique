import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SuiviSession, formaterRestant } from '../session/suivi-session';

/** Page de l'espace ouverte dans le cadre, marquée dans le menu. */
export type PageDuCadre = 'virement' | 'comptes';

// Cadre commun des pages de l'espace hors accueil (« Faire un virement », « Mes comptes », détail
// d'un compte) : même menu latéral, même barre du haut (minuteur de session, retour à l'espace,
// déconnexion) et même style que l'accueil de l'espace (src/espace.scss). Le contenu de la page
// est projeté dans <main>, sous la barre du haut.
@Component({
  selector: 'app-cadre-espace',
  imports: [RouterLink],
  template: `
    <div class="espace">
      <aside>
        <div class="logo">Hi-Tech<span>Bank</span></div>
        <nav aria-label="Mon espace">
          <a routerLink="/espace"><i></i>Accueil</a>
          <div class="sep">Mes comptes</div>
          <a routerLink="/espace" fragment="compte"><i></i>Compte courant</a>
          <a routerLink="/versement" [attr.aria-current]="courant('virement')"><i></i>Faire un virement</a>
          <a routerLink="/comptes" [attr.aria-current]="courant('comptes')"><i></i>Mes comptes</a>
          <a routerLink="/espace" fragment="epargne"><i></i>Épargne et placements</a>
        </nav>
        <a class="cta" routerLink="/espace" fragment="epargne">Souscrire un produit</a>
      </aside>

      <div class="mnav">
        <div class="logo">Hi-Tech<span>Bank</span></div>
        <nav class="row" aria-label="Mon espace (mobile)">
          <a routerLink="/espace">Accueil</a>
          <a routerLink="/versement" [attr.aria-current]="courant('virement')">Virement</a>
          <a routerLink="/comptes" [attr.aria-current]="courant('comptes')">Mes comptes</a>
        </nav>
      </div>

      <main>
        <div class="top">
          <div class="hello">
            @if (identifiant()) {
              Bonjour, identifiant <b>{{ identifiant() }}</b>
            }
          </div>
          <div class="right">
            @if (restant(); as temps) {
              <span class="minuteur" title="Repère indicatif : la session se ferme après 10 minutes sans activité">
                Fermeture automatique dans <b>{{ temps }}</b>
              </span>
            }
            <a class="btn o sm" routerLink="/espace">← Mon espace</a>
            <button class="btn p sm deconnexion" type="button" (click)="suivi.deconnecter()">Me déconnecter</button>
          </div>
        </div>
        <ng-content />
      </main>
    </div>
  `,
})
export class CadreEspace {
  protected readonly suivi = inject(SuiviSession);

  readonly identifiant = input('');
  readonly page = input.required<PageDuCadre>();

  protected readonly restant = computed(() => {
    const secondes = this.suivi.restant();
    return secondes === null ? null : formaterRestant(secondes);
  });

  /** « page » pour le lien de la page ouverte (aria-current), rien pour les autres. */
  protected courant(page: PageDuCadre): 'page' | null {
    return this.page() === page ? 'page' : null;
  }
}
