import { apiBase } from './api';

describe('apiBase', () => {
  it("pointe la passerelle de l'environnement du frontend", () => {
    expect(apiBase('backend', 'frontend.formation-ba-compte-unique.qualif.check-consulting.net')).toBe(
      'https://gateway.formation-ba-compte-unique.qualif.check-consulting.net/backend',
    );
  });

  it('vise le backend demandé sur la passerelle', () => {
    expect(
      apiBase('bouchons-identite', 'frontend.formation-ba-compte-unique.dev.check-consulting.net'),
    ).toBe('https://gateway.formation-ba-compte-unique.dev.check-consulting.net/bouchons-identite');
  });

  it('utilise le relais du serveur de dev en local', () => {
    expect(apiBase('backend', 'localhost')).toBe('/api/backend');
    expect(apiBase('bouchons-identite', 'localhost')).toBe('/api/bouchons-identite');
  });
});
