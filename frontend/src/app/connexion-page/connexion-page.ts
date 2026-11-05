import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AccesLayout } from '../acces/acces-layout';
import { ConnexionService, RefusConnexion } from '../connexion';

const MESSAGE_INDISPONIBLE = 'Connexion impossible pour le moment, réessayez plus tard.';

// Écran « Me connecter » (CU-15), d'après la maquette « Connexion Hi-Tech Bank ».
@Component({
  selector: 'app-connexion-page',
  imports: [AccesLayout, RouterLink],
  templateUrl: './connexion-page.html',
})
export class ConnexionPage {
  private readonly api = inject(ConnexionService);
  private readonly router = inject(Router);

  protected readonly erreur = signal('');
  protected readonly invitation = signal('');
  protected readonly enCours = signal(false);
  protected readonly visible = signal(false);
  protected readonly points: [string, string][] = [
    ['Connexion simple', 'identifiant et mot de passe, sans code à recevoir.'],
    ['Session sécurisée', "fermeture automatique après 10 minutes d'inactivité."],
    ['Vos données protégées', 'vous seule accédez à vos comptes.'],
  ];

  protected connecter(event: Event, identifiant: HTMLInputElement, motDePasse: HTMLInputElement): void {
    event.preventDefault();
    this.erreur.set('');
    this.invitation.set('');
    this.enCours.set(true);
    this.api.connecter(identifiant.value.trim(), motDePasse.value).subscribe({
      next: () => void this.router.navigateByUrl('/espace'),
      error: (e: HttpErrorResponse) => {
        this.enCours.set(false);
        motDePasse.value = '';
        const refus: RefusConnexion | null = e.status === 401 ? e.error : null;
        this.erreur.set(refus?.message ?? MESSAGE_INDISPONIBLE);
        this.invitation.set(refus?.invitation ?? '');
      },
    });
  }
}
