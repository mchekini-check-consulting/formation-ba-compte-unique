package com.example.compteunique.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Convention de compte courant présentée à la signature (CU-12). Son empreinte SHA-256 est
// enregistrée avec la signature : elle prouve quel texte a été signé.
// Texte provisoire du prototype : le texte définitif reste à fournir.
public final class Convention {

	public static final String TEXTE = """
			Convention de compte courant Hi-Tech Bank (version prototype 1).

			1. Objet : la présente convention fixe les conditions d'ouverture, de fonctionnement et de \
			clôture de votre compte courant.
			2. Ouverture : le compte est ouvert sans versement initial, avec un solde de 0 €.
			3. Frais : la tenue du compte est gratuite. Les autres frais figurent dans la brochure tarifaire.
			4. Accès en ligne : vous accédez à votre espace avec votre identifiant et votre mot de passe \
			personnel, que vous ne devez communiquer à personne.
			5. Durée : la convention est conclue pour une durée indéterminée. Vous pouvez la résilier à tout moment.
			6. Droits : vous disposez d'un délai de rétractation de 14 jours à compter de la signature.
			""";

	public static final String EMPREINTE = empreinte(TEXTE);

	private Convention() {
	}

	static String empreinte(String texte) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

}
