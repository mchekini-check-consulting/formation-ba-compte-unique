import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { JournalAlertes } from '../alertes-test/journal-alertes';
import { forkJoin } from 'rxjs';
import { DateMetierService, afficherDate } from '../date-metier-test/date-metier';
import { CatalogueTestService, FichierDepose, JournalBatch, ValeurLiquidative } from './catalogue';

type Etat = 'chargement' | 'affiche' | 'indisponible';

// CU-381 (TECH-13) : page /test/catalogue. Le prototype n'a pas de dépôt fournisseur : la testeuse dépose le
// fichier catalogue_uc_AAAA-MM-JJ.csv, lance le batch sans attendre 2 h (pour la date métier, en semaine) et lit
// le journal : supports ajoutés, modifiés, retirés et lignes rejetées avec leur motif. Absente en preprod et prod.
@Component({
  selector: 'app-catalogue-test',
  imports: [RouterLink, JournalAlertes],
  template: `
    <main class="catalogue-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Catalogue des unités de compte (test)</h1>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Page de test indisponible dans cet environnement ou pour le moment.</p>
        }
        @case ('affiche') {
          <p data-test="date-metier">
            Date métier : <strong>{{ dateMetier() }}</strong>.
            Le batch charge le fichier de cette date. <a routerLink="/test/date-metier">Changer la date métier</a>
          </p>

          <h2>1. Déposer le fichier du jour</h2>
          <label for="fichier">Fichier catalogue_uc_AAAA-MM-JJ.csv</label>
          <input #fichier id="fichier" type="file" accept=".csv,text/csv" (change)="deposer(fichier)" />
          @if (fichiers().length > 0) {
            <p class="vide">
              Derniers dépôts :
              @for (f of fichiers(); track $index) {
                <span class="badge">{{ f.nomFichier }}</span>
              }
            </p>
          }

          <h2>2. Lancer le batch</h2>
          <button type="button" (click)="lancer()" [disabled]="enCours()">Lancer le batch maintenant</button>

          @if (message()) {
            <p class="succes" role="status">{{ message() }}</p>
          }
          @if (erreur()) {
            <p class="erreur" role="alert">{{ erreur() }}</p>
          }

          <h2>Historique des valeurs liquidatives</h2>
          <form data-test="historique" (submit)="chercher($event, isin.value)">
            <label for="isin">Code ISIN du support</label>
            <div class="ligne">
              <input #isin id="isin" name="isin" maxlength="12" autocomplete="off" />
              <button type="submit">Voir l’historique</button>
            </div>
          </form>
          @if (messageVl()) {
            <p class="vide" data-test="vl-message">{{ messageVl() }}</p>
          }
          @if (historique().length > 0) {
            <div class="tableau" data-test="vl">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Date de la VL</th>
                    <th scope="col">Valeur liquidative</th>
                    <th scope="col">Source</th>
                  </tr>
                </thead>
                <tbody>
                  @for (v of historique(); track v.dateVl) {
                    <tr>
                      <td>{{ jour(v.dateVl) }}</td>
                      <td>{{ euros(v.valeur) }}</td>
                      <td>{{ v.source }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <h2>Vider le catalogue</h2>
          <p class="vide">Avant une recette : efface les supports et leurs valeurs liquidatives, puis redéposez le fichier de référence et relancez le batch. Le journal est conservé.</p>
          @if (confirmerVidage()) {
            <p class="erreur">Tous les supports et leurs valeurs liquidatives seront effacés. Confirmez-vous ?</p>
            <div class="ligne">
              <button type="button" class="danger" data-test="confirmer-vider" (click)="vider()">Oui, vider le catalogue</button>
              <button type="button" class="secondaire" data-test="annuler-vider" (click)="confirmerVidage.set(false)">Annuler</button>
            </div>
          } @else {
            <button type="button" class="secondaire" data-test="vider" (click)="confirmerVidage.set(true)">Vider le catalogue</button>
          }

          <app-journal-alertes />

          <h2>Journal du batch</h2>
          @if (journal().length === 0) {
            <p class="vide">Aucune exécution.</p>
          } @else {
            @for (j of journal(); track $index) {
              <section class="execution" [class.echec]="j.statut === 'ECHEC'">
                <h3>
                  {{ j.nomFichier }} — {{ j.statut === 'TERMINE' ? 'Terminé' : 'Échec' }}
                  <span class="badge">{{ j.declenchement === 'MANUEL' ? 'Lancé à la main' : 'Automatique' }}</span>
                </h3>
                <p class="vide">Débuté le {{ afficher(j.debut) }} (date métier)</p>
                @if (j.statut === 'ECHEC') {
                  <p class="erreur">{{ j.message }} — catalogue et dernières valeurs liquidatives conservés.</p>
                } @else {
                  <p data-test="compteurs">
                    {{ j.ajoutes }} ajoutés · {{ j.modifies }} modifiés · {{ j.retires }} retirés ·
                    {{ j.rejetees }} lignes rejetées
                  </p>
                  @if (j.lignesRejetees.length > 0) {
                    <div class="tableau">
                      <table>
                        <thead>
                          <tr>
                            <th scope="col">Ligne</th>
                            <th scope="col">Code ISIN</th>
                            <th scope="col">Motif</th>
                          </tr>
                        </thead>
                        <tbody>
                          @for (l of j.lignesRejetees; track $index) {
                            <tr>
                              <td>{{ l.numeroLigne }}</td>
                              <td>{{ l.codeIsin ?? '—' }}</td>
                              <td>{{ l.motif }}</td>
                            </tr>
                          }
                        </tbody>
                      </table>
                    </div>
                  }
                }
              </section>
            }
          }
        }
      }
    </main>
  `,
  styles: `
    .catalogue-test { max-width: 1100px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    h2 { color: #0b2545; margin: 32px 0 12px; font-size: 20px; }
    h3 { margin: 0 0 6px; font-size: 16px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    label { display: block; font-weight: 700; margin-bottom: 8px; }
    input { max-width: 100%; }
    .badge { font-size: 13px; padding: 4px 10px; border-radius: 999px; background: #f4f6fa; color: #5d6887; margin-right: 6px; }
    .vide { color: #5d6887; line-height: 1.6; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #0b2545; color: #fff; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: 0.6; cursor: wait; }
    button.secondaire { background: #fff; color: #0b2545; }
    button.danger { background: #b42318; border-color: #b42318; }
    .ligne { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .ligne input { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; font: inherit; }
    .execution { border: 1px solid #d9dee6; border-radius: 12px; padding: 12px 16px; margin-bottom: 12px; background: #fff; }
    .execution.echec { border-color: #fda29b; }
    .tableau { overflow-x: auto; border: 1px solid #eef1f5; border-radius: 8px; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; }
    th { background: #f4f6fa; white-space: nowrap; }
  `,
})
export class CatalogueTest {
  private readonly service = inject(CatalogueTestService);
  private readonly dates = inject(DateMetierService);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly dateMetier = signal('');
  protected readonly fichiers = signal<FichierDepose[]>([]);
  protected readonly journal = signal<JournalBatch[]>([]);
  protected readonly message = signal<string | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly afficher = afficherDate;
  protected readonly historique = signal<ValeurLiquidative[]>([]);
  protected readonly messageVl = signal<string | null>(null);
  protected readonly confirmerVidage = signal(false);

  constructor() {
    this.charger();
  }

  protected jour(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString('fr-FR');
  }

  protected euros(valeur: number): string {
    return valeur.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  }

  protected chercher(event: Event, saisie: string): void {
    event.preventDefault();
    const codeIsin = saisie.trim().toUpperCase();
    this.historique.set([]);
    this.messageVl.set(null);
    if (!codeIsin) {
      return;
    }
    this.service.historique(codeIsin).subscribe({
      next: (valeurs) => {
        this.historique.set(valeurs);
        if (valeurs.length === 0) {
          this.messageVl.set(`Aucune valeur liquidative pour ${codeIsin}.`);
        }
      },
      error: (refus: HttpErrorResponse) =>
        this.messageVl.set(
          refus.status === 404
            ? `Aucun support ${codeIsin} au catalogue.`
            : 'L’historique est momentanément indisponible. Réessayez.',
        ),
    });
  }

  protected vider(): void {
    this.message.set(null);
    this.erreur.set(null);
    this.service.vider().subscribe({
      next: () => {
        this.confirmerVidage.set(false);
        this.historique.set([]);
        this.message.set('Catalogue vidé : redéposez le fichier de référence puis lancez le batch.');
        this.charger();
      },
      error: () => {
        this.confirmerVidage.set(false);
        this.erreur.set('Le catalogue n’a pas pu être vidé. Réessayez.');
      },
    });
  }

  protected deposer(champ: HTMLInputElement): void {
    const fichier = champ.files?.[0];
    if (!fichier) {
      return;
    }
    this.message.set(null);
    this.erreur.set(null);
    fichier.text().then((contenu) =>
      this.service.deposer(fichier.name, contenu).subscribe({
        next: (depot) => {
          champ.value = '';
          this.message.set(`Fichier ${depot.nomFichier} déposé.`);
          this.charger();
        },
        error: (refus: HttpErrorResponse) =>
          this.erreur.set(refus.error?.message ?? 'Le fichier n’a pas pu être déposé. Réessayez.'),
      }),
    );
  }

  protected lancer(): void {
    this.message.set(null);
    this.erreur.set(null);
    this.enCours.set(true);
    this.service.lancer().subscribe({
      next: (resultat) => {
        this.enCours.set(false);
        if (resultat.statut === 'TERMINE') {
          this.message.set(`Batch terminé : ${resultat.ajoutes} ajoutés, ${resultat.rejetees} lignes rejetées.`);
        } else {
          this.erreur.set(`Batch en échec : ${resultat.message}`);
        }
        this.charger();
      },
      error: (refus: HttpErrorResponse) => {
        this.enCours.set(false);
        this.erreur.set(refus.error?.message ?? 'Le batch n’a pas pu être lancé. Réessayez.');
      },
    });
  }

  private charger(): void {
    forkJoin({ date: this.dates.lire(), fichiers: this.service.fichiers(), journal: this.service.journal() }).subscribe({
      next: ({ date, fichiers, journal }) => {
        this.dateMetier.set(new Date(date.dateMetier).toLocaleDateString('fr-FR', { timeZone: 'Europe/Paris', dateStyle: 'full' }));
        this.fichiers.set(fichiers);
        this.journal.set(journal);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }
}
