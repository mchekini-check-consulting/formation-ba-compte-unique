package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.EvenementADistribuer;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.ordres.OrdresService.DemandeInvalide;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.OrdresService.Enregistrement;
import com.example.compteunique.ordres.OrdresService.MontantInsuffisant;

// CU-376 (TECH-08) : page de test /test/ordres (sans session, comme les autres /test/...). Aucun écran client ne
// passe encore d'ordre et les positions ne viendront qu'avec TECH-10 : la testeuse passe des ordres sur des contrats
// de test et enregistre des positions de test.
// POST   /test/ordres                 : passer un ordre {id, numeroContrat, type, supportDepart, supportArrivee, montant}
//                                       (201 créé, 200 doublon, 400 invalide, 422 montant insuffisant).
// GET    /test/ordres                 : les 50 derniers ordres, avec le montant disponible et l'événement.
// POST   /test/ordres/positions       : enregistrer une position de test {numeroContrat, codeIsin, montant | nombreParts}.
// GET    /test/ordres/positions       : les 50 dernières positions de test.
// GET    /test/ordres/disponible      : montant disponible ?contrat=&support=.
// DELETE /test/ordres                 : vider les ordres et positions de test.
// Absente en preprod et prod (ORDRES_SIMULES=false, helm.yaml : 404).
@RestController
@RequestMapping("/test/ordres")
@ConditionalOnProperty(name = "app.test.ordres-simules", havingValue = "true")
public class OrdresTestController {

	private final OrdresService service;
	private final PositionsContrat positions;
	private final Evenements evenements;

	public OrdresTestController(OrdresService service, PositionsContrat positions, Evenements evenements) {
		this.service = service;
		this.positions = positions;
		this.evenements = evenements;
	}

	/** Suivi de l'événement « ordre demandé » : EN_ATTENTE (pas encore dans Kafka) ou PUBLIE. */
	public record EvenementVue(UUID id, String statut, String publieLe, String derniereErreur) {
	}

	public record OrdreVue(UUID id, String numeroContrat, String type, String supportDepart, String supportArrivee,
			BigDecimal montant, String demandeLe, LocalDate dateExecutionPrevue, String statut,
			BigDecimal montantDisponible, EvenementVue evenement) {
	}

	public record PositionDemandee(String numeroContrat, String codeIsin, BigDecimal montant, BigDecimal nombreParts) {
	}

	public record PositionVue(String numeroContrat, String codeIsin, BigDecimal montant, BigDecimal nombreParts,
			String enregistreeLe) {
	}

	public record Disponible(String numeroContrat, String codeIsin, BigDecimal disponible) {
	}

	public record Refus(String message) {
	}

	public record RefusMontant(String message, BigDecimal disponible) {
	}

	@PostMapping
	public ResponseEntity<OrdreVue> passer(@RequestBody DemandeOrdre demande) {
		Enregistrement resultat = service.passer(demande);
		return ResponseEntity.status(resultat.nouveau() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(vue(resultat.ordre()));
	}

	@GetMapping
	public List<OrdreVue> ordres() {
		return service.derniers().stream().map(this::vue).toList();
	}

	@PostMapping("/positions")
	@ResponseStatus(HttpStatus.CREATED)
	public Disponible enregistrerPosition(@RequestBody PositionDemandee position) {
		String contrat = obligatoire(position.numeroContrat(), OrdresService.CONTRAT_LONGUEUR_MAX, "numéro de contrat");
		String isin = obligatoire(position.codeIsin(), 12, "code ISIN");
		boolean enEuros = position.montant() != null;
		BigDecimal valeur = enEuros ? position.montant() : position.nombreParts();
		if (enEuros == (position.nombreParts() != null) || valeur.signum() <= 0) {
			throw new DemandeInvalide("Indiquez un montant en euros ou un nombre de parts, positif.");
		}
		positions.enregistrerPositionTest(contrat, isin, position.montant(), position.nombreParts());
		return disponible(contrat, isin);
	}

	@GetMapping("/positions")
	public List<PositionVue> positions() {
		return positions.positionsDeTest().stream()
				.map(p -> new PositionVue(p.getNumeroContrat(), p.getCodeIsin(), p.getMontant(), p.getNombreParts(),
						paris(p.getEnregistreeLe())))
				.toList();
	}

	@GetMapping("/disponible")
	public Disponible disponible(@RequestParam("contrat") String contrat, @RequestParam("support") String support) {
		return new Disponible(contrat, support, service.montantDisponible(contrat, support));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void vider() {
		service.viderOrdresEtPositionsDeTest();
	}

	@ExceptionHandler(DemandeInvalide.class)
	public ResponseEntity<Refus> invalide(DemandeInvalide refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(MontantInsuffisant.class)
	public ResponseEntity<RefusMontant> insuffisant(MontantInsuffisant refus) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
				.body(new RefusMontant(refus.getMessage(), refus.getDisponible()));
	}

	private OrdreVue vue(Ordre ordre) {
		String support = ordre.getSupportDepart() != null ? ordre.getSupportDepart() : ordre.getSupportArrivee();
		EvenementVue evenement = ordre.getEvenementId() == null ? null
				: evenements.aDistribuer(ordre.getEvenementId()).map(e -> evenement(ordre.getEvenementId(), e))
						.orElse(null);
		return new OrdreVue(ordre.getId(), ordre.getNumeroContrat(), ordre.getType().name(), ordre.getSupportDepart(),
				ordre.getSupportArrivee(), ordre.getMontant(), paris(ordre.getDemandeLe()),
				ordre.getDateExecutionPrevue(), ordre.getStatut().name(),
				service.montantDisponible(ordre.getNumeroContrat(), support), evenement);
	}

	private static EvenementVue evenement(UUID id, EvenementADistribuer e) {
		return new EvenementVue(id, e.getPublieLe() == null ? "EN_ATTENTE" : "PUBLIE",
				e.getPublieLe() == null ? null : paris(e.getPublieLe()), e.getDerniereErreur());
	}

	private static String paris(OffsetDateTime heure) {
		return DateMetier.heureDeParis(heure).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private static String obligatoire(String valeur, int longueurMax, String nom) {
		String texte = valeur == null ? "" : valeur.trim();
		if (texte.isEmpty() || texte.length() > longueurMax) {
			throw new DemandeInvalide("Le " + nom + " est obligatoire (" + longueurMax + " caractères au plus).");
		}
		return texte;
	}

}
