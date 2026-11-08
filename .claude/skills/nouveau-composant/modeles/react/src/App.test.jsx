import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import App from './App.jsx'

it("affiche le titre de l'application", () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: '__TITRE__' })).toBeTruthy()
})
