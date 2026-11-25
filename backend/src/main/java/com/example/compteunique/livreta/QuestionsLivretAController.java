package com.example.compteunique.livreta;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.connexion.ConnexionController;
import com.example.compteunique.livreta.LivretAController.Refus;
import com.example.compteunique.livreta.QuestionsLivretAService.Etape;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;

// CU-32 (US-23) : étape « Questions et données » de l'ouverture d'un Livret A (session obligatoire), avant
// l'éligibilité.
// GET  /livret-a/questions : {"versionTexte", "texte", "texteAAccepter", "questionnaire"}.
// POST /livret-a/questions : {"objetEpargne", "origineFonds", "precisionOrigine"?, "texteAccepte"} -> questionnaire (201).
// Refus : {"message", "termine"} avec le message affiché à la cliente (mêmes règles que LivretAController).
@RestController
@RequestMapping("/livret-a/questions")
public class QuestionsLivretAController {

	private static final String COOKIE = ConnexionController.COOKIE_SESSION;

	private final QuestionsLivretAService questions;

	public QuestionsLivretAController(QuestionsLivretAService questions) {
		this.questions = questions;
	}

	/** Étape vue par la cliente ; questionnaire : celui de cette ouverture, null s'il n'est pas rempli. */
	public record EtapeQuestions(String versionTexte, String texte, boolean texteAAccepter,
			QuestionnaireVue questionnaire) {
	}

	/** Réponses de la cliente ; texteAccepte : case du texte d'information (ignorée s'il est déjà accepté). */
	public record Reponses(ObjetEpargne objetEpargne, OrigineFonds origineFonds, String precisionOrigine,
			Boolean texteAccepte) {
	}

	@GetMapping
	public EtapeQuestions etape(@CookieValue(name = COOKIE, required = false) String jetonSession) {
		Etape etape = questions.etat(jetonSession);
		return new EtapeQuestions(etape.versionTexte(), etape.texte(), etape.texteAAccepter(),
				QuestionnaireVue.de(etape.questionnaire()));
	}

	@PostMapping
	public ResponseEntity<QuestionnaireVue> repondre(@CookieValue(name = COOKIE, required = false) String jetonSession,
			@RequestBody(required = false) Reponses reponses) {
		Reponses lues = reponses == null ? new Reponses(null, null, null, null) : reponses;
		return ResponseEntity.status(HttpStatus.CREATED).body(QuestionnaireVue.de(questions.repondre(jetonSession,
				lues.objetEpargne(), lues.origineFonds(), lues.precisionOrigine(), lues.texteAccepte())));
	}

	/** Sans session : 401 sans corps ; sans compte courant : 404 ; refus : son code et son message. */
	@ExceptionHandler({ DemandeRefusee.class, ClienteNonConnecteeException.class, CompteInaccessibleException.class })
	public ResponseEntity<Refus> refus(RuntimeException erreur) {
		return LivretAController.reponseDe(erreur);
	}

}
