import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CarteCompte } from '../comptes/carte-compte';
import { Compte, ComptesService } from '../comptes/comptes';
import { ConnexionService } from '../connexion';

type EtatComptes = { lu: false } | { lu: true; erreur: boolean; comptes: Compte[] };

// Espace de la cliente connectée (CU-15) avec son compte courant (CU-19, maquette de l'espace
// client). Sans session valide, retour à la connexion. Le solde est relu à chaque ouverture.
@Component({
  selector: 'app-espace',
  imports: [CarteCompte, RouterLink],
  templateUrl: './espace.html',
  styleUrl: './espace.scss',
})
export class Espace {
  private readonly router = inject(Router);
  private readonly comptes = inject(ComptesService);
  private readonly etat = signal<EtatComptes>({ lu: false });

  protected readonly identifiant = signal('');
  protected readonly lu = computed(() => this.etat().lu);
  protected readonly compte = computed(() => {
    const etat = this.etat();
    return etat.lu && !etat.erreur ? (etat.comptes[0] ?? null) : null;
  });
  protected readonly sansCompte = computed(() => {
    const etat = this.etat();
    return etat.lu && !etat.erreur && etat.comptes.length === 0;
  });

  constructor() {
    inject(ConnexionService)
      .session()
      .subscribe({
        next: (session) => {
          this.identifiant.set(session.identifiant);
          this.lireComptes();
        },
        error: () => this.versConnexion(),
      });
  }

  private lireComptes(): void {
    this.comptes.mesComptes().subscribe({
      next: (comptes) => this.etat.set({ lu: true, erreur: false, comptes }),
      error: (erreur: HttpErrorResponse) => {
        if (erreur.status === 401) {
          this.versConnexion();
        } else {
          this.etat.set({ lu: true, erreur: true, comptes: [] });
        }
      },
    });
  }

  private versConnexion(): void {
    void this.router.navigateByUrl('/connexion');
  }
}
