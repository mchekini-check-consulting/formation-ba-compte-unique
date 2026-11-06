import { Route, Routes } from '@angular/router';
import { ActivationPage } from './activation/activation-page';
import { DetailCompte } from './comptes/detail-compte';
import { MesComptes } from './comptes/mes-comptes';
import { ConnexionPage } from './connexion-page/connexion-page';
import { Espace } from './espace/espace';
import { JournalTest } from './journal-test/journal-test';
import { Landing } from './landing/landing';
import { MessagesTest } from './messages-test/messages-test';
import { PageInfo } from './page-info/page-info';
import { MotDePasseOublie } from './reinitialisation/mot-de-passe-oublie';
import { ReinitialisationPage } from './reinitialisation/reinitialisation-page';
import { connexionPublique, espaceProtege } from './session/garde-session';

// CU-170 : refus par défaut. Seules les pages de PAGES_PUBLIQUES s'ouvrent sans session ; toutes
// les autres, y compris une page ajoutée plus tard ou une adresse inconnue, exigent une session
// (CU-18 : sans session valide, retour à la connexion). Rendre une page publique est un choix
// explicite, ici.
export const PAGES_PUBLIQUES: Routes = [
  { path: '', component: Landing, pathMatch: 'full' },
  { path: 'connexion', component: ConnexionPage, canActivate: [connexionPublique] },
  { path: 'activation', component: ActivationPage },
  { path: 'mot-de-passe-oublie', component: MotDePasseOublie },
  { path: 'reinitialisation', component: ReinitialisationPage },
  // Pages de test : vides hors environnement de test (le backend répond 404).
  { path: 'test/emails', component: MessagesTest, data: { canal: 'emails' } },
  { path: 'test/sms', component: MessagesTest, data: { canal: 'sms' } },
  { path: 'test/journal-securite', component: JournalTest },
  { path: 'mentions-legales', component: PageInfo, data: { titre: 'Mentions légales' } },
  { path: 'protection-des-donnees', component: PageInfo, data: { titre: 'Politique de protection des données' } },
  { path: 'accessibilite', component: PageInfo, data: { titre: 'Accessibilité' } },
];

const PAGES_DE_L_ESPACE: Routes = [
  { path: 'espace', component: Espace },
  { path: 'comptes', component: MesComptes },
  { path: 'comptes/:id', component: DetailCompte },
];

const protegee = (route: Route): Route => ({ ...route, canActivate: [espaceProtege, ...(route.canActivate ?? [])] });

export const routes: Routes = [
  ...PAGES_PUBLIQUES,
  ...PAGES_DE_L_ESPACE.map(protegee),
  // Adresse inconnue : vers l'espace, donc vers la connexion sans session.
  { path: '**', redirectTo: 'espace' },
];
