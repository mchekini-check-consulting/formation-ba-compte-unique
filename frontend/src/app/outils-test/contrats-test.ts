import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GestionService, Produit } from '../gestion/gestion';

// CU-314 / CU-338 : page /test/contrats (local, dev et qualif). Crée un contrat de test PER ou assurance vie pour la
// cliente connectée dans ce navigateur, sans passer par la souscription : il démarre en gestion pilotée, profil
// équilibré (prudent si le profil investisseur est prudent). Déplacé hors de « Choisir mon mode de gestion » : aucun
// élément de test n'est visible dans le parcours de la cliente. Le backend refuse hors environnement de test.
@Component({
  selector: 'app-contrats-test',
  imports: [RouterLink],
  template: `
    <main class="contrats-test">
      <a routerLink="/test/ordres">← Ordres (test)</a>
      <h1>Contrats de test</h1>
      <p>
        Pour la cliente connectée dans ce navigateur : le contrat démarre en gestion pilotée, profil équilibré (prudent
        si son profil investisseur est prudent). Ses encours se saisissent sur la <a routerLink="/test/ordres">page des ordres</a>.
      </p>
      @if (message()) {
        <p class="succes" role="status">{{ message() }}</p>
      }
      @if (erreur()) {
        <p class="erreur" role="alert">{{ erreur() }}</p>
      }
      <p class="actions">
        <button type="button" data-test="creer-PER" [disabled]="envoi()" (click)="creer('PER')">Créer un contrat de test PER</button>
        <button type="button" data-test="creer-AV" [disabled]="envoi()" (click)="creer('AV')">Créer un contrat de test assurance vie</button>
      </p>
    </main>
  `,
  styles: `
    .contrats-test { max-width: 900px; margin: 0 auto; padding: 40px 16px; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #0f1b33; }
    a { color: #0b2545; font-weight: 700; }
    .actions { display: flex; gap: 12px; flex-wrap: wrap; }
    button { padding: 10px 18px; border-radius: 999px; border: 0; background: #0b2545; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .erreur { color: #b42318; font-weight: 700; }
    .succes { color: #067647; font-weight: 700; }
  `,
})
export class ContratsTest {
  private readonly service = inject(GestionService);
  protected readonly envoi = signal(false);
  protected readonly message = signal('');
  protected readonly erreur = signal('');

  protected creer(produit: Produit): void {
    this.envoi.set(true);
    this.message.set('');
    this.erreur.set('');
    this.service.creerContratDeTest(produit).subscribe({
      next: (etat) => {
        this.envoi.set(false);
        this.message.set(`Contrat de test ${etat.contrat?.numeroContrat ?? ''} créé, en gestion pilotée.`);
      },
      error: (refus: HttpErrorResponse) => {
        this.envoi.set(false);
        this.erreur.set((refus.error as { message?: string } | null)?.message ?? 'Création impossible : connectez-vous d’abord, en environnement de test.');
      },
    });
  }
}
