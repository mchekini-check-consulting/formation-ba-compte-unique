package com.example.bouchonsidentite.document;

// Corps de POST /stripe-identity/verifications-document.
// recto et verso : code d'une pièce de test (PieceTest), par exemple "PIECE_VALIDE".
public record VerificationDocumentRequete(String dossierId, String recto, String verso, SaisieIdentite saisie) {
}
