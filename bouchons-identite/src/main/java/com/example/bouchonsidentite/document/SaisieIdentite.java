package com.example.bouchonsidentite.document;

import java.time.LocalDate;

// Identité saisie par le client (US-01), comparée aux données lues sur la pièce.
// dateExpiration est facultative : si elle n'est pas fournie, elle n'est pas contrôlée.
public record SaisieIdentite(String nomNaissance, String prenoms, LocalDate dateNaissance, LocalDate dateExpiration) {
}
