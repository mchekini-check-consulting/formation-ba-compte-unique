import { Route, Routes } from '@angular/router';
import { ActivationPage } from './activation/activation-page';
import { MesBeneficiaires } from './beneficiaires/mes-beneficiaires';
import { DetailCompte } from './comptes/detail-compte';
import { MesComptes } from './comptes/mes-comptes';
import { ConnexionPage } from './connexion-page/connexion-page';
import { DateMetierTest } from './date-metier-test/date-metier-test';
import { Espace } from './espace/espace';
import { JournalTest } from './journal-test/journal-test';
import { Landing } from './landing/landing';
import { MessagesTest } from './messages-test/messages-test';
import { PageInfo } from './page-info/page-info';
import { MotDePasseOublie } from './reinitialisation/mot-de-passe-oublie';
import { ReinitialisationPage } from './reinitialisation/reinitialisation-page';
import { connexionPublique, espaceProtege } from './session/garde-session';
import { VerificationTest } from './verification-test/verification-test';
import { VersementPage } from './versement/versement-page';

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
  { path: 'test/verification-beneficiaire', component: VerificationTest },
  // CU-380 : date métier ; modifiable en test seulement (le backend refuse en preprod et prod).
  { path: 'test/date-metier', component: DateMetierTest },
  // CU-381 : dépôt du fichier catalogue et lancement du batch ; absent en preprod et prod (le backend répond 404).
  { path: 'test/catalogue', loadComponent: () => import('./catalogue-test/catalogue-test').then((m) => m.CatalogueTest) },
  // CU-382 : boîte d'envoi Kafka, événements reçus et messages en erreur ; absent en preprod et prod (404 du backend).
  { path: 'test/evenements', loadComponent: () => import('./evenements-test/evenements-test').then((m) => m.EvenementsTest) },
  // CU-376 : passer des ordres sur des contrats de test et enregistrer des positions de test ; absent en preprod et prod (404 du backend).
  { path: 'test/ordres', loadComponent: () => import('./ordres-test/ordres-test').then((m) => m.OrdresTest) },
  // CU-27 : présentation du Livret A, ouverte à toutes ; son bouton d'ouverture mène à /livret-a (protégée).
  { path: 'epargne/livret-a', loadComponent: () => import('./livret-a/presentation-livret-a').then((m) => m.PresentationLivretA) },
  { path: 'mentions-legales', component: PageInfo, data: { titre: 'Mentions légales' } },
  { path: 'protection-des-donnees', component: PageInfo, data: { titre: 'Politique de protection des données' } },
  { path: 'accessibilite', component: PageInfo, data: { titre: 'Accessibilité' } },
];

const PAGES_DE_L_ESPACE: Routes = [
  { path: 'espace', component: Espace },
  { path: 'comptes', component: MesComptes },
  { path: 'comptes/:id', component: DetailCompte },
  // CU-11 : ouverte par les boutons « Faire un virement » de l'espace.
  { path: 'versement', component: VersementPage },
  // CU-146 : rubrique « Mes bénéficiaires ».
  { path: 'beneficiaires', component: MesBeneficiaires },
  // CU-31 : ouverture d'un Livret A (éligibilité et contrôle de multidétention).
  { path: 'livret-a', loadComponent: () => import('./livret-a/livret-a-page').then((m) => m.LivretAPage) },
  // CU-10 : profil client, requis avant une souscription de PER ou d'assurance vie.
  { path: 'profil', loadComponent: () => import('./profil/profil-page').then((m) => m.ProfilPage) },
  // CU-412 : supports en unités de compte disponibles pour le PER et l'assurance vie (?produit=PER|AV).
  { path: 'supports', loadComponent: () => import('./supports/supports-page').then((m) => m.SupportsPage) },
  // CU-311 / CU-331 : découvrir le PER ou l'assurance vie et lire ses documents avant de souscrire (?produit=PER|AV).
  { path: 'decouvrir', loadComponent: () => import('./decouverte/decouverte-page').then((m) => m.DecouvertePage) },
  // CU-312 / CU-330 : souscrire son PER ou son assurance vie (premier versement, clause bénéficiaire pour l'assurance
  // vie, signature par code SMS) après « Continuer » sur Découvrir (?produit=PER|AV).
  { path: 'souscrire', loadComponent: () => import('./souscription/souscription-page').then((m) => m.SouscriptionPage) },
  // CU-314 / CU-338 : choisir son mode de gestion (libre, pilotée, sous mandat) après « Continuer » (?produit=PER|AV).
  { path: 'gestion', loadComponent: () => import('./gestion/gestion-page').then((m) => m.GestionPage) },
];

const protegee = (route: Route): Route => ({ ...route, canActivate: [espaceProtege, ...(route.canActivate ?? [])] });

export const routes: Routes = [
  ...PAGES_PUBLIQUES,
  ...PAGES_DE_L_ESPACE.map(protegee),
  // Adresse inconnue : vers l'espace, donc vers la connexion sans session.
  { path: '**', redirectTo: 'espace' },
];
