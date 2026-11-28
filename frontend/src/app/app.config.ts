import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { sessionFermee } from './session/session-fermee';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Liens vers une section de page (ex. bouchons du Livret A sur /test/scenarios#livret-a).
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    // Appels aux backends ; CU-169 : un 401 renvoie vers la connexion (« Session expirée »).
    provideHttpClient(withFetch(), withInterceptors([sessionFermee]))
  ]
};
