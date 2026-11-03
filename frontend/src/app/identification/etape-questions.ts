import { Component, computed, output, signal } from '@angular/core';
import { ErreursReponses, MESSAGE_ANNULATION, Reponses, sansErreur, validerReponses } from './regles';

const QUESTIONS = [
  {
    cle: 'origineFonds',
    titre: "1. Quelle est l'origine principale des fonds que vous allez déposer ?",
    choix: ['Salaire', 'Épargne', 'Héritage', "Vente d'un bien", 'Autre'],
  },
  {
    cle: 'objetRelation',
    titre: "2. Quel est l'objet de votre relation avec la banque ?",
    choix: ['Gestion du quotidien', 'Épargne', 'Placement', 'Crédit'],
  },
  {
    cle: 'pep',
    titre: '3. Êtes-vous une personne politiquement exposée (PEP) ?',
    choix: ['Oui', 'Non'],
  },
  {
    cle: 'residenceEtranger',
    titre: '4. Avez-vous une résidence fiscale hors de France, ou êtes-vous citoyen américain ?',
    choix: ['Non', 'Oui'],
  },
] as const;

type CleQuestion = (typeof QUESTIONS)[number]['cle'];

// Écrans « Quelques questions » et « Vos données » (CUB-12, maquette écrans 4 et 5).
// Le bouton reste grisé tant qu'une réponse obligatoire manque ; un clic affiche alors le
// message sous chaque question concernée.
@Component({
  selector: 'app-etape-questions',
  templateUrl: './etape-questions.html',
})
export class EtapeQuestions {
  readonly valide = output<Reponses>();
  readonly annule = output<string>();
  readonly retour = output<void>();

  protected readonly questions = QUESTIONS;
  protected readonly reponses = signal<Reponses>({
    origineFonds: '',
    origineAutre: '',
    objetRelation: '',
    pep: '',
    residenceEtranger: '',
    residences: [{ pays: '', nif: '' }],
    consentementIdentite: false,
    consentementSituation: false,
  });
  protected readonly afficherErreurs = signal(false);
  protected readonly erreurs = computed<ErreursReponses>(() => validerReponses(this.reponses()));
  protected readonly complet = computed(() => sansErreur(this.erreurs()));

  protected repondre(cle: CleQuestion, valeur: string): void {
    this.reponses.update((r) => ({ ...r, [cle]: valeur }));
  }

  protected saisirAutre(event: Event): void {
    this.reponses.update((r) => ({ ...r, origineAutre: (event.target as HTMLInputElement).value }));
  }

  protected saisirResidence(index: number, champ: 'pays' | 'nif', event: Event): void {
    const valeur = (event.target as HTMLInputElement).value;
    this.reponses.update((r) => ({
      ...r,
      residences: r.residences.map((l, i) => (i === index ? { ...l, [champ]: valeur } : l)),
    }));
  }

  protected ajouterResidence(): void {
    this.reponses.update((r) => ({ ...r, residences: [...r.residences, { pays: '', nif: '' }] }));
  }

  protected retirerResidence(index: number): void {
    this.reponses.update((r) => ({ ...r, residences: r.residences.filter((_, i) => i !== index) }));
  }

  protected consentir(cle: 'consentementIdentite' | 'consentementSituation', event: Event): void {
    this.reponses.update((r) => ({ ...r, [cle]: (event.target as HTMLInputElement).checked }));
  }

  protected erreurQuestion(cle: CleQuestion): string | undefined {
    return this.afficherErreurs() ? this.erreurs()[cle] : undefined;
  }

  protected continuer(): void {
    this.afficherErreurs.set(true);
    if (!this.complet()) {
      return;
    }
    if (this.reponses().pep === 'Oui') {
      this.annule.emit(MESSAGE_ANNULATION);
      return;
    }
    this.valide.emit(this.reponses());
  }
}
