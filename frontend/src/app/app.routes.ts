import { Routes } from '@angular/router';
import { ItemsPage } from './items-page/items-page';
import { Landing } from './landing/landing';

export const routes: Routes = [
  { path: '', component: Landing, pathMatch: 'full' },
  { path: 'items', component: ItemsPage },
];
