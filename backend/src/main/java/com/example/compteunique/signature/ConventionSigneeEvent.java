package com.example.compteunique.signature;

// Publié quand une convention est signée. La création du compte (CU-13) l'écoute, une fois la
// signature enregistrée : la signature elle-même ne crée ni compte, ni IBAN, ni identifiant (CU-12).
public record ConventionSigneeEvent(Long dossierId) {
}
