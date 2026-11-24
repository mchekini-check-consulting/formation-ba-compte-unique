package com.example.compteunique.datemetier;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.datemetier.DateMetier.ChangementInvalide;
import com.example.compteunique.datemetier.DateMetier.DateMetierNonModifiable;
import com.example.compteunique.datemetier.DateMetier.Etat;

// CU-380 (TECH-12) : date métier, page de test /test/date-metier (sans session, comme les autres /test/...).
// Les modules de placement n'appellent pas ces routes : ils utilisent directement DateMetier.
// GET  /test/date-metier               : date métier en vigueur (tous environnements, lecture seule).
// PUT  /test/date-metier               : la changer {date: "2026-12-31T11:00", auteur} (heure de Paris).
// POST /test/date-metier/date-reelle   : revenir à l'heure réelle {auteur}.
// GET  /test/date-metier/journal       : les 100 derniers changements.
// Changer et consulter le journal répondent 403 en preprod et prod (DATE_METIER_MODIFIABLE=false, helm.yaml).
@RestController
public class DateMetierController {

	private final DateMetier dateMetier;

	public DateMetierController(DateMetier dateMetier) {
		this.dateMetier = dateMetier;
	}

	public record Changement(LocalDateTime date, String auteur) {
	}

	public record RetourDateReelle(String auteur) {
	}

	public record LigneJournal(OffsetDateTime ancienneDate, OffsetDateTime nouvelleDate, String auteur,
			OffsetDateTime changeLe) {

		static LigneJournal de(ChangementDateMetier c) {
			return new LigneJournal(c.getAncienneDate(), c.getNouvelleDate(), c.getAuteur(), c.getChangeLe());
		}

	}

	public record Refus(String message) {
	}

	@GetMapping("/test/date-metier")
	public Etat lire() {
		return dateMetier.etat();
	}

	@PutMapping("/test/date-metier")
	public Etat changer(@RequestBody Changement changement) {
		return dateMetier.changer(changement.date(), changement.auteur());
	}

	@PostMapping("/test/date-metier/date-reelle")
	public Etat revenirALaDateReelle(@RequestBody RetourDateReelle demande) {
		return dateMetier.revenirALaDateReelle(demande.auteur());
	}

	@GetMapping("/test/date-metier/journal")
	public List<LigneJournal> journal() {
		return dateMetier.journal().stream().map(LigneJournal::de).toList();
	}

	@ExceptionHandler(DateMetierNonModifiable.class)
	public ResponseEntity<Refus> nonModifiable(DateMetierNonModifiable refus) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(ChangementInvalide.class)
	public ResponseEntity<Refus> invalide(ChangementInvalide refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getMessage()));
	}

}
