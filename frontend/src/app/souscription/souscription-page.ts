import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BoutonFonctionnalite } from '../fonctionnalites/bouton-fonctionnalite';
import { PROFILS } from '../gestion/gestion';
import { BrouillonSouscription } from '../tunnel/brouillon';
import { Tunnel } from '../tunnel/tunnel';
import { Observable } from 'rxjs';
import { eurosAuCentime } from '../livret-a/parametres';
import { Beneficiaire, EtatSouscription, ORIGINES, OrigineFonds, Produit, SouscriptionService } from './souscription';

type Etat = 'chargement' | 'affiche' | 'indisponible';

const TEXTES: Record<Produit, { titre: string; nom: string; versement: string; minimum: string; compartiment: string }> = {
  PER: {
    titre: 'Souscrire mon PER',
    nom: 'le PER',
    versement: 'Mon premier versement',
    minimum: 'Le premier versement minimum est de 100 €',
    compartiment: ', sur le compartiment 1 (versements volontaires)',
  },
  AV: {
    titre: 'Souscrire mon assurance vie',
    nom: 'l’assurance vie',
    versement: 'Mon versement initial',
    minimum: 'Le versement initial minimum est de 100 €',
    compartiment: '',
  },
};

const QUOTES_PARTS = 'Les quotes-parts doivent totaliser 100 %';

/** Ligne de la clause en cours de saisie (quote-part en texte, comme saisie). */
interface Ligne {
  nom: string;
  prenom: string;
  dateNaissance: string;
  quotePart: string;
}

/** Montant ou pourcentage saisi (virgule ou point, espaces acceptés) ; NaN si la saisie n'est pas un nombre. */
export function lireMontant(saisie: string): number {
  const texte = saisie.replace(/\s/g, '').replace(',', '.');
  return /^\d+(\.\d{1,2})?$/.test(texte) ? Number(texte) : Number.NaN;
}

const ligneComplete = (l: Ligne) =>
  l.nom.trim() !== '' && l.prenom.trim() !== '' && l.dateNaissance !== '' && lireMontant(l.quotePart) > 0;

// CU-312 (PER-04) et CU-330 (AV-01) : souscrire son PER ou son assurance vie (?produit=PER|AV) après « Continuer » sur
// la page Découvrir. Profil non validé : renvoi vers le questionnaire. Premier versement (100 € au moins, depuis le
// compte courant) et origine des fonds, clause bénéficiaire facultative pour l'assurance vie (quotes-parts à 100 %),
// puis signature par code SMS (valable 5 minutes ; 3 codes erronés : 30 minutes de blocage). Refus : message du backend.
@Component({
  selector: 'app-souscription-page',
  imports: [BoutonFonctionnalite, RouterLink, Tunnel],
  template: `
    <div class="page souscription">
      <app-tunnel [produit]="produit" [etape]="etapeTunnel()" [etat]="donnees()" [autonome]="false">
      <h1>{{ textes.titre }}</h1>

      @if (message()) {
        <p class="succes" role="status">{{ message() }}</p>
      }
      @if (erreur()) {
        <p class="erreur" role="alert">{{ erreur() }}</p>
      }

      @switch (etat()) {
        @case ('chargement') {
          <p>Chargement…</p>
        }
        @case ('indisponible') {
          <p class="erreur" role="alert">La souscription est momentanément indisponible. Réessayez plus tard.</p>
        }
        @case ('affiche') {
          @if (donnees()!.etape === 'SIGNEE') {
            <!-- Lot 5, étape 5 : confirmation (PER-04, AV-01, TECH-08). -->
            <section class="carte" data-test="signee">
              <h2>Votre contrat est ouvert</h2>
              <p>Contrat n° {{ donnees()!.numeroContrat }}, en gestion pilotée.</p>
              @if (donnees()!.montant !== null) {
                <p data-test="investi">Votre versement de {{ eurosAuCentime(donnees()!.montant!) }} sera investi à la prochaine valeur liquidative.</p>
              }
              <p>Vos documents : <a routerLink="/decouvrir" [queryParams]="{ produit }">document d’informations clés et fiche produit</a>.</p>
              <p class="boutons">
                <a class="btn p" data-test="choisir-gestion" routerLink="/gestion" [queryParams]="{ produit, tunnel: 1 }">Choisir mon mode de gestion</a>
                <a class="btn o" data-test="voir-contrat" routerLink="/espace" fragment="synthese">Voir mon contrat</a>
              </p>
            </section>
          } @else if (donnees()!.profil !== 'VALIDE') {
            <p class="carte" data-test="profil-requis">
              Votre profil investisseur doit être complété ou revalidé avant de souscrire.
              <a routerLink="/profil">Compléter mon profil</a>
            </p>
          } @else if (donnees()!.etape === 'VERSEMENT' || modification()) {
            <form class="carte" data-test="versement" (submit)="continuer($event)">
              <h2>{{ textes.versement }}</h2>
              <p>Prélevé sur votre compte courant Hi-Tech Bank à la signature{{ textes.compartiment }}.</p>
              <label>Montant (€)
                <input data-test="montant" inputmode="decimal" [value]="montant()" (input)="montant.set(valeur($event))" />
              </label>
              @if (sousLeMinimum()) {
                <p class="erreur" data-test="minimum">{{ textes.minimum }}</p>
              }
              <fieldset>
                <legend>Origine des fonds</legend>
                @for (origine of origines; track origine.code) {
                  <label class="choix">
                    <input type="radio" name="origine" [value]="origine.code" [checked]="origineFonds() === origine.code"
                      (change)="origineFonds.set(origine.code)" />
                    {{ origine.libelle }}
                  </label>
                }
              </fieldset>
              @if (origineFonds() === 'AUTRE') {
                <label>Précisez
                  <input data-test="precision" maxlength="200" [value]="precision()" (input)="precision.set(valeur($event))" />
                </label>
              }
              @if (produit === 'AV') {
                <fieldset data-test="clause">
                  <legend>Bénéficiaires en cas de décès (facultatif)</legend>
                  @for (ligne of lignes(); track $index; let i = $index) {
                    <div class="beneficiaire" data-test="beneficiaire">
                      <label>Nom <input maxlength="70" [value]="ligne.nom" (input)="changer(i, 'nom', $event)" /></label>
                      <label>Prénom <input maxlength="70" [value]="ligne.prenom" (input)="changer(i, 'prenom', $event)" /></label>
                      <label>Date de naissance
                        <input type="date" [value]="ligne.dateNaissance" (input)="changer(i, 'dateNaissance', $event)" />
                      </label>
                      <label>Quote-part (%)
                        <input inputmode="decimal" [value]="ligne.quotePart" (input)="changer(i, 'quotePart', $event)" />
                      </label>
                      <button class="lien" type="button" (click)="retirer(i)">Retirer</button>
                    </div>
                  }
                  <button class="btn o" type="button" data-test="ajouter" (click)="ajouter()">Ajouter un bénéficiaire</button>
                  @if (lignes().length === 0) {
                    <p>Sans bénéficiaire désigné, le capital entrera dans votre succession.</p>
                  } @else if (!totalCent()) {
                    <p class="erreur" data-test="quotes-parts">{{ quotesParts }}</p>
                  }
                </fieldset>
              }
              @if (donnees()!.profilGestionParDefaut; as profil) {
                <!-- Lot 5 (D5) : le contrat naît à la signature, en gestion pilotée ; le mode se choisit juste après. -->
                <p class="info" data-test="gestion-par-defaut">Votre contrat démarrera en gestion pilotée, profil {{ libelleProfil(profil) }}. Vous pourrez choisir un autre mode de gestion juste après l’ouverture.</p>
              }
              <p>
                <app-bouton-fonctionnalite cle="versements-programmes" classe="lien">Programmer des versements</app-bouton-fonctionnalite>
              </p>
              <button class="btn p" type="submit" data-test="continuer" [disabled]="!peutContinuer() || envoi()">Continuer</button>
            </form>
          } @else {
            <section class="carte" data-test="signature">
              <h2>Signer mon contrat</h2>
              <p>{{ textes.versement }} : <strong>{{ eurosAuCentime(donnees()!.montant!) }}</strong>, {{ libelleOrigine() }}.
                <button class="lien" type="button" data-test="modifier" (click)="modifier()">Modifier</button></p>
              @if (produit === 'AV') {
                <p data-test="recap-clause">{{ resumeClause() }}
                  <button class="lien" type="button" data-test="modifier-clause" (click)="modifier()">Modifier</button></p>
              }
              <p>Un code à 6 chiffres vous est envoyé par SMS ; il est valable 5 minutes.</p>
              <button class="btn o" type="button" data-test="code" [disabled]="envoi()" (click)="envoyerCode()">
                {{ donnees()!.codeEnvoyeLe ? 'Recevoir un nouveau code' : 'Recevoir mon code par SMS' }}
              </button>
              @if (donnees()!.codeEnvoyeLe) {
                <form class="code" data-test="formulaire-code" (submit)="signer($event)">
                  <label>Code reçu par SMS
                    <input inputmode="numeric" maxlength="6" autocomplete="one-time-code" [value]="code()"
                      (input)="code.set(valeur($event))" />
                  </label>
                  <button class="btn p" type="submit" [disabled]="code().length !== 6 || envoi()">Signer</button>
                </form>
              }
            </section>
          }
        }
      }
      </app-tunnel>
    </div>
  `,
  styles: `
    .info { padding: 12px 14px; border-radius: 12px; background: #eef6ff; border: 1px solid #c7defa; }
    .boutons { display: flex; flex-wrap: wrap; gap: 12px; }
    label { display: grid; gap: 4px; font-weight: 700; }
    .choix { display: flex; gap: 8px; align-items: center; font-weight: 400; }
    .beneficiaire { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 8px; align-items: end;
      padding: 8px; border: 1px solid #eef1f5; border-radius: 8px; }
    .code { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
  `,
})
export class SouscriptionPage {
  private readonly service = inject(SouscriptionService);
  private readonly brouillons = inject(BrouillonSouscription);
  private readonly router = inject(Router);
  protected readonly produit: Produit =
    inject(ActivatedRoute).snapshot.queryParamMap.get('produit') === 'AV' ? 'AV' : 'PER';
  protected readonly textes = TEXTES[this.produit];
  protected readonly origines = ORIGINES;
  protected readonly quotesParts = QUOTES_PARTS;
  protected readonly eurosAuCentime = eurosAuCentime;
  /** Lot 5 : étape du tunnel : 3 versement (ou modification), 4 récapitulatif et signature, 5 confirmation. */
  protected readonly etapeTunnel = computed(() => {
    const etape = this.donnees()?.etape;
    return etape === 'SIGNEE' ? 5 : etape === 'SIGNATURE' && !this.modification() ? 4 : 3;
  });
  protected readonly etat = signal<Etat>('chargement');
  protected readonly donnees = signal<EtatSouscription | null>(null);
  protected readonly modification = signal(false);
  protected readonly montant = signal('');
  protected readonly origineFonds = signal<OrigineFonds | null>(null);
  protected readonly precision = signal('');
  protected readonly lignes = signal<Ligne[]>([]);
  protected readonly code = signal('');
  protected readonly envoi = signal(false);
  protected readonly message = signal('');
  protected readonly erreur = signal('');
  protected readonly sousLeMinimum = computed(() => {
    const montant = lireMontant(this.montant());
    return this.montant().trim() !== '' && (Number.isNaN(montant) || montant < (this.donnees()?.minimum ?? 100));
  });
  // Centièmes entiers : 33,33 + 33,33 + 33,34 totalise exactement 100.
  protected readonly totalCent = computed(() =>
    this.lignes().reduce((total, l) => total + Math.round(lireMontant(l.quotePart) * 100), 0) === 10000);
  protected readonly peutContinuer = computed(() =>
    this.montant().trim() !== '' && !this.sousLeMinimum() && this.origineFonds() !== null
    && (this.origineFonds() !== 'AUTRE' || this.precision().trim() !== '')
    && (this.lignes().length === 0 || (this.lignes().every(ligneComplete) && this.totalCent())));
  protected readonly libelleOrigine = computed(() => {
    const d = this.donnees();
    const libelle = ORIGINES.find((o) => o.code === d?.origineFonds)?.libelle.replace(' (à préciser)', '') ?? '';
    return d?.originePrecision ? `${libelle} : ${d.originePrecision}` : libelle;
  });
  protected readonly resumeClause = computed(() => {
    const beneficiaires = this.donnees()?.beneficiaires ?? [];
    return beneficiaires.length === 0
      ? 'Aucun bénéficiaire désigné : le capital entrera dans votre succession.'
      : `Bénéficiaires : ${beneficiaires.map((b) => `${b.prenom} ${b.nom} (${String(b.quotePartPct).replace('.', ',')} %)`).join(', ')}.`;
  });

  constructor() {
    effect(() => {
      if (this.donnees()?.etape === 'VERSEMENT' || this.modification()) {
        this.brouillons.garder(this.produit, { montant: this.montant(), origineFonds: this.origineFonds(), precision: this.precision(), lignes: this.lignes() });
      }
    });
    this.service.etat(this.produit).subscribe({
      next: (donnees) => {
        if (this.etapeSautee(donnees)) {
          return;
        }
        this.afficher(donnees);
        this.etat.set('affiche');
      },
      error: () => this.etat.set('indisponible'),
    });
  }

  /**
   * Adresse /souscrire tapée sans avoir fait les étapes précédentes : retour à la bonne étape, le profil (CU-10,
   * « redirigée vers le questionnaire ») puis les documents (PER-02, AV-02). Le backend refuse aussi la souscription.
   */
  private etapeSautee(donnees: EtatSouscription): boolean {
    if (donnees.etape === 'SIGNEE') {
      return false;
    }
    if (donnees.profil !== 'VALIDE') {
      void this.router.navigate(['/profil'], { replaceUrl: true });
      return true;
    }
    if (!donnees.documentsLus) {
      void this.router.navigate(['/decouvrir'], { queryParams: { produit: this.produit }, replaceUrl: true });
      return true;
    }
    return false;
  }

  protected libelleProfil(profil: keyof typeof PROFILS): string {
    return PROFILS[profil];
  }

  protected valeur(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected ajouter(): void {
    this.lignes.update((l) => [...l, { nom: '', prenom: '', dateNaissance: '', quotePart: '' }]);
  }

  protected retirer(index: number): void {
    this.lignes.update((l) => l.filter((_, i) => i !== index));
  }

  protected changer(index: number, champ: keyof Ligne, event: Event): void {
    const valeur = this.valeur(event);
    this.lignes.update((l) => l.map((ligne, i) => (i === index ? { ...ligne, [champ]: valeur } : ligne)));
  }

  protected modifier(): void {
    this.modification.set(true);
    this.message.set('');
    this.erreur.set('');
  }

  protected continuer(event: Event): void {
    event.preventDefault();
    if (!this.peutContinuer()) {
      return;
    }
    const beneficiaires: Beneficiaire[] = this.lignes().map((l) => ({
      nom: l.nom.trim(), prenom: l.prenom.trim(), dateNaissance: l.dateNaissance, quotePartPct: lireMontant(l.quotePart),
    }));
    this.appeler(this.service.versement(this.produit, {
      montant: lireMontant(this.montant()), origineFonds: this.origineFonds()!, originePrecision: this.precision().trim(),
      beneficiaires,
    }), '');
  }

  protected envoyerCode(): void {
    this.code.set('');
    this.appeler(this.service.envoyerCode(this.produit), 'Un code vous a été envoyé par SMS.');
  }

  protected signer(event: Event): void {
    event.preventDefault();
    this.appeler(this.service.signer(this.produit, this.code()),
      'Votre contrat est souscrit et votre premier versement est enregistré.');
  }

  private appeler(appel: Observable<EtatSouscription>, texte: string): void {
    this.envoi.set(true);
    this.erreur.set('');
    this.message.set('');
    appel.subscribe({
      next: (donnees) => {
        this.afficher(donnees);
        this.envoi.set(false);
        this.message.set(texte);
      },
      error: (refus: HttpErrorResponse) => {
        this.envoi.set(false);
        this.erreur.set(refus.error?.message ?? 'Votre demande n’a pas pu être enregistrée. Réessayez.');
      },
    });
  }

  private afficher(donnees: EtatSouscription): void {
    this.donnees.set(donnees);
    this.modification.set(false);
    const brouillon = this.brouillons.lire(this.produit);
    if (donnees.etape === 'VERSEMENT' && donnees.montant === null && brouillon) {
      // D6 : saisie reprise après un retour arrière, tant que la session est ouverte.
      this.montant.set(brouillon.montant);
      this.origineFonds.set(brouillon.origineFonds);
      this.precision.set(brouillon.precision);
      this.lignes.set(brouillon.lignes);
    } else if (donnees.montant !== null) {
      this.montant.set(String(donnees.montant).replace('.', ','));
      this.origineFonds.set(donnees.origineFonds);
      this.precision.set(donnees.originePrecision ?? '');
      this.lignes.set((donnees.beneficiaires ?? []).map((b) => ({
        nom: b.nom, prenom: b.prenom, dateNaissance: b.dateNaissance, quotePart: String(b.quotePartPct).replace('.', ','),
      })));
    }
  }
}
