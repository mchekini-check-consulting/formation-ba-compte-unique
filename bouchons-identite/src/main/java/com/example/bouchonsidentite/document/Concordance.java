package com.example.bouchonsidentite.document;

import com.example.bouchonsidentite.commun.Textes;

// Concordance champ par champ entre la pièce et la saisie : true = identique,
// false = différent, null = non contrôlé (date d'expiration non saisie).
public record Concordance(Boolean nomNaissance, Boolean prenoms, Boolean dateNaissance, Boolean dateExpiration) {

	static Concordance entre(DonneesExtraites piece, SaisieIdentite saisie) {
		return new Concordance(
				Textes.identiques(piece.nomNaissance(), saisie.nomNaissance()),
				Textes.identiques(piece.prenoms(), saisie.prenoms()),
				piece.dateNaissance().equals(saisie.dateNaissance()),
				saisie.dateExpiration() == null ? null : piece.dateExpiration().equals(saisie.dateExpiration()));
	}

	boolean complete() {
		return nomNaissance && prenoms && dateNaissance && !Boolean.FALSE.equals(dateExpiration);
	}

}
