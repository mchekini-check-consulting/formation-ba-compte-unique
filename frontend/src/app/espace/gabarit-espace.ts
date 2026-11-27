import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, skip } from 'rxjs';
import { ProfilService } from '../profil/profil';
import { SuiviSession, formaterRestant } from '../session/suivi-session';

/** Une entrée du menu : lien vers une page ou vers une rubrique de l'accueil (fragment). */
export interface EntreeMenu {
  libelle: string;
  lien: string;
  fragment?: string;
  queryParams?: Record<string, string>;
}

/** Une rubrique du menu : un lien seul, ou un titre suivi de ses entrées. */
export interface RubriqueMenu {
  titre: string;
  lien?: EntreeMenu;
  entrees?: EntreeMenu[];
}

/** Lot 2 (PO, 27/11/2026) : menu unique, qui reprend toutes les entrées des deux anciens menus de l'espace. */
export const MENU: RubriqueMenu[] = [
  { titre: 'Accueil', lien: { libelle: 'Accueil', lien: '/espace' } },
  {
    titre: 'Mes comptes et contrats',
    entrees: [
      { libelle: 'Compte courant', lien: '/espace', fragment: 'compte' },
      { libelle: 'Mes comptes', lien: '/comptes' },
      { libelle: 'Mon patrimoine', lien: '/espace', fragment: 'patrimoine' },
      { libelle: 'Comptes d’autres banques (bientôt)', lien: '/espace', fragment: 'bientot' },
    ],
  },
  {
    titre: 'Virements et bénéficiaires',
    entrees: [
      { libelle: 'Faire un virement', lien: '/versement' },
      { libelle: 'Mes bénéficiaires', lien: '/beneficiaires' },
    ],
  },
  {
    titre: 'Épargne et placements',
    entrees: [
      { libelle: 'Produits à souscrire', lien: '/espace', fragment: 'epargne' },
      { libelle: 'Ouvrir mon Livret A', lien: '/livret-a' },
      { libelle: 'Supports disponibles', lien: '/supports' },
      { libelle: 'Gestion de mon PER', lien: '/gestion', queryParams: { produit: 'PER' } },
      { libelle: 'Gestion de mon assurance vie', lien: '/gestion', queryParams: { produit: 'AV' } },
      { libelle: 'Optimisation et conseil', lien: '/espace', fragment: 'optimisation' },
    ],
  },
  { titre: 'Crédits', lien: { libelle: 'Crédits', lien: '/espace', fragment: 'credits' } },
  { titre: 'Simulateurs', lien: { libelle: 'Simulateurs', lien: '/espace', fragment: 'simulateurs' } },
  { titre: 'Documents', lien: { libelle: 'Mes documents', lien: '/espace', fragment: 'documents' } },
  {
    titre: 'Mon profil et sécurité',
    entrees: [
      { libelle: 'Mon profil investisseur', lien: '/profil' },
      { libelle: 'Mes informations et sécurité', lien: '/espace', fragment: 'infos' },
    ],
  },
];

/** Données de route d'une page de l'espace : fil d'Ariane et cible du bouton retour. */
export interface Ariane {
  /** Rubrique du menu, puis éventuelle page parente (lien), puis la page. */
  rubrique: string;
  parent?: EntreeMenu;
  page: string;
  retour: EntreeMenu;
  /** Le paramètre produit (PER, AV) de la page est gardé par le retour. */
  garderProduit?: boolean;
}

const DUREE_ANNONCE_MS = 3200;

// Lot 2 : gabarit unique de toutes les pages connectées. En-tête (logo, « Bonjour + prénom » ou l'identifiant
// à défaut (D8), minuterie de session, Messages, Me déconnecter), même menu latéral, fil d'Ariane et bouton retour,
// pied de page. La session est suivie ici (CU-17) : chaque changement de page la signale et relance le suivi ;
// rien de l'espace ne s'affiche avant la réponse du serveur (CU-18).
@Component({
  selector: 'app-gabarit-espace',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="evitement" href="#contenu" (click)="allerAuContenu($event)">Aller au contenu</a>
    <div class="espace">
      <aside>
        <a class="logo" routerLink="/espace">Hi-Tech<span>Bank</span></a>
        <nav aria-label="Menu de mon espace">
          @for (r of menu; track r.titre) {
            @if (r.lien; as e) {
              <a [routerLink]="e.lien" [fragment]="e.fragment" [queryParams]="e.queryParams"
                 routerLinkActive="actif" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="exact"><i></i>{{ e.libelle }}</a>
            } @else {
              <div class="sep" [id]="'menu-' + $index">{{ r.titre }}</div>
              <ul [attr.aria-labelledby]="'menu-' + $index">
                @for (e of r.entrees; track e.libelle) {
                  <li>
                    <a [routerLink]="e.lien" [fragment]="e.fragment" [queryParams]="e.queryParams"
                       routerLinkActive="actif" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="exact"><i></i>{{ e.libelle }}</a>
                  </li>
                }
              </ul>
            }
          }
        </nav>
        <a class="cta" routerLink="/espace" fragment="epargne">Souscrire un produit</a>
      </aside>

      <main id="contenu" tabindex="-1">
        <div class="top">
          <div class="hello">
            @if (prenom()) {
              Bonjour <b>{{ prenom() }}</b>
            } @else if (identifiant()) {
              Bonjour, identifiant <b>{{ identifiant() }}</b>
            }
          </div>
          <div class="right">
            @if (restant(); as temps) {
              <span class="minuteur" title="Repère indicatif : la session se ferme après 10 minutes sans activité">
                Fermeture automatique dans <b>{{ temps }}</b>
              </span>
            }
            <button class="btn o sm" type="button" (click)="annoncer('Messagerie : bientôt disponible.')">Messages</button>
            <button class="btn p sm deconnexion" type="button" (click)="suivi.deconnecter()">Me déconnecter</button>
          </div>
        </div>

        @if (ariane(); as a) {
          <div class="ariane-barre">
            <a class="retour" [routerLink]="a.retour.lien" [fragment]="a.retour.fragment" [queryParams]="retourParams()">← Retour</a>
            <nav aria-label="Fil d’Ariane" class="ariane">
              <ol>
                <li><a routerLink="/espace">Accueil</a></li>
                <li>{{ a.rubrique }}</li>
                @if (a.parent; as p) {
                  <li><a [routerLink]="p.lien" [queryParams]="retourParams()">{{ p.libelle }}</a></li>
                }
                <li aria-current="page">{{ a.page }}</li>
              </ol>
            </nav>
          </div>
        }

        @if (identifiant()) {
          <router-outlet />
        }

        <footer>
          <a routerLink="/mentions-legales">Mentions légales</a>
          <a routerLink="/protection-des-donnees">Politique de protection des données</a>
          <a routerLink="/accessibilite">Accessibilité</a>
        </footer>
      </main>

      @if (annonce()) {
        <div class="toast" role="status">{{ annonce() }}</div>
      }
    </div>
  `,
})
export class GabaritEspace {
  protected readonly suivi = inject(SuiviSession);
  private readonly profils = inject(ProfilService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private minuteurAnnonce: ReturnType<typeof setTimeout> | null = null;

  protected readonly menu = MENU;
  protected readonly exact = { paths: 'exact', fragment: 'exact', queryParams: 'subset', matrixParams: 'ignored' } as const;
  protected readonly identifiant = signal('');
  protected readonly prenom = signal('');
  protected readonly annonce = signal('');
  protected readonly ariane = signal<Ariane | null>(null);
  private readonly produit = signal<string | null>(null);
  protected readonly retourParams = computed(() => {
    const produit = this.produit();
    return this.ariane()?.garderProduit && produit ? { produit } : this.ariane()?.retour.queryParams;
  });
  protected readonly restant = computed(() => {
    const secondes = this.suivi.restant();
    return secondes === null ? null : formaterRestant(secondes);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.suivi.arreter();
      if (this.minuteurAnnonce) clearTimeout(this.minuteurAnnonce);
    });
    this.lirePage();
    this.ouvrir(true);
    // La première fin de navigation est celle qui a ouvert le gabarit (déjà signalée ci-dessus).
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), skip(1)).subscribe(() => {
      this.lirePage();
      // CU-17 critère 4 : un changement de page est une action ; le suivi repart si la page quittée l'a arrêté.
      this.ouvrir(false);
    });
  }

  /** Lien d'évitement : le clavier passe directement au contenu de la page (RGAA). */
  protected allerAuContenu(event: Event): void {
    event.preventDefault();
    document.getElementById('contenu')?.focus();
  }

  /** Messagerie pas encore développée : annonce (le lot 3 la passe en « Bientôt »). */
  protected annoncer(texte: string): void {
    this.annonce.set(texte);
    if (this.minuteurAnnonce) clearTimeout(this.minuteurAnnonce);
    this.minuteurAnnonce = setTimeout(() => this.annonce.set(''), DUREE_ANNONCE_MS);
  }

  private ouvrir(premiere: boolean): void {
    this.suivi.ouvrir().subscribe({
      next: (session) => {
        this.identifiant.set(session.identifiant);
        if (premiere) {
          // D8 : prénom lu en base par le service du profil ; absent, l'identifiant reste affiché.
          this.profils.lire().subscribe({ next: (etat) => this.prenom.set(etat.prenom ?? ''), error: () => undefined });
        }
      },
      error: () => undefined,
    });
  }

  private lirePage(): void {
    let page: ActivatedRouteSnapshot | null = this.route.snapshot;
    while (page?.firstChild) {
      page = page.firstChild;
    }
    this.ariane.set((page?.data['ariane'] as Ariane | undefined) ?? null);
    this.produit.set(page?.queryParamMap.get('produit') ?? null);
  }
}
