package com.example.compteunique.beneficiaire;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.virement.IbanBeneficiaire;

// CU-145 (TECH-06) : bouchon « Vérification du Bénéficiaire (VoP) ». Les banques vérifient l'exactitude d'un IBAN et
// de son titulaire en temps réel grâce au protocole interbancaire standardisé Verification of Payee (VoP), obligatoire
// et gratuit dans toute la zone euro depuis le 9 octobre 2025. Utilisée par l'ajout d'un bénéficiaire (CU-146).
// Aucun partenaire réel n'est branché : en test (local, dev, qualif : app.test.virements-simules, VIREMENTS_SIMULES
// dans helm.yaml), un bouchon répond à partir des IBAN de test (CU-88) et du nom de leur titulaire (prénoms et nom
// de naissance du dossier), et de quelques comptes fictifs d'autres banques (COMPTES_EXTERNES) ; en production, le bouchon n'existe pas et toute vérification est « indisponible ».
// Chaque appel au bouchon est tracé dans le journal de test (date, IBAN, résultat). Jamais d'IBAN ni de nom dans les
// logs.
@Service
@Loggable
public class VerificationBeneficiaire {

	/** Compte d'une autre banque connu du bouchon : IBAN fictif (clé juste), banque et titulaire. */
	public record CompteExterne(String iban, String banque, String titulaire) {
	}

	/**
	 * Comptes d'autres banques connus du bouchon, pour ajouter un bénéficiaire hors de notre banque en test. Tout autre
	 * IBAN reste « introuvable » (critère 3). Affichés sur la page de test /test/verification-beneficiaire.
	 */
	public static final List<CompteExterne> COMPTES_EXTERNES = List.of(
			new CompteExterne("FR7630006000011234567890189", "Crédit Agricole", "Paul Durand"),
			new CompteExterne("FR1420041010050500013M02606", "La Banque Postale", "Marie Dupont"),
			new CompteExterne("FR7630004000011234567890076", "BNP Paribas", "Lucas Bernard"),
			new CompteExterne("FR7630003000021234568001118", "Société Générale", "Camille Petit"),
			new CompteExterne("FR7630002000031234568112257", "LCL", "Hugo Robert"),
			new CompteExterne("FR7610278000041234568223334", "Crédit Mutuel", "Léa Richard"),
			new CompteExterne("FR7618206000051234568334467", "Crédit Agricole", "Nathan Moreau"),
			new CompteExterne("FR7630066000061234568445531", "CIC", "Chloé Simon"),
			new CompteExterne("FR7610907000071234568556666", "Banque Populaire", "Louis Laurent"),
			new CompteExterne("FR7640618000081234568667754", "Boursorama", "Manon Michel"),
			new CompteExterne("FR7616958000091234568778868", "Qonto", "Gabriel Garcia"),
			new CompteExterne("FR7620041000101234568889944", "La Banque Postale", "Inès Lefebvre"),
			new CompteExterne("DE89370400440532013000", "Banque allemande", "Jean Martin"),
			new CompteExterne("ES9121000418450200051332", "Banque espagnole", "Lucia Garcia"),
			new CompteExterne("BE68539007547034", "Banque belge", "Sophie Lambert"));

	private final CompteRepository comptes;
	private final DossierRepository dossiers;
	private final AppelVerificationRepository journal;
	private final BouchonVerificationRepository bouchon;
	private final boolean bouchonActif;
	private final Clock horloge;

	@Autowired
	public VerificationBeneficiaire(CompteRepository comptes, DossierRepository dossiers,
			AppelVerificationRepository journal, BouchonVerificationRepository bouchon,
			@Value("${app.test.virements-simules}") boolean bouchonActif) {
		this(comptes, dossiers, journal, bouchon, bouchonActif, Clock.systemDefaultZone());
	}

	VerificationBeneficiaire(CompteRepository comptes, DossierRepository dossiers, AppelVerificationRepository journal,
			BouchonVerificationRepository bouchon, boolean bouchonActif, Clock horloge) {
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.journal = journal;
		this.bouchon = bouchon;
		this.bouchonActif = bouchonActif;
		this.horloge = horloge;
	}

	/** iban : forme enregistrée (sans espace, en majuscules), au format valide. */
	@Transactional
	public Verification verifier(String iban, String nom) {
		if (!bouchonActif) {
			return Verification.sans(ResultatVerification.INDISPONIBLE);
		}
		Verification verification = parLeBouchon(iban, nom);
		journal.save(new AppelVerification(OffsetDateTime.now(horloge), iban, verification.resultat()));
		return verification;
	}

	private Verification parLeBouchon(String iban, String nom) {
		if (estIndisponible()) {
			return Verification.sans(ResultatVerification.INDISPONIBLE);
		}
		Optional<String> titulaire = IbanBeneficiaire.estDeTest(iban)
				? comptes.findByIban(iban).flatMap(compte -> dossiers.findById(compte.getDossierId()))
						.map(VerificationBeneficiaire::titulaire)
				: COMPTES_EXTERNES.stream().filter(compte -> compte.iban().equals(iban))
						.map(CompteExterne::titulaire).findFirst();
		if (titulaire.isEmpty()) {
			return Verification.sans(ResultatVerification.INTROUVABLE);
		}
		return NomTitulaire.correspondent(nom, titulaire.get())
				? new Verification(ResultatVerification.CORRESPOND, titulaire.get())
				: Verification.sans(ResultatVerification.NE_CORRESPOND_PAS);
	}

	/** Nom du titulaire d'un compte de test : prénoms et nom de naissance du dossier (70 caractères au plus). */
	static String titulaire(Dossier dossier) {
		String nom = (dossier.getPrenoms() + " " + dossier.getNomNaissance()).strip();
		return nom.length() > Beneficiaire.NOM_MAX ? nom.substring(0, Beneficiaire.NOM_MAX) : nom;
	}

	/** Sélecteur « Service indisponible » du bouchon (CU-145 critère 4). */
	@Transactional(readOnly = true)
	public boolean estIndisponible() {
		return bouchon.findById(BouchonVerification.ID).map(BouchonVerification::isIndisponible).orElse(false);
	}

	@Transactional
	public void rendreIndisponible(boolean indisponible) {
		BouchonVerification selecteur = bouchon.findById(BouchonVerification.ID)
				.orElseGet(() -> new BouchonVerification(false));
		selecteur.setIndisponible(indisponible);
		bouchon.save(selecteur);
	}

	/** Derniers appels du journal de test. */
	@Transactional(readOnly = true)
	public List<AppelVerification> journal() {
		return journal.findTop50ByOrderByAppeleLeDescIdDesc();
	}

}
