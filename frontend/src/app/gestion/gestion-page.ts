import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { afficherDate } from '../date-metier-test/date-metier';
import { euros, eurosAuCentime } from '../livret-a/parametres';
import { EtatGestion, GestionService, ModeGestion, Produit, ProfilGestion, Versement } from './gestion';

type Etat = 'chargement' | 'affiche' | 'indisponible';

export const MODES: { code: ModeGestion; titre: string; texte: string }[] = [
  { code: 'PILOTEE', titre: 'Gestion pilotée', texte: 'Nous répartissons votre épargne selon votre profil de gestion.' },
  { code: 'LIBRE', titre: 'Gestion libre', texte: 'Vous choisissez vous-même vos supports.' },
  { code: 'MANDAT', titre: 'Gestion sous mandat', texte: 'Un gérant décide pour vous, dans le cadre de votre profil.' },
];

export const PROFILS: Record<ProfilGestion, string> = { PRUDENT: 'Prudent', EQUILIBRE: 'Équilibré', DYNAMIQUE: 'Dynamique' };

const LIBELLE_MODE: Record<ModeGestion, string> = { LIBRE: 'Libre', PILOTEE: 'Pilotée', MANDAT: 'Sous mandat' };

const pourcent = (valeur: number) => `${String(valeur).replace('.', ',')} %`;

// CU-314 (PER-07) et CU-338 (AV-07) : choisir son mode de gestion (?produit=PER|AV), après « Continuer » sur la page
// Découvrir puis la souscription (PER-04, AV-01). Sans contrat, la page mène à la souscription ; le contrat de test
// (dev et qualif) se crée sur la page /test/contrats, jamais ici (aucun élément de test visible). Les conditions du mandat
// (taux tout compris, montant minimum) s'affichent dès que le
// mandat est sélectionné, avant validation ; les refus viennent du backend avec leur message.
@Component({
  selector: 'app-gestion-page',
  imports: [RouterLink],
  template: `
    <main class="gestion">
      <a routerLink="/decouvrir" [queryParams]="{ produit }">← Découvrir {{ nomProduit }}</a>
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
                @for (m of modes; track m.code) {
                  <label class="option">
                    <input type="radio" name="mode" [value]="m.code" [checked]="mode() === m.code" (change)="mode.set(m.code)" />
                    <span><strong>{{ m.titre }}</strong> — {{ m.texte }}</span>
                  </label>
                }
              </fieldset>
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
                  <table>
                    <thead><tr><th>Support</th><th>Risque</th><th>Part</th></tr></thead>
                    <tbody>
                      @for (l of contrat.allocation; track l.codeIsin) {
                        <tr>
                          <td>{{ l.nom }} @if (l.nonCote) {<span class="badge">non coté</span>}</td>
                          <td>{{ l.niveauRisque }}/7</td>
                          <td>{{ pourcent(l.partPct) }}</td>
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
              <a class="btn p" data-test="souscrire" routerLink="/decouvrir" [queryParams]="{ produit }">{{ produit === 'PER' ? 'Souscrire mon PER' : 'Souscrire mon assurance vie' }}</a>
            </section>
          }
        }
      }
    </main>
  `,
  styles: `
    .gestion { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1, h2 { color: #0b2545; }
    h1 { margin: 24px 0 8px; }
    h2 { margin: 0 0 8px; font-size: 18px; }
    .lead, .detail { line-height: 1.6; color: #5d6887; }
    .carte { border: 1px solid #d9dee6; border-radius: 14px; padding: 16px; background: #fff; line-height: 1.6; margin-bottom: 16px; display: grid; gap: 8px; }
    fieldset { border: 0; padding: 0; margin: 0; display: grid; gap: 8px; }
    legend { font-weight: 700; margin-bottom: 4px; }
    .option { display: flex; gap: 8px; align-items: baseline; }
    .profil { display: grid; gap: 4px; font-weight: 700; max-width: 260px; }
    select, input:not([type]), input[inputmode] { padding: 8px; border: 1px solid #d9dee6; border-radius: 8px; font: inherit; }
    .mandat { padding: 12px; border-radius: 10px; background: #f4f6fa; }
    .mandat p { margin: 0 0 4px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #eef1f5; }
    .badge { margin-left: 6px; padding: 2px 8px; border-radius: 999px; background: #fff4e5; color: #8a4b00; font-size: 12px; font-weight: 700; }
    .versement { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
    .versement label { display: grid; gap: 4px; font-weight: 700; }
    .btn { justify-self: start; padding: 10px 18px; border-radius: 999px; font-weight: 700; cursor: pointer; font: inherit; }
    .btn.o { border: 1px solid #0b2545; background: #fff; color: #0b2545; }
    .btn.p { border: 0; background: #0b2545; color: #fff; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
  `,
})
export class GestionPage {
  private readonly service = inject(GestionService);
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
