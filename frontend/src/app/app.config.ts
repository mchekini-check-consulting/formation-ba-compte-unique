import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { sessionFermee } from './session/session-fermee';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Appels aux backends ; CU-169 : un 401 renvoie vers la connexion (« Session expirée »).
    provideHttpClient(withFetch(), withInterceptors([sessionFermee]))
  ]
};
