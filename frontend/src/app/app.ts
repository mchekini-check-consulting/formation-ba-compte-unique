import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// Coquille de l'application : la page affichée dépend de l'adresse (app.routes.ts)
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
