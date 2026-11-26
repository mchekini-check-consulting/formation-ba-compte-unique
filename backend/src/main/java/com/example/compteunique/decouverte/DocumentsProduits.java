package com.example.compteunique.decouverte;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;

/**
 * CU-311 (PER-02) et CU-331 (AV-02) : documents à lire avant de souscrire un PER ou une assurance vie. Un document
 * est lu quand il a été ouvert puis que sa case « J'ai pris connaissance » a été cochée. La case ne peut être cochée
 * qu'après l'ouverture. « Continuer » est possible quand les deux documents du produit sont lus.
 * <p>
 * Les stories de souscription (PER-04, AV-01) appellent {@link #peutContinuer(UUID, Produit)}.
 */
@Service
@Loggable
public class DocumentsProduits {

	public enum Produit {
		PER, AV
	}

	public enum Document {
		DIC, FICHE_PRODUIT
	}

	/** Un document présenté : la version est enregistrée à chaque ouverture. */
	public record Presente(Document code, String titre, String version, String contenu) {
	}

	/** Un document et ce que la cliente en a fait. */
	public record EtatDocument(Document code, String titre, String version, String contenu, boolean ouvert,
			boolean lu) {
	}

	public record Etat(Produit produit, List<EtatDocument> documents, boolean peutContinuer) {
	}

	// shortcut: documents fictifs (décision BA du 26/11/2026), à remplacer par les documents validés par la conformité.
	static final Map<Produit, List<Presente>> DOCUMENTS = Map.of(
			Produit.PER, List.of(
					new Presente(Document.DIC, "Document d'informations clés du PER", "DIC-PER-2026-11",
							"Document d'exemple. Le PER est un produit d'épargne retraite : l'épargne est bloquée "
									+ "jusqu'au départ à la retraite, sauf cas de déblocage anticipé prévus par la loi "
									+ "(achat de la résidence principale, accidents de la vie). Le capital investi en "
									+ "unités de compte n'est pas garanti et peut baisser."),
					new Presente(Document.FICHE_PRODUIT, "Fiche produit du PER", "FICHE-PER-2026-11",
							"Document d'exemple. Frais d'entrée : 0 %. Frais de gestion : 0,50 % par an sur les "
									+ "unités de compte. Les versements volontaires sont déductibles du revenu "
									+ "imposable dans la limite du plafond indiqué sur l'avis d'imposition ; ils sont "
									+ "imposés à la sortie.")),
			Produit.AV, List.of(
					new Presente(Document.DIC, "Document d'informations clés de l'assurance vie", "DIC-AV-2026-11",
							"Document d'exemple. L'assurance vie permet d'épargner sur le fonds en euros, dont le "
									+ "capital est garanti, et sur des unités de compte, dont le capital n'est pas "
									+ "garanti. L'épargne reste disponible à tout moment par un rachat partiel ou "
									+ "total."),
					new Presente(Document.FICHE_PRODUIT, "Fiche produit de l'assurance vie", "FICHE-AV-2026-11",
							"Document d'exemple. Frais d'entrée : 0 %. Frais de gestion : 0,65 % par an sur les "
									+ "unités de compte. Fiscalité des rachats : les gains sont imposés, avec un "
									+ "abattement annuel après 8 ans de détention.")));

	private final CompteService comptes;
	private final DateMetier dateMetier;
	private final ConsultationDocumentRepository consultations;

	public DocumentsProduits(CompteService comptes, DateMetier dateMetier,
			ConsultationDocumentRepository consultations) {
		this.comptes = comptes;
		this.dateMetier = dateMetier;
		this.consultations = consultations;
	}

	@Transactional(readOnly = true)
	public Etat etat(String jetonSession, Produit produit) {
		return etat(customerId(jetonSession), produit);
	}

	/** Pour PER-04 et AV-01 : les deux documents du produit ont été ouverts et leur case cochée. */
	@Transactional(readOnly = true)
	public boolean peutContinuer(UUID customerId, Produit produit) {
		return etat(customerId, produit).peutContinuer();
	}

	/** Enregistre l'ouverture du document (date, heure métier et version), à chaque ouverture. */
	@Transactional
	public Etat ouvrir(String jetonSession, Produit produit, Document document) {
		UUID customerId = customerId(jetonSession);
		consultations.save(new ConsultationDocument(customerId, produit, document,
				presente(produit, document).version(), maintenant()));
		return etat(customerId, produit);
	}

	/** Coche la case du document ; refusé tant que cette version du document n'a pas été ouverte. */
	@Transactional
	public Etat cocher(String jetonSession, Produit produit, Document document) {
		UUID customerId = customerId(jetonSession);
		consultations.findFirstByCustomerIdAndProduitAndDocumentAndVersionDocumentOrderByIdDesc(customerId, produit,
				document, presente(produit, document).version()).orElseThrow(DocumentNonOuvert::new)
				.cocher(maintenant());
		return etat(customerId, produit);
	}

	private Etat etat(UUID customerId, Produit produit) {
		List<EtatDocument> documents = DOCUMENTS.get(produit).stream().map(d -> new EtatDocument(d.code(), d.titre(),
				d.version(), d.contenu(),
				consultations.findFirstByCustomerIdAndProduitAndDocumentAndVersionDocumentOrderByIdDesc(customerId,
						produit, d.code(), d.version()).isPresent(),
				consultations.existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(customerId,
						produit, d.code(), d.version())))
				.toList();
		return new Etat(produit, documents, documents.stream().allMatch(EtatDocument::lu));
	}

	private static Presente presente(Produit produit, Document document) {
		return DOCUMENTS.get(produit).stream().filter(d -> d.code() == document).findFirst().orElseThrow();
	}

	private OffsetDateTime maintenant() {
		return dateMetier.maintenant().toOffsetDateTime();
	}

	private UUID customerId(String jetonSession) {
		return comptes.compteCourant(jetonSession).getCustomerId();
	}

	/** Case cochée avant l'ouverture du document : rien n'est enregistré. */
	public static class DocumentNonOuvert extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

}
