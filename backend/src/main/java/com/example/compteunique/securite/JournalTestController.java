package com.example.compteunique.securite;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.compte.EvenementSecurite;

// CU-167 et CU-48 : GET /test/journal-securite[?identifiant=...] : les 100 derniers événements
// du journal de sécurité, du plus récent au plus ancien. 404 hors environnement de test.
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/test/journal-securite
@RestController
@RequestMapping("/test/journal-securite")
public class JournalTestController {

	private final ConsultationJournal consultation;

	public JournalTestController(ConsultationJournal consultation) {
		this.consultation = consultation;
	}

	/** Une ligne du journal telle qu'enregistrée (le journal ne contient ni mot de passe ni jeton). */
	public record LigneJournal(Long id, OffsetDateTime survenuLe, String evenement, String resultat, String motif,
			String identifiantSaisi, Long identifiantConnexionId, String adresseIp, String compteDemande) {

		static LigneJournal de(EvenementSecurite e) {
			return new LigneJournal(e.getId(), e.getSurvenuLe(), e.getEvenement(), e.getResultat(), e.getMotif(),
					e.getIdentifiantSaisi(), e.getIdentifiantConnexionId(), e.getAdresseIp(), e.getCompteDemande());
		}

	}

	@GetMapping
	public ResponseEntity<List<LigneJournal>> derniers(@RequestParam(required = false) String identifiant) {
		return consultation.derniers(identifiant)
				.map(evenements -> ResponseEntity.ok(evenements.stream().map(LigneJournal::de).toList()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
