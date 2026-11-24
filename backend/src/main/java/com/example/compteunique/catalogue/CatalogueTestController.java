package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.catalogue.ChargementCatalogue.DepotInvalide;
import com.example.compteunique.catalogue.ChargementCatalogue.JourNonOuvre;
import com.example.compteunique.catalogue.JournalBatch.Declenchement;

// CU-381 (TECH-13) : page de test /test/catalogue (sans session, comme les autres /test/...). Le prototype n'a pas de
// dépôt fournisseur : la testeuse dépose le fichier du jour, lance le batch sans attendre l'heure et lit le journal.
// POST /test/catalogue/fichiers  : déposer {nomFichier: "catalogue_uc_2026-11-23.csv", contenu}.
// GET  /test/catalogue/fichiers  : les 20 derniers fichiers déposés (sans leur contenu).
// POST /test/catalogue/batch     : lancer le batch maintenant pour la date métier (refusé un samedi ou un dimanche).
// GET  /test/catalogue/journal   : les 20 dernières exécutions, avec les lignes rejetées.
// Absent en preprod et prod (CATALOGUE_SIMULE=false, helm.yaml : 404) ; le batch automatique y tourne toujours.
@RestController
@RequestMapping("/test/catalogue")
@ConditionalOnProperty(name = "app.test.catalogue-simule", havingValue = "true")
public class CatalogueTestController {

	private final ChargementCatalogue chargement;

	public CatalogueTestController(ChargementCatalogue chargement) {
		this.chargement = chargement;
	}

	public record Depot(String nomFichier, String contenu) {
	}

	public record FichierVue(String nomFichier, LocalDate dateFichier, OffsetDateTime deposeLe) {

		static FichierVue de(FichierDepose f) {
			return new FichierVue(f.getNomFichier(), f.getDateFichier(), f.getDeposeLe());
		}

	}

	public record LigneRejeteeVue(int numeroLigne, String codeIsin, String motif) {
	}

	public record JournalVue(LocalDate dateMetier, String declenchement, String nomFichier, OffsetDateTime debut,
			OffsetDateTime fin, int ajoutes, int modifies, int retires, int rejetees, String statut, String message,
			List<LigneRejeteeVue> lignesRejetees) {

		static JournalVue de(JournalBatch j) {
			return new JournalVue(j.getDateMetier(), j.getDeclenchement().name(), j.getNomFichier(), j.getDebut(),
					j.getFin(), j.getAjoutes(), j.getModifies(), j.getRetires(), j.getRejetees(), j.getStatut().name(),
					j.getMessage(), j.getLignesRejetees().stream()
							.map(l -> new LigneRejeteeVue(l.getNumeroLigne(), l.getCodeIsin(), l.getMotif()))
							.toList());
		}

	}

	public record Refus(String message) {
	}

	@PostMapping("/fichiers")
	@ResponseStatus(HttpStatus.CREATED)
	public FichierVue deposer(@RequestBody Depot depot) {
		return FichierVue.de(chargement.deposer(depot.nomFichier(), depot.contenu()));
	}

	@GetMapping("/fichiers")
	public List<FichierVue> fichiers() {
		return chargement.fichiersDeposes().stream().map(FichierVue::de).toList();
	}

	@PostMapping("/batch")
	public JournalVue lancer() {
		return JournalVue.de(chargement.lancer(Declenchement.MANUEL));
	}

	@GetMapping("/journal")
	public List<JournalVue> journal() {
		return chargement.journal().stream().map(JournalVue::de).toList();
	}

	@ExceptionHandler(DepotInvalide.class)
	public ResponseEntity<Refus> depotInvalide(DepotInvalide refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(JourNonOuvre.class)
	public ResponseEntity<Refus> jourNonOuvre(JourNonOuvre refus) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(refus.getMessage()));
	}

}
