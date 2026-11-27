import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { afficherDate } from '../date-metier-test/date-metier';
import { DemandeOrdre, OrdreTest, OrdresTestService, PositionTest, TypeOrdre } from './ordres';

type Etat = 'chargement' | 'affiche' | 'indisponible';

const TYPES: Record<TypeOrdre, string> = { VERSEMENT: 'Versement', RACHAT: 'Rachat', ARBITRAGE: 'Arbitrage' };
const STATUTS: Record<OrdreTest['statut'], string> = {
  DEMANDE: 'Demandé',
  EN_COURS: 'En cours',
  EXECUTE: 'Exécuté',
  REJETE: 'Rejeté',
  ANNULE: 'Annulé',
};

// CU-376 (TECH-08) : page /test/ordres. Aucun écran client ne passe encore d'ordre et les positions ne viendront
// qu'avec TECH-10 : la testeuse passe un ordre sur un contrat de test (identifiant créé à la validation, « Renvoyer
// le même ordre » pour vérifier l'absence de doublon), enregistre des positions de test et suit l'événement
// « ordre demandé » (en attente ou publié dans Kafka). Un rachat ou un arbitrage peut être passé en nombre de parts ;
// un ordre « demandé » peut être annulé avant l'heure limite (le backend refuse sinon, avec le motif).
// Absente en preprod et prod.
@Component({
  selector: 'app-ordres-test',
  imports: [RouterLink],
  template: `
    <main class="ordres-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Ordres (test)</h1>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Page de test indisponible dans cet environnement ou pour le moment.</p>
        }
        @case ('affiche') {
          <p class="vide">
            L’heure limite (12 h) suit la date métier. <a routerLink="/test/date-metier">Changer la date métier</a> ·
            <a routerLink="/test/evenements">Boîte d’envoi Kafka</a> ·
            <a routerLink="/test/catalogue">Catalogue</a> ·
            <a routerLink="/test/contrats">Créer un contrat de test</a>
          </p>

          @if (message()) {
            <p class="succes" role="status">{{ message() }}</p>
          }
          @if (erreur()) {
            <p class="erreur" role="alert">{{ erreur() }}</p>
          }

          <h2>1. Enregistrer une position de test</h2>
          <form data-test="position" class="grille" (submit)="enregistrerPosition($event)">
            <label>Contrat <input id="position-contrat" name="positionContrat" maxlength="30" [value]="contratPosition()" (change)="contratPosition.set(valeur($event))" /></label>
            <label>Code ISIN <input id="position-isin" name="positionIsin" maxlength="12" [value]="isinPosition()" (change)="isinPosition.set(valeur($event))" /></label>
            <label>Unité
              <select id="position-unite" name="positionUnite" (change)="unitePosition.set(valeur($event) === 'parts' ? 'parts' : 'euros')">
                <option value="euros" [selected]="unitePosition() === 'euros'">Montant en euros</option>
                <option value="parts" [selected]="unitePosition() === 'parts'">Nombre de parts</option>
              </select>
            </label>
            <label>{{ unitePosition() === 'parts' ? 'Nombre de parts' : 'Montant (€)' }} <input id="position-valeur" name="positionValeur" inputmode="decimal" [value]="valeurPosition()" (change)="valeurPosition.set(valeur($event))" /></label>
            <button type="submit">Enregistrer la position</button>
          </form>

          <h2>2. Passer un ordre</h2>
          <form data-test="ordre" class="grille" (submit)="passer($event)">
            <label>Contrat <input id="contrat" name="contrat" maxlength="30" [value]="contrat()" (change)="contrat.set(valeur($event))" /></label>
            <label>Type
              <select id="type" name="type" (change)="type.set(typeDe(valeur($event)))">
                @for (t of types; track t) {
                  <option [value]="t" [selected]="type() === t">{{ libelleType(t) }}</option>
                }
              </select>
            </label>
            @if (type() !== 'VERSEMENT') {
              <label>Support vendu (ISIN) <input id="depart" name="depart" maxlength="12" [value]="depart()" (change)="depart.set(valeur($event))" /></label>
            }
            @if (type() !== 'RACHAT') {
              <label>Support acheté (ISIN) <input id="arrivee" name="arrivee" maxlength="12" [value]="arrivee()" (change)="arrivee.set(valeur($event))" /></label>
            }
            @if (type() !== 'VERSEMENT') {
              <label>Unité
                <select id="unite" name="unite" (change)="unite.set(valeur($event) === 'parts' ? 'parts' : 'euros')">
                  <option value="euros" [selected]="unite() === 'euros'">Montant en euros</option>
                  <option value="parts" [selected]="unite() === 'parts'">Nombre de parts</option>
                </select>
              </label>
            }
            <label>{{ enParts() ? 'Nombre de parts' : 'Montant (€)' }} <input id="montant" name="montant" inputmode="decimal" [value]="montant()" (change)="montant.set(valeur($event))" /></label>
            <button type="submit" [disabled]="enCours()">Passer l’ordre</button>
            @if (dernier()) {
              <button type="button" class="secondaire" data-test="renvoyer" [disabled]="enCours()" (click)="envoyer(dernier()!)">Renvoyer le même ordre</button>
            }
          </form>

          <h2>Ordres</h2>
          @if (ordres().length === 0) {
            <p class="vide">Aucun ordre.</p>
          } @else {
            <div class="tableau" data-test="ordres">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Demandé le (date métier)</th>
                    <th scope="col">Contrat</th>
                    <th scope="col">Type</th>
                    <th scope="col">Supports</th>
                    <th scope="col">Montant</th>
                    <th scope="col">Statut</th>
                    <th scope="col">Exécution prévue (VL du)</th>
                    <th scope="col">Annulé le (date métier)</th>
                    <th scope="col">Disponible</th>
                    <th scope="col">Événement « ordre demandé »</th>
                    <th scope="col"><span class="masque">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (o of ordres(); track o.id) {
                    <tr>
                      <td>{{ afficher(o.demandeLe) }}</td>
                      <td>{{ o.numeroContrat }}</td>
                      <td>{{ libelleType(o.type) }}</td>
                      <td>{{ o.supportDepart ?? '—' }} → {{ o.supportArrivee ?? '—' }}</td>
                      <td>{{ quantite(o) }}</td>
                      <td>{{ libelleStatut(o.statut) }}</td>
                      <td>{{ jour(o.dateExecutionPrevue) }}</td>
                      <td>{{ o.annuleLe ? afficher(o.annuleLe) : '—' }}</td>
                      <td>{{ euros(o.montantDisponible) }}</td>
                      <td>
                        @if (o.evenement) {
                          @if (o.evenement.statut === 'PUBLIE') {
                            Publié le {{ afficher(o.evenement.publieLe!) }}
                          } @else {
                            En attente{{ o.evenement.derniereErreur ? ' — ' + o.evenement.derniereErreur : '' }}
                          }
                        } @else {
                          —
                        }
                      </td>
                      <td>
                        @if (o.statut === 'DEMANDE') {
                          <button type="button" class="secondaire" data-test="annuler-ordre" [disabled]="enCours()" [attr.aria-label]="'Annuler l’ordre du ' + afficher(o.demandeLe)" (click)="annuler(o)">Annuler</button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <h2>Positions de test</h2>
          @if (positions().length === 0) {
            <p class="vide">Aucune position de test.</p>
          } @else {
            <div class="tableau" data-test="positions">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Contrat</th>
                    <th scope="col">Code ISIN</th>
                    <th scope="col">Position</th>
                    <th scope="col">Enregistrée le</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of positions(); track $index) {
                    <tr>
                      <td>{{ p.numeroContrat }}</td>
                      <td>{{ p.codeIsin }}</td>
                      <td>{{ p.montant !== null ? euros(p.montant) : p.nombreParts + ' parts' }}</td>
                      <td>{{ afficher(p.enregistreeLe) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <h2>Remise à zéro</h2>
          @if (confirmerVidage()) {
            <p class="erreur">Tous les ordres et positions de test seront effacés. Confirmez-vous ?</p>
            <div class="ligne">
              <button type="button" class="danger" data-test="confirmer-vider" (click)="vider()">Oui, tout effacer</button>
              <button type="button" class="secondaire" data-test="annuler-vider" (click)="confirmerVidage.set(false)">Annuler</button>
            </div>
          } @else {
            <button type="button" class="secondaire" data-test="vider" (click)="confirmerVidage.set(true)">Vider les ordres et positions de test</button>
          }
        }
      }
    </main>
  `,
  styles: `
    .ordres-test { max-width: 1200px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    h2 { color: #0b2545; margin: 32px 0 12px; font-size: 20px; }
    .grille { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; }
    label { display: flex; flex-direction: column; gap: 6px; font-weight: 700; font-size: 14px; }
    input, select { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; font: inherit; font-weight: 400; min-width: 0; }
    .vide { color: #5d6887; line-height: 1.6; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: wait; }
    button.secondaire { background: #fff; color: #0b2545; }
    button.danger { background: #b42318; border-color: #b42318; }
    .ligne { display: flex; flex-wrap: wrap; gap: 8px; }
    .tableau { overflow-x: auto; border: 1px solid #eef1f5; border-radius: 8px; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; }
    th { background: #f4f6fa; white-space: nowrap; }
    .masque { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `,
})
export class OrdresTest {
  private readonly service = inject(OrdresTestService);
  protected readonly types = Object.keys(TYPES) as TypeOrdre[];
  protected readonly etat = signal<Etat>('chargement');
  protected readonly ordres = signal<OrdreTest[]>([]);
  protected readonly positions = signal<PositionTest[]>([]);
  protected readonly message = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly confirmerVidage = signal(false);
  protected readonly dernier = signal<DemandeOrdre | null>(null);
  protected readonly contrat = signal('AV-TEST-0001');
  protected readonly type = signal<TypeOrdre>('RACHAT');
  protected readonly depart = signal('');
  protected readonly arrivee = signal('');
  protected readonly montant = signal('');
  protected readonly unite = signal<'euros' | 'parts'>('euros');
  /** CU-376 : un rachat ou un arbitrage peut être exprimé en parts ; un versement reste en euros. */
  protected readonly enParts = computed(() => this.type() !== 'VERSEMENT' && this.unite() === 'parts');
  protected readonly contratPosition = signal('AV-TEST-0001');
  protected readonly isinPosition = signal('');
  protected readonly unitePosition = signal<'euros' | 'parts'>('euros');
  protected readonly valeurPosition = signal('');
  protected readonly afficher = afficherDate;

  constructor() {
    this.charger();
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected typeDe(valeur: string): TypeOrdre {
    return valeur in TYPES ? (valeur as TypeOrdre) : 'RACHAT';
  }

  protected libelleType(type: TypeOrdre): string {
    return TYPES[type];
  }

  protected libelleStatut(statut: OrdreTest['statut']): string {
    return STATUTS[statut];
  }

  protected euros(montant: number): string {
    return `${montant.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).replace(/ | /g, ' ')} €`;
  }

  protected jour(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString('fr-FR');
  }

  protected passer(event: Event): void {
    event.preventDefault();
    const quantite = OrdresTest.nombre(this.montant());
    const enParts = this.enParts();
    if (quantite === null) {
      this.afficherErreur(
        enParts
          ? 'Nombre de parts invalide : indiquez un nombre positif, par exemple 10 ou 2,5.'
          : 'Montant invalide : indiquez un nombre positif, par exemple 1000 ou 50,5.',
      );
      return;
    }
    const type = this.type();
    // CU-376 : l'identifiant unique de l'ordre est créé ici, au moment de la validation.
    this.envoyer({
      id: crypto.randomUUID(),
      numeroContrat: this.contrat().trim(),
      type,
      supportDepart: type === 'VERSEMENT' ? null : OrdresTest.isin(this.depart()),
      supportArrivee: type === 'RACHAT' ? null : OrdresTest.isin(this.arrivee()),
      montant: enParts ? null : quantite,
      nombreParts: enParts ? quantite : null,
    });
  }

  protected quantite(ordre: OrdreTest): string {
    return ordre.nombreParts !== null
      ? `${ordre.nombreParts.toLocaleString('fr-FR', { maximumFractionDigits: 6 })} parts`
      : this.euros(ordre.montant ?? 0);
  }

  protected annuler(ordre: OrdreTest): void {
    this.message.set(null);
    this.erreur.set(null);
    this.enCours.set(true);
    this.service.annuler(ordre.id).subscribe({
      next: () => {
        this.enCours.set(false);
        this.message.set('Ordre annulé : le montant est de nouveau disponible.');
        this.charger();
      },
      error: (refus: HttpErrorResponse) => {
        this.enCours.set(false);
        this.afficherErreur(refus.error?.message ?? 'L’ordre n’a pas pu être annulé. Réessayez.');
      },
    });
  }

  protected envoyer(demande: DemandeOrdre): void {
    this.message.set(null);
    this.erreur.set(null);
    this.enCours.set(true);
    this.service.passer(demande).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.dernier.set(demande);
        this.message.set(
          reponse.status === 201
            ? 'Ordre enregistré au statut « demandé ».'
            : 'Ordre déjà enregistré : aucun doublon créé.',
        );
        this.charger();
      },
      error: (refus: HttpErrorResponse) => {
        this.enCours.set(false);
        this.afficherErreur(refus.error?.message ?? 'L’ordre n’a pas pu être enregistré. Réessayez.');
      },
    });
  }

  protected enregistrerPosition(event: Event): void {
    event.preventDefault();
    const valeur = OrdresTest.nombre(this.valeurPosition());
    if (valeur === null) {
      this.afficherErreur('Valeur invalide : indiquez un nombre positif.');
      return;
    }
    const enParts = this.unitePosition() === 'parts';
    this.message.set(null);
    this.erreur.set(null);
    this.service
      .enregistrerPosition(
        this.contratPosition().trim(),
        OrdresTest.isin(this.isinPosition()) ?? '',
        enParts ? null : valeur,
        enParts ? valeur : null,
      )
      .subscribe({
        next: (resultat) => {
          this.message.set(`Montant disponible sur ${resultat.codeIsin} : ${this.euros(resultat.disponible)}`);
          this.charger();
        },
        error: (refus: HttpErrorResponse) =>
          this.afficherErreur(refus.error?.message ?? 'La position n’a pas pu être enregistrée. Réessayez.'),
      });
  }

  protected vider(): void {
    this.message.set(null);
    this.erreur.set(null);
    this.service.vider().subscribe({
      next: () => {
        this.confirmerVidage.set(false);
        this.dernier.set(null);
        this.message.set('Ordres et positions de test effacés.');
        this.charger();
      },
      error: () => {
        this.confirmerVidage.set(false);
        this.afficherErreur('Les ordres n’ont pas pu être effacés. Réessayez.');
      },
    });
  }

  private afficherErreur(texte: string): void {
    this.message.set(null);
    this.erreur.set(texte);
  }

  private static nombre(saisie: string): number | null {
    const texte = saisie.trim().replace(/\s/g, '').replace(',', '.');
    const valeur = Number(texte);
    return texte !== '' && Number.isFinite(valeur) && valeur > 0 ? valeur : null;
  }

  private static isin(saisie: string): string | null {
    const texte = saisie.trim().toUpperCase();
    return texte === '' ? null : texte;
  }

  private charger(): void {
    forkJoin({ ordres: this.service.ordres(), positions: this.service.positions() }).subscribe({
      next: ({ ordres, positions }) => {
        this.ordres.set(ordres);
        this.positions.set(positions);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }
}
