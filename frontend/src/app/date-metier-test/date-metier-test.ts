import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChangementDateMetier, DateMetierService, EtatDateMetier, afficherDate } from './date-metier';

type Etat = 'chargement' | 'affiche' | 'indisponible';

// CU-380 (TECH-12) : page /test/date-metier. La testeuse avance la date métier (heure de Paris) pour
// vérifier les délais sans attendre ; l'heure continue ensuite d'avancer. Chaque changement est tracé avec
// le nom saisi. En preprod et prod, la date est l'heure réelle et le backend refuse tout changement.
@Component({
  selector: 'app-date-metier-test',
  imports: [RouterLink],
  template: `
    <main class="date-metier-test">
      <a routerLink="/">← Retour au site</a>
      <h1>Date métier (test)</h1>
      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="vide" role="status">Date métier indisponible pour le moment. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          <p class="date" data-test="date-metier">
            {{ afficher(dateMetier()!.dateMetier) }}
            <span class="badge">{{ dateMetier()!.simulee ? 'Date simulée' : 'Date réelle' }}</span>
          </p>
          @if (!dateMetier()!.modifiable) {
            <p class="vide" role="status">La date métier ne peut pas être modifiée dans cet environnement.</p>
          } @else {
            <form (submit)="changer($event, nouvelleDate.value, auteur.value)">
              <label for="nouvelle-date">Nouvelle date et heure (Paris)</label>
              <input #nouvelleDate id="nouvelle-date" name="nouvelleDate" type="datetime-local" required />
              <label for="auteur">Votre nom</label>
              <input #auteur id="auteur" name="auteur" maxlength="100" required />
              <div class="actions">
                <button type="submit">Changer la date</button>
                <button type="button" (click)="revenir(auteur.value)">Revenir à la date réelle</button>
              </div>
            </form>
            @if (erreur()) {
              <p class="erreur" role="alert">{{ erreur() }}</p>
            }
            <h2>Tâche de revalidation des profils</h2>
            <p class="vide">Chaque nuit (date métier), les profils dont la date de revalidation est dépassée passent « à revalider » et l’événement est publié. Pour ne pas attendre :</p>
            <button type="button" data-test="revalidation" (click)="lancerRevalidation()">Lancer la tâche maintenant</button>
            @if (resultatRevalidation()) {
              <p class="succes" role="status" data-test="resultat-revalidation">{{ resultatRevalidation() }}</p>
            }
            <h2>Journal des changements</h2>
            @if (journal().length === 0) {
              <p class="vide">Aucun changement.</p>
            } @else {
              <div class="tableau">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Changé le (heure réelle)</th>
                      <th scope="col">Par</th>
                      <th scope="col">Ancienne date</th>
                      <th scope="col">Nouvelle date</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (ligne of journal(); track $index) {
                      <tr>
                        <td>{{ afficher(ligne.changeLe) }}</td>
                        <td>{{ ligne.auteur }}</td>
                        <td>{{ afficher(ligne.ancienneDate) }}</td>
                        <td>{{ afficher(ligne.nouvelleDate) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          }
        }
      }
    </main>
  `,
  styles: `
    .date-metier-test { max-width: 1100px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    h1 { color: #0b2545; margin: 24px 0 12px; }
    h2 { color: #0b2545; margin: 32px 0 12px; font-size: 20px; }
    .date { font-size: 22px; font-weight: 700; display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; }
    .badge { font-size: 13px; padding: 4px 10px; border-radius: 999px; background: #f4f6fa; color: #5d6887; }
    .vide { color: #5d6887; line-height: 1.6; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
    form { display: grid; gap: 8px; max-width: 420px; margin: 16px 0; }
    label { font-weight: 700; }
    input { padding: 9px 12px; border: 1px solid #d9dee6; border-radius: 10px; min-width: 0; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
    button { padding: 10px 18px; border-radius: 10px; border: 1px solid #0b2545; background: #fff; color: #0b2545; font-weight: 700; cursor: pointer; }
    button[type="submit"] { background: #0b2545; color: #fff; }
    .tableau { overflow-x: auto; border: 1px solid #d9dee6; border-radius: 12px; background: #fff; }
    table { border-collapse: collapse; width: 100%; font-size: 14px; }
    th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eef1f5; white-space: nowrap; }
    th { background: #f4f6fa; }
  `,
})
export class DateMetierTest {
  private readonly service = inject(DateMetierService);
  protected readonly etat = signal<Etat>('chargement');
  protected readonly dateMetier = signal<EtatDateMetier | null>(null);
  protected readonly journal = signal<ChangementDateMetier[]>([]);
  protected readonly erreur = signal<string | null>(null);
  protected readonly resultatRevalidation = signal<string | null>(null);
  protected readonly afficher = afficherDate;

  constructor() {
    this.charger();
  }

  protected changer(event: Event, date: string, auteur: string): void {
    event.preventDefault();
    if (!date) {
      this.erreur.set('Choisissez la nouvelle date et heure.');
      return;
    }
    this.service.changer(date, auteur).subscribe(this.apresChangement());
  }

  protected revenir(auteur: string): void {
    this.service.revenirALaDateReelle(auteur).subscribe(this.apresChangement());
  }

  /** CU-415 : lance la tâche quotidienne de revalidation des profils, sans attendre la nuit. */
  protected lancerRevalidation(): void {
    this.erreur.set(null);
    this.resultatRevalidation.set(null);
    this.service.lancerRevalidation().subscribe({
      next: ({ profilsARevalider }) =>
        this.resultatRevalidation.set(`Tâche lancée : ${profilsARevalider} profil(s) passé(s) « à revalider ».`),
      error: () => this.erreur.set('La tâche n’a pas pu être lancée. Réessayez.'),
    });
  }

  private apresChangement() {
    return {
      next: () => {
        this.erreur.set(null);
        this.charger();
      },
      error: (refus: HttpErrorResponse) =>
        this.erreur.set(refus.error?.message ?? 'Le changement de date n’a pas pu être enregistré. Réessayez.'),
    };
  }

  private charger(): void {
    this.service.lire().subscribe({
      next: (etat) => {
        this.dateMetier.set(etat);
        this.etat.set('affiche');
        if (etat.modifiable) {
          this.service.journal().subscribe({ next: (lignes) => this.journal.set(lignes), error: () => this.journal.set([]) });
        }
      },
      error: () => this.etat.set('indisponible'),
    });
  }
}
