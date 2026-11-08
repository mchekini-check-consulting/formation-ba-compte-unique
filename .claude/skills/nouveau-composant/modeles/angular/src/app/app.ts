import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: '__PREFIXE__-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly titre = '__TITRE__';
}
