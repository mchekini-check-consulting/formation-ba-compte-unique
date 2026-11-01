import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Item, ItemsService } from '../items';

// Liste des items du backend (GET) et formulaire d'ajout (POST)
@Component({
  selector: 'app-items-page',
  styleUrl: './items-page.scss',
  templateUrl: './items-page.html',
})
export class ItemsPage {
  private readonly api = inject(ItemsService);
  protected readonly items = signal<Item[]>([]);
  protected readonly erreur = signal('');

  constructor() {
    this.api.lister().subscribe({
      next: (items) => this.items.set(items),
      error: (e: HttpErrorResponse) => this.erreur.set(`Chargement impossible (${e.status})`),
    });
  }

  protected ajouter(event: Event, nom: HTMLInputElement, description: HTMLInputElement): void {
    event.preventDefault();
    this.erreur.set('');
    this.api.creer({ nom: nom.value, description: description.value }).subscribe({
      next: (item) => {
        this.items.update((items) => [...items, item]);
        nom.value = '';
        description.value = '';
      },
      error: (e: HttpErrorResponse) => this.erreur.set(`Ajout impossible (${e.status})`),
    });
  }
}
