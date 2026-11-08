import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiBase, appeler } from './api.js'

describe('apiBase', () => {
  it("pointe le gateway de l'environnement du frontend", () => {
    expect(apiBase('contrats', '__SLUG__.__DEPOT__.qualif.check-consulting.net')).toBe(
      'https://gateway.__DEPOT__.qualif.check-consulting.net/contrats',
    )
  })

  it('utilise le relais du serveur de dev en local', () => {
    expect(apiBase('contrats', 'localhost')).toBe('/api/contrats')
  })

  it("prend par défaut l'adresse de la page", () => {
    expect(apiBase('contrats')).toBe('/api/contrats')
  })
})

describe('appeler', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('renvoie le JSON de la réponse', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => [{ id: 1 }] })
    vi.stubGlobal('fetch', fetch)
    await expect(appeler('contrats', '/contrats')).resolves.toEqual([{ id: 1 }])
    expect(fetch).toHaveBeenCalledWith('/api/contrats/contrats', undefined)
  })

  it('échoue si le code HTTP est une erreur', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))
    await expect(appeler('contrats', '/contrats')).rejects.toThrow('HTTP 503')
  })
})
