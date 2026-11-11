package com.example.compteunique.beneficiaire;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.function.UnaryOperator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Messagerie;

// CU-146 et CU-147 : code de confirmation à 6 chiffres envoyé par SMS au mobile du dossier de la cliente (en test :
// capturé et affiché sur /test/sms, CU-87). Seule son empreinte est conservée (SHA-256 avec un sel aléatoire),
// jamais le code. Valable 5 minutes, à usage unique, 3 essais. Ni code ni numéro dans les logs.
@Service
@Loggable
public class CodesSms {

	public static final Duration VALIDITE = Duration.ofMinutes(5);
	public static final int CODES_FAUX_MAX = 3;

	static final String ENVOI_IMPOSSIBLE = "Le code n’a pas pu être envoyé par SMS. Réessayez plus tard.";

	private static final SecureRandom ALEATOIRE = new SecureRandom();
	private static final int CODES_POSSIBLES = 1_000_000;
	private static final int OCTETS_SEL = 16;

	/** Résultat de la saisie d'un code. */
	public enum Saisie {
		CORRECT, INCORRECT, INCORRECT_DERNIER, EXPIRE
	}

	private final DossierRepository dossiers;
	private final Messagerie messagerie;

	public CodesSms(DossierRepository dossiers, Messagerie messagerie) {
		this.dossiers = dossiers;
		this.messagerie = messagerie;
	}

	/**
	 * Envoie un nouveau code au mobile du dossier du compte ; texte : message du SMS à partir du code. Renvoie
	 * l'empreinte à enregistrer. Refus « envoi impossible » si le SMS ne part pas (aucun fournisseur hors test).
	 */
	public String envoyer(Compte compte, UnaryOperator<String> texte) {
		String code = String.format("%06d", ALEATOIRE.nextInt(CODES_POSSIBLES));
		String mobile = dossiers.findById(compte.getDossierId()).map(Dossier::getMobile)
				.filter(numero -> !numero.isBlank())
				.orElseThrow(CodesSms::envoiImpossible);
		if (!messagerie.envoyerSms(mobile, texte.apply(code))) {
			throw envoiImpossible();
		}
		return hacher(code);
	}

	private static DemandeRefusee envoiImpossible() {
		return new DemandeRefusee(HttpStatus.SERVICE_UNAVAILABLE, ENVOI_IMPOSSIBLE);
	}

	/** Empreinte « sel$sha256(sel + code) ». */
	public static String hacher(String code) {
		byte[] octets = new byte[OCTETS_SEL];
		ALEATOIRE.nextBytes(octets);
		String sel = HexFormat.of().formatHex(octets);
		return sel + "$" + sha256(sel + code);
	}

	static boolean correspond(String empreinte, String code) {
		if (empreinte == null || code == null || !code.strip().matches("\\d{6}")) {
			return false;
		}
		int separateur = empreinte.indexOf('$');
		if (separateur < 0) {
			return false;
		}
		String attendu = empreinte.substring(separateur + 1);
		String calcule = sha256(empreinte.substring(0, separateur) + code.strip());
		return MessageDigest.isEqual(attendu.getBytes(StandardCharsets.US_ASCII),
				calcule.getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * Contrôle d'une saisie : code expiré (plus de 5 minutes), correct, incorrect, ou incorrect pour la troisième
	 * fois (le parcours est alors annulé).
	 */
	public static Saisie controler(String empreinte, OffsetDateTime envoyeLe, int codesFauxAvant, String code,
			OffsetDateTime maintenant) {
		if (maintenant.isAfter(envoyeLe.plus(VALIDITE))) {
			return Saisie.EXPIRE;
		}
		if (correspond(empreinte, code)) {
			return Saisie.CORRECT;
		}
		return codesFauxAvant + 1 >= CODES_FAUX_MAX ? Saisie.INCORRECT_DERNIER : Saisie.INCORRECT;
	}

	private static String sha256(String texte) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

}
