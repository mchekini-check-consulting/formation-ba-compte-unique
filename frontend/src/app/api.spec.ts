import { apiBase } from './api';

describe('apiBase', () => {
  it('pointe la passerelle de l\'environnement du frontend', () => {
    expect(apiBase('frontend.formation-ba-compte-unique.qualif.check-consulting.net')).toBe(
      'https://api.qualif.check-consulting.net/formation-ba-compte-unique/backend',
    );
  });

  it('utilise le proxy du serveur de dev en local', () => {
    expect(apiBase('localhost')).toBe('/api');
  });
});
