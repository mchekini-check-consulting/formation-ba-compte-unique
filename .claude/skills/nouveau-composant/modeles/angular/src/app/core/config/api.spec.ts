import { apiBase } from './api';

describe('apiBase', () => {
  it("pointe le gateway de l'environnement du frontend", () => {
    expect(apiBase('contrats', '__SLUG__.__DEPOT__.qualif.check-consulting.net')).toBe(
      'https://gateway.__DEPOT__.qualif.check-consulting.net/contrats',
    );
  });

  it('utilise le relais du serveur de dev en local', () => {
    expect(apiBase('contrats', 'localhost')).toBe('/api/contrats');
  });

  it("prend par défaut l'adresse de la page", () => {
    // Les tests tournent sur localhost : relais local
    expect(apiBase('contrats')).toBe('/api/contrats');
  });
});
