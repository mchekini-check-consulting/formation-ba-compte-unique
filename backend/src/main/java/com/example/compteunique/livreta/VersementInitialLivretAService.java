package com.example.compteunique.livreta;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.logging.Loggable;

// CU-33 (US-24) : versement initial du Livret A, une fois l'ouverture validée (CU-31, ou certificat de clôture
// validé, CU-296). Montant entre le minimum (10 €) et le plafond (22 950 €) du Livret A (ParametresLivretA), au
// plus le solde du compte courant Hi-Tech Bank, seule source proposée. Le montant est seulement enregistré dans la
// demande : le débit du compte courant et la création du Livret A ont lieu à la signature (CU-34).
@Service
@Loggable
public class VersementInitialLivretAService {

	static final String MINIMUM = "Le versement minimum à l'ouverture est de 10 €";
	static final String PLAFOND = "Le plafond du Livret A est de 22 950 €";
	static final String MONTANT_INVALIDE = "Montant invalide : saisissez un montant en euros, par exemple 150 ou 150,50";
	static final String SOLDE_INSUFFISANT = "Solde de votre compte courant insuffisant";
	static final String HORS_ETAPE = "Le versement initial se choisit une fois l'ouverture de votre Livret A validée.";

	/** Euros, avec au plus deux décimales (virgule ou point) ; espaces de séparation des milliers acceptés. */
	private static final Pattern MONTANT = Pattern.compile("^\\d+([.,]\\d{1,2})?$");

	private final CompteService comptes;
	private final DemandeLivretARepository demandes;
	private final Clock horloge;

	@Autowired
	public VersementInitialLivretAService(CompteService comptes, DemandeLivretARepository demandes) {
		this(comptes, demandes, Clock.systemDefaultZone());
	}

	VersementInitialLivretAService(CompteService comptes, DemandeLivretARepository demandes, Clock horloge) {
		this.comptes = comptes;
		this.demandes = demandes;
		this.horloge = horloge;
	}

	/**
	 * Critères « Saisie du montant » et « Source du versement » : montant contrôlé puis enregistré dans la demande,
	 * avec le compte courant à débiter et la date de saisie. Un nouveau montant remplace le précédent.
	 */
	@Transactional
	public DemandeLivretA choisir(String jetonSession, String reference, String montant) {
		Compte compte = comptes.compteCourant(jetonSession);
		DemandeLivretA demande = demandes.findByReferenceAndCustomerId(reference, compte.getCustomerId())
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE));
		if (!demande.attendVersementInitial()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, HORS_ETAPE);
		}
		long centimes = centimes(montant);
		if (compte.getSoldeCentimes() < centimes) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, SOLDE_INSUFFISANT);
		}
		demande.choisirVersementInitial(centimes, compte.getId(), OffsetDateTime.now(horloge));
		return demande;
	}

	/**
	 * Montant saisi -> centimes. Vide : minimum ; négatif, non numérique, plus de deux décimales : invalide ; sous
	 * le minimum ou au-dessus du plafond : refusé avec le message correspondant.
	 */
	static long centimes(String saisie) {
		String lu = saisie == null ? "" : saisie.replaceAll("[\\s\\u00A0\\u202F]", "");
		if (lu.isEmpty()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MINIMUM);
		}
		if (!MONTANT.matcher(lu).matches()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MONTANT_INVALIDE);
		}
		BigDecimal euros = new BigDecimal(lu.replace(',', '.'));
		if (euros.compareTo(BigDecimal.valueOf(ParametresLivretA.PLAFOND)) > 0) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, PLAFOND);
		}
		long centimes = euros.movePointRight(2).longValueExact();
		if (centimes < ParametresLivretA.minimumCentimes()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MINIMUM);
		}
		return centimes;
	}

}
