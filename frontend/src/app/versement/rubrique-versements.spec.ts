import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ComptesService, RubriqueVersements as Rubrique } from '../comptes/comptes';
import { RubriqueVersements } from './rubrique-versements';

// CU-147 : virement exécuté vers un bénéficiaire, puis recrédit après un refus de la banque destinataire.
const VIREMENT_EXECUTE = {
  date: '2026-11-11T09:30:00Z',
  libelle: 'Loyer',
  emetteur: null,
  montantCentimes: 50000,
  sortant: true,
  beneficiaire: 'Test Cliente-B',
  simule: false,
  recredit: false,
  ibanMasque: '•••• 0202',
  statut: 'exécuté',
  motif: null,
  virementId: 21,
};

const RECREDIT = {
  ...VIREMENT_EXECUTE,
  date: '2026-11-11T10:30:00Z',
  sortant: false,
  recredit: true,
  statut: 'refusé',
  motif: 'Compte du bénéficiaire clôturé',
};

const RUBRIQUE: Rubrique = {
  iban: 'FR76 9999 9000 0199 0000 0000 105',
  mouvements: [
    {
      date: '2026-11-09T09:30:00Z',
      libelle: 'Premier versement',
      emetteur: 'Emetteur Test',
      montantCentimes: 1250,
      sortant: false,
      beneficiaire: null,
      simule: false,
    },
  ],
};

const VIREMENT_SORTANT_SIMULE = {
  date: '2026-11-10T09:30:00Z',
  libelle: 'Remboursement',
  emetteur: null,
  montantCentimes: 3000,
  sortant: true,
  beneficiaire: 'Beneficiaire Test',
  simule: true,
};

const refus = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

describe('RubriqueVersements', () => {
  let versements: ReturnType<typeof vi.fn<(id: string) => Observable<Rubrique>>>;

  beforeEach(() => {
    versements = vi.fn(() => of(RUBRIQUE));
    TestBed.configureTestingModule({
      providers: [
        { provide: ComptesService, useValue: { versements } },
      ],
    });
  });

  async function afficher() {
    const fixture = TestBed.createComponent(RubriqueVersements);
    fixture.componentRef.setInput('compteId', '5');
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    return { fixture, page };
  }

  it("affiche l'IBAN à communiquer et les mouvements du compte", async () => {
    const { page } = await afficher();
    expect(versements).toHaveBeenCalledWith('5');
    expect(page.querySelector('.iban')?.textContent).toBe('FR76 9999 9000 0199 0000 0000 105');
    const mouvement = page.querySelector('.mouvements li')?.textContent ?? '';
    expect(mouvement).toContain('09/11/2026');
    expect(mouvement).toContain('Premier versement');
    expect(mouvement).toContain('Emetteur Test');
    expect(page.querySelector('.montant')?.textContent).toContain('+12,50');
  });

  it('affiche un virement sortant simulé en débit, avec son bénéficiaire et la mention « simulé »', async () => {
    versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: [VIREMENT_SORTANT_SIMULE, ...RUBRIQUE.mouvements] }));
    const { page } = await afficher();
    const [sortant, entrant] = [...page.querySelectorAll('.mouvements li')];
    expect(sortant.textContent).toContain('Remboursement');
    expect(sortant.textContent).toContain('Vers Beneficiaire Test');
    expect(sortant.querySelector('.simule')?.textContent).toBe('simulé');
    expect(sortant.querySelector('.montant')?.textContent).toContain('−30,00');
    expect(sortant.querySelector('.montant')?.classList).toContain('debit');
    expect(entrant.querySelector('.simule')).toBeNull();
    expect(entrant.querySelector('.montant')?.textContent).toContain('+12,50');
  });

  it('D4 : aucune simulation dans la page (virement entrant, refus de la banque : sur /test/virements)', async () => {
    versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: [VIREMENT_EXECUTE] }));
    const { page } = await afficher();
    expect(page.textContent).not.toContain('Simuler');
    expect(page.textContent).not.toContain('Environnement de test');
    expect(page.querySelector('form, button.refus, a[href^="/test/"]')).toBeNull();
  });

  it("n'affiche pas d'IBAN pour un compte clôturé et indique l'absence de mouvement", async () => {
    versements.mockReturnValue(of({ iban: null, mouvements: [] }));
    const { page } = await afficher();
    expect(page.querySelector('.iban')).toBeNull();
    expect(page.textContent).toContain('Aucun mouvement pour le moment.');
  });

  it('affiche un message compréhensible si les versements ne sont pas lus', async () => {
    versements.mockReturnValue(refus(500, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')?.textContent).toBe('Vos versements sont momentanément indisponibles.');
  });

  it("laisse l'intercepteur gérer une session fermée (401)", async () => {
    versements.mockReturnValue(refus(401, null));
    const { page } = await afficher();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('CU-147 : affiche le virement exécuté avec son statut et l’IBAN masqué, et le recrédit avec son motif', async () => {
    versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: [RECREDIT, VIREMENT_EXECUTE] }));
    const { page } = await afficher();
    const [recredit, debit] = [...page.querySelectorAll('.mouvements li')];
    expect(debit.textContent).toContain('Vers Test Cliente-B · •••• 0202');
    expect(debit.querySelector('.statut')?.textContent).toBe('exécuté');
    expect(debit.querySelector('.montant')?.textContent).toContain('−500,00');
    expect(recredit.textContent).toContain('Recrédit : virement vers Test Cliente-B · •••• 0202 refusé (Compte du bénéficiaire clôturé)');
    expect(recredit.querySelector('.statut.refuse')?.textContent).toBe('refusé');
    expect(recredit.querySelector('.montant')?.textContent).toContain('+500,00');
    expect(page.querySelector('button.refus')).toBeNull();
  });

  describe('Historique des mouvements', () => {
    const SEPT = Array.from({ length: 7 }, (_, i) => ({
      ...RUBRIQUE.mouvements[0],
      date: `2026-11-0${9 - i}T09:30:00Z`,
      libelle: `Mouvement ${i + 1}`,
    }));

    async function avecSeptMouvements() {
      versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: SEPT }));
      return afficher();
    }

    it('affiche les 5 derniers mouvements sous « Historique des mouvements », avec « Voir tout » à droite', async () => {
      const { page } = await avecSeptMouvements();
      const entete = page.querySelector('.versements > .entete');
      expect(entete?.querySelector('h3')?.textContent).toBe('Historique des mouvements');
      expect(entete?.querySelector('button.voir-tout')?.textContent).toBe('Voir tout');
      const lignes = page.querySelectorAll('.versements > .mouvements li');
      expect(lignes).toHaveLength(5);
      expect(lignes[0].textContent).toContain('Mouvement 1');
      expect(lignes[4].textContent).toContain('Mouvement 5');
      expect(page.querySelector('[role=dialog]')).toBeNull();
    });

    it('« Voir tout » ouvre une fenêtre avec tout l’historique, fermée par ✕', async () => {
      const { fixture, page } = await avecSeptMouvements();
      (page.querySelector('button.voir-tout') as HTMLButtonElement).click();
      await fixture.whenStable();
      const fenetre = page.querySelector('[role=dialog]') as HTMLElement;
      expect(fenetre.getAttribute('aria-modal')).toBe('true');
      expect(fenetre.querySelector('h3')?.textContent).toBe('Historique des mouvements');
      expect(fenetre.querySelectorAll('.mouvements li')).toHaveLength(7);
      expect(fenetre.querySelector('.nombre')?.textContent).toContain('7 mouvements');
      expect(document.activeElement).toBe(fenetre.querySelector('button.fermer'));

      (fenetre.querySelector('button.fermer') as HTMLButtonElement).click();
      await fixture.whenStable();
      expect(page.querySelector('[role=dialog]')).toBeNull();
    });

    it('la fenêtre se ferme aussi par Échap ou par un clic à côté', async () => {
      const { fixture, page } = await avecSeptMouvements();
      const ouvrir = async () => {
        (page.querySelector('button.voir-tout') as HTMLButtonElement).click();
        await fixture.whenStable();
      };
      await ouvrir();
      page.querySelector('[role=dialog]')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      await fixture.whenStable();
      expect(page.querySelector('[role=dialog]')).toBeNull();

      await ouvrir();
      (page.querySelector('.voile') as HTMLElement).click();
      await fixture.whenStable();
      expect(page.querySelector('[role=dialog]')).toBeNull();
    });

    it('sans mouvement, ni « Voir tout » ni fenêtre', async () => {
      versements.mockReturnValue(of({ ...RUBRIQUE, mouvements: [] }));
      const { page } = await afficher();
      expect(page.querySelector('button.voir-tout')).toBeNull();
      expect(page.textContent).toContain('Aucun mouvement pour le moment.');
    });

    it('un seul mouvement : « 1 mouvement » au singulier dans la fenêtre', async () => {
      const { fixture, page } = await afficher();
      (page.querySelector('button.voir-tout') as HTMLButtonElement).click();
      await fixture.whenStable();
      expect(page.querySelector('[role=dialog] .nombre')?.textContent).toContain('1 mouvement,');
    });
  });

  it('CU-147 : relit les mouvements quand la page signale un virement exécuté', async () => {
    const { fixture } = await afficher();
    expect(versements).toHaveBeenCalledOnce();
    fixture.componentRef.setInput('actualisation', 1);
    await fixture.whenStable();
    expect(versements).toHaveBeenCalledTimes(2);
  });
});
