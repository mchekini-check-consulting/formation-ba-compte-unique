import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Tunnel } from '../tunnel/tunnel';
import { InfoBulle, Terme } from '../pedagogie/info-bulle';
import { Observable } from 'rxjs';
import { afficherDate } from '../date-metier-test/date-metier';
import { euros, eurosAuCentime } from '../livret-a/parametres';
import { EtatGestion, GestionService, ModeGestion, Produit, ProfilGestion, Versement, LIBELLE_MODE, PROFILS } from './gestion';

type Etat = 'chargement' | 'affiche' | 'indisponible';

export const MODES: { code: ModeGestion; titre: string; texte: string; terme?: Terme }[] = [
  { code: 'PILOTEE', titre: 'Gestion pilotée', texte: 'Nous répartissons votre épargne selon votre profil de gestion.', terme: 'gestion-pilotee' },
  { code: 'LIBRE', titre: 'Gestion libre', texte: 'Vous choisissez vous-même vos supports.', terme: 'gestion-libre' },
  { code: 'MANDAT', titre: 'Gestion sous mandat', texte: 'Un gérant décide pour vous, dans le cadre de votre profil.' },
];

export { PROFILS } from './gestion';

const pourcent = (valeur: number) => `${String(valeur).replace('.', ',')} %`;

// CU-314 (PER-07) et CU-338 (AV-07) : choisir son mode de gestion (?produit=PER|AV), après « Continuer » sur la page
// Découvrir puis la souscription (PER-04, AV-01). Sans contrat, la page mène à la souscription ; le contrat de test
// (dev et qualif) se crée sur la page /test/contrats, jamais ici (aucun élément de test visible). Les conditions du mandat
// (taux tout compris, montant minimum) s'affichent dès que le
// mandat est sélectionné, avant validation ; les refus viennent du backend avec leur message.
@Component({
  selector: 'app-gestion-page',
  imports: [RouterLink, Tunnel, InfoBulle],
  template: `
    <div class="page gestion">
      <app-tunnel [produit]="produit" etape="gestion" [actif]="dansLeTunnel">
      <h1>Choisir mon mode de gestion</h1>
      <p class="lead">{{ produit === 'PER' ? 'Plan d’épargne retraite' : 'Assurance vie' }} : changer de mode ou de profil est gratuit.</p>

      @if (message()) {
        <p class="succes" role="status">{{ message() }}</p>
      }
      @if (erreur()) {
        <p class="erreur" role="alert">{{ erreur() }}</p>
      }

      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="erreur" role="alert">Le choix du mode de gestion est momentanément indisponible. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          @if (!donnees()!.profilInvestisseur) {
            <p class="carte" data-test="profil-requis">
              Votre profil client doit être complété ou revalidé avant de choisir votre gestion.
              <a routerLink="/profil">Compléter mon profil</a>
            </p>
          }
          @if (donnees()!.contrat; as contrat) {
            <section class="carte actuelle" data-test="actuelle">
              <h2>Ma gestion actuelle</h2>
              <p>
                <strong>{{ libelleMode(contrat.mode) }}</strong> · profil {{ libelleProfil(contrat.profil) }}, depuis le
                {{ date(contrat.changeLe) }}
              </p>
              <p class="detail">
                Contrat {{ contrat.numeroContrat }} · encours {{ eurosAuCentime(contrat.encours) }}
              </p>
            </section>

            <form class="carte" data-test="choix" (submit)="valider($event)">
              <h2>Mon choix</h2>
              <fieldset>
                <legend>Mode de gestion</legend>
                <!-- Lot 5 : trois cartes côte à côte (pilotée, libre, sous mandat). -->
                <div class="cartes-modes">
                  @for (m of modes; track m.code) {
                    <label class="option carte-mode" [class.choisie]="mode() === m.code">
                      <input type="radio" name="mode" [value]="m.code" [checked]="mode() === m.code" (change)="mode.set(m.code)" />
                      <span><strong>{{ m.titre }}</strong>@if (m.terme) {<app-info [terme]="m.terme" />}<br />{{ m.texte }}</span>
                    </label>
                  }
                </div>
              </fieldset>
              @if (mode() === 'LIBRE') {
                <!-- Lot 5 : en gestion libre, la cliente choisit ses supports (lot 6). -->
                <p data-test="vers-supports"><a routerLink="/supports" [queryParams]="dansLeTunnel ? { produit, tunnel: 1 } : { produit }">Choisir mes supports</a></p>
              }
              @if (mode() !== 'LIBRE') {
                <label class="profil">
                  Profil de gestion
                  <select name="profil" (change)="profil.set(profilDe($event))">
                    @for (p of profils; track p) {
                      <option [value]="p" [selected]="profil() === p">{{ libelleProfil(p) }}</option>
                    }
                  </select>
                </label>
                @if (donnees()!.profilInvestisseur) {
                  <p class="detail">Votre profil investisseur : {{ libelleProfil(donnees()!.profilInvestisseur!) }}. Votre profil de gestion ne peut pas le dépasser.</p>
                }
              }
              @if (mode() === 'MANDAT') {
                <div class="mandat" data-test="conditions-mandat">
                  <p><strong>Taux de frais tout compris : {{ pourcent(donnees()!.mandat.tauxToutComprisPct) }} par an.</strong> Il couvre les frais de mandat et les frais de gestion des supports.</p>
                  <p><strong>Montant minimum du mandat : {{ euros(donnees()!.mandat.montantMinimum) }}.</strong></p>
                  <p>Vous pouvez sortir du mandat à tout moment, sans préavis.</p>
                </div>
              }
              @if (produit === 'AV' && mode() === 'PILOTEE' && contrat.mode !== 'PILOTEE' && contrat.encoursNonCote > 0) {
                <p class="mandat" data-test="non-cotes">Vos supports non cotés ({{ euros(contrat.encoursNonCote) }}) restent dans votre contrat et ne sont pas gérés par la gestion pilotée.</p>
              }
              <button class="btn p" type="submit" data-test="valider" [disabled]="envoi() || inchange()">Valider mon choix</button>
            </form>

            @if (contrat.mode === 'PILOTEE') {
              <section class="carte" data-test="allocation">
                <h2>Ma répartition en gestion pilotée</h2>
                @if (produit === 'PER') {
                  <p>
                    @if (contrat.contraintes.horizonAnnees !== null) {
                      À {{ contrat.contraintes.horizonAnnees }} an(s) de votre retraite :
                    }
                    au moins {{ pourcent(contrat.contraintes.partMinFaibleRisquePct) }} d’actifs à faible risque et
                    {{ pourcent(contrat.contraintes.partMinNonCotePct) }} de non coté.
                  </p>
                } @else {
                  <p>Au moins {{ pourcent(contrat.contraintes.partMinNonCotePct) }} de vos nouveaux versements vont sur des supports non cotés.</p>
                }
                @if (contrat.contraintes.grilleAValider) {
                  <p class="detail" data-test="a-valider">Pourcentages d’exemple, à valider par la conformité.</p>
                }
                @if (contrat.allocationIndisponible) {
                  <p class="erreur">{{ contrat.allocationIndisponible }}</p>
                } @else {
                  <!-- PO 28/11 : la répartition telle qu'elle est enregistrée, une ligne par support, sans regrouper,
                       arrondir ni masquer ; la barre illustre la part exacte de la ligne. -->
                  <table data-test="repartition-pilotee">
                    <thead><tr><th>Support</th><th>Catégorie</th><th>Risque</th><th>Part</th></tr></thead>
                    <tbody>
                      @for (l of contrat.allocation; track l.codeIsin) {
                        <tr>
                          <td>{{ l.nom }} @if (l.nonCote) {<span class="badge">non coté</span>}</td>
                          <td>{{ l.categorie }}</td>
                          <td>{{ l.niveauRisque }}/7</td>
                          <td class="rp-part">
                            <span class="rp-piste" aria-hidden="true"><span [style.width.%]="l.partPct"></span></span>
                            {{ pourcent(l.partPct) }}
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                  <form class="versement" data-test="versement" (submit)="verser($event)">
                    <label>Verser (€) <input name="montant" inputmode="decimal" [value]="montant()" (change)="montant.set(valeur($event))" /></label>
                    <button class="btn o" type="submit" [disabled]="envoi()">Verser selon ma répartition</button>
                  </form>
                  @if (versement(); as v) {
                    <ul data-test="repartition">
                      @for (r of v.repartition; track r.codeIsin) {
                        <li>{{ r.codeIsin }} : {{ eurosAuCentime(r.montant) }}</li>
                      }
                    </ul>
                  }
                }
              </section>
            }

            <section class="carte">
              <h2>Historique</h2>
              <ul data-test="historique">
                @for (c of contrat.historique; track $index) {
                  <li>{{ date(c.changeLe) }} : {{ libelleMode(c.mode) }} · {{ libelleProfil(c.profil) }} — frais {{ eurosAuCentime(c.frais) }}</li>
                }
              </ul>
              @for (r of contrat.reequilibrages; track $index) {
                <p class="detail" data-test="reequilibrage">
                  Rééquilibrage du {{ date(r.demandeLe) }} : {{ r.arbitrages.length }} arbitrage(s) gratuit(s), {{ eurosAuCentime(r.montantArbitre) }}, au prochain cours.
                </p>
              }
            </section>
          } @else {
            <section class="carte" data-test="sans-contrat">
              <p>Vous n’avez pas encore de contrat {{ nomCourt }}. Votre contrat démarrera en gestion pilotée ; vous pourrez ensuite choisir votre mode de gestion ici.</p>
              <a class="btn p" data-test="souscrire" routerLink="/produit" [queryParams]="{ produit }">{{ produit === 'PER' ? 'Souscrire mon PER' : 'Souscrire mon assurance vie' }}</a>
            </section>
          }
        }
      }
      </app-tunnel>
    </div>
  `,
  styles: `
    .cartes-modes { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
    .carte-mode { display: flex; gap: 8px; align-items: flex-start; padding: 14px; border: 1px solid #d9dee6; border-radius: 14px; background: #fff; cursor: pointer; }
    .carte-mode.choisie { border-color: #0b2545; box-shadow: 0 0 0 2px #0b2545 inset; }
    h2 { margin: 0 0 8px; font-size: 18px; }
    .lead, .detail { line-height: 1.6; color: #5d6887; }
    .option { display: flex; gap: 8px; align-items: baseline; }
    .profil { display: grid; gap: 4px; font-weight: 700; max-width: 260px; }
    .mandat { padding: 12px; border-radius: 10px; background: #f4f6fa; }
    .mandat p { margin: 0 0 4px; }
    .badge { margin-left: 6px; padding: 2px 8px; border-radius: 999px; background: #fff4e5; color: #8a4b00; font-size: 12px; font-weight: 700; white-space: nowrap; }
    .versement { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
    .versement label { display: grid; gap: 4px; font-weight: 700; }
  `,
})
export class GestionPage {
  private readonly service = inject(GestionService);
  /** Lot 5 : étape 6 (facultative) du tunnel quand la page est ouverte depuis la confirmation (?tunnel=1). */
  protected readonly dansLeTunnel = inject(ActivatedRoute).snapshot.queryParamMap.get('tunnel') === '1';
  protected readonly produit: Produit =
    inject(ActivatedRoute).snapshot.queryParamMap.get('produit') === 'PER' ? 'PER' : 'AV';
  protected readonly nomProduit = this.produit === 'PER' ? 'le PER' : 'l’assurance vie';
  protected readonly nomCourt = this.produit === 'PER' ? 'PER' : 'd’assurance vie';
  protected readonly modes = MODES;
  protected readonly profils = Object.keys(PROFILS) as ProfilGestion[];
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<EtatGestion | null>(null);
  protected readonly mode = signal<ModeGestion>('PILOTEE');
  protected readonly profil = signal<ProfilGestion>('EQUILIBRE');
  protected readonly montant = signal('');
  protected readonly versement = signal<Versement | null>(null);
  protected readonly envoi = signal(false);
  protected readonly message = signal('');
  protected readonly erreur = signal('');
  protected readonly inchange = computed(() => {
    const contrat = this.donnees()?.contrat;
    return !!contrat && contrat.mode === this.mode() && (this.mode() === 'LIBRE' || contrat.profil === this.profil());
  });
  protected readonly euros = euros;
  protected readonly eurosAuCentime = eurosAuCentime;
  protected readonly pourcent = pourcent;
  protected readonly date = afficherDate;

  constructor() {
    this.service.etat(this.produit).subscribe({
      next: (donnees) => {
        this.afficher(donnees);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }

  protected libelleMode(mode: ModeGestion): string {
    return LIBELLE_MODE[mode];
  }

  protected libelleProfil(profil: ProfilGestion): string {
    return PROFILS[profil];
  }

  protected profilDe(event: Event): ProfilGestion {
    const valeur = this.valeur(event) as ProfilGestion;
    return valeur in PROFILS ? valeur : 'EQUILIBRE';
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected valider(event: Event): void {
    event.preventDefault();
    const avant = this.donnees()?.contrat?.mode;
    const vers = this.mode();
    let texte = 'Votre choix est enregistré, sans frais.';
    if (vers === 'PILOTEE' && avant !== 'PILOTEE') {
      texte += ' Vos positions seront rééquilibrées au prochain cours, sans frais.';
    } else if (avant === 'MANDAT' && vers !== 'MANDAT') {
      texte = 'Votre sortie du mandat est enregistrée, sans préavis ni frais.';
    }
    this.envoyer(this.service.choisir(this.produit, vers, this.profil()), texte);
  }

  protected verser(event: Event): void {
    event.preventDefault();
    const montant = Number(this.montant().replace(',', '.').replace(/\s/g, ''));
    if (!Number.isFinite(montant) || montant <= 0) {
      this.erreur.set('Indiquez un montant positif.');
      return;
    }
    this.debut();
    this.service.verser(this.produit, crypto.randomUUID(), montant).subscribe({
      next: (versement) => {
        this.versement.set(versement);
        this.envoi.set(false);
        this.message.set(`Versement de ${eurosAuCentime(versement.montant)} enregistré, réparti selon votre gestion pilotée.`);
      },
      error: (refus: HttpErrorResponse) => this.echec(refus, 'Le versement n’a pas pu être enregistré. Réessayez.'),
    });
  }

  private envoyer(appel: Observable<EtatGestion>, texte: string): void {
    this.debut();
    appel.subscribe({
      next: (donnees) => {
        this.afficher(donnees);
        this.envoi.set(false);
        this.message.set(texte);
      },
      error: (refus: HttpErrorResponse) => this.echec(refus, 'Votre choix n’a pas pu être enregistré. Réessayez.'),
    });
  }

  private debut(): void {
    this.envoi.set(true);
    this.erreur.set('');
    this.message.set('');
  }

  private echec(refus: HttpErrorResponse, parDefaut: string): void {
    this.envoi.set(false);
    this.erreur.set(refus.error?.message ?? parDefaut);
  }

  private afficher(donnees: EtatGestion): void {
    this.donnees.set(donnees);
    if (donnees.contrat) {
      this.mode.set(donnees.contrat.mode);
      this.profil.set(donnees.contrat.profil);
    }
  }
}
