import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { BandeauScenarios } from './outils-test/bandeau-scenarios';

// Coquille de l'application : la page affichée dépend de l'adresse (app.routes.ts). PO 28/11 : sur les pages /test/...
// (gardées par l'interrupteur « Outils de test »), le bandeau des scénarios actifs, chargé seulement à ce moment-là.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BandeauScenarios],
  template: `
    @if (pageDeTest()) {
      @defer {
        <app-bandeau-scenarios />
      }
    }
    <router-outlet />
  `,
})
export class App {
  private readonly router = inject(Router);
  protected readonly pageDeTest = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/test/')),
    ),
    { initialValue: false },
  );
}
