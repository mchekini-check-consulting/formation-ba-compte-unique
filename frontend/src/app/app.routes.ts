import { Routes } from '@angular/router';
import { ConnexionPage } from './connexion-page/connexion-page';
import { Espace } from './espace/espace';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { PageInfo } from './page-info/page-info';

export const routes: Routes = [
  { path: '', component: Landing, pathMatch: 'full' },
  { path: 'connexion', component: ConnexionPage },
  { path: 'espace', component: Espace },
  { path: 'items', component: ItemsPage },
  { path: 'mentions-legales', component: PageInfo, data: { titre: 'Mentions légales' } },
  { path: 'protection-des-donnees', component: PageInfo, data: { titre: 'Politique de protection des données' } },
  { path: 'accessibilite', component: PageInfo, data: { titre: 'Accessibilité' } },
];
