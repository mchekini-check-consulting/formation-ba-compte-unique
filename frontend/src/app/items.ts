import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiBase } from './api';

// Ressource générique du backend (GET/POST /items)
export interface Item {
  id: number;
  nom: string;
  description: string | null;
  creeLe: string;
}

export interface ItemRequest {
  nom: string;
  description: string;
}

@Injectable({ providedIn: 'root' })
export class ItemsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${apiBase()}/items`;

  lister(): Observable<Item[]> {
    return this.http.get<Item[]>(this.url);
  }

  creer(item: ItemRequest): Observable<Item> {
    return this.http.post<Item>(this.url, item);
  }
}
