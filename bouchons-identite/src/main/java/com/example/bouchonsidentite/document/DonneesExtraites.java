package com.example.bouchonsidentite.document;

import java.time.LocalDate;

// Données lues sur la pièce (renvoyées au parcours, jamais enregistrées par le bouchon).
public record DonneesExtraites(String nomNaissance, String prenoms, LocalDate dateNaissance, LocalDate dateExpiration) {
}
