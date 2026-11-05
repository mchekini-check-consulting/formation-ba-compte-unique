import { Routes } from '@angular/router';
import { ActivationPage } from './activation/activation-page';
import { DetailCompte } from './comptes/detail-compte';
import { MesComptes } from './comptes/mes-comptes';
import { ConnexionPage } from './connexion-page/connexion-page';
import { Espace } from './espace/espace';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';
import { MessagesTest } from './messages-test/messages-test';
import { PageInfo } from './page-info/page-info';

export const routes: Routes = [
  { path: '', component: Landing, pathMatch: 'full' },
  { path: 'connexion', component: ConnexionPage },
  { path: 'activation', component: ActivationPage },
  { path: 'espace', component: Espace },
  { path: 'comptes', component: MesComptes },
  { path: 'comptes/:id', component: DetailCompte },
  { path: 'items', component: ItemsPage },
  { path: 'test/emails', component: MessagesTest, data: { canal: 'emails' } },
  { path: 'test/sms', component: MessagesTest, data: { canal: 'sms' } },
  { path: 'mentions-legales', component: PageInfo, data: { titre: 'Mentions légales' } },
  { path: 'protection-des-donnees', component: PageInfo, data: { titre: 'Politique de protection des données' } },
  { path: 'accessibilite', component: PageInfo, data: { titre: 'Accessibilité' } },
];
