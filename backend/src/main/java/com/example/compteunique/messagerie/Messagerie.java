package com.example.compteunique.messagerie;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.compteunique.logging.Loggable;

// Envoi des e-mails et des SMS. Aucun fournisseur d'envoi n'est encore branché.
// CU-87 : en environnement de test (app.messages.capture, MESSAGES_CAPTURE dans helm.yaml),
// rien ne quitte l'environnement : le message est enregistré en base, à la place du
// précédent du même canal, et affiché sur /test/emails ou /test/sms.
// Hors test (preprod, prod), l'envoi échoue tant qu'aucun fournisseur n'est branché.
@Service
@Loggable
public class Messagerie {

	private static final Logger log = LoggerFactory.getLogger(Messagerie.class);

	private final MessageTestRepository messages;
	private final boolean capture;
	private final Clock horloge;

	@Autowired
	public Messagerie(MessageTestRepository messages, @Value("${app.messages.capture}") boolean capture) {
		this(messages, capture, Clock.systemDefaultZone());
	}

	Messagerie(MessageTestRepository messages, boolean capture, Clock horloge) {
		this.messages = messages;
		this.capture = capture;
		this.horloge = horloge;
	}

	/** Renvoie false si le message n'a pas pu être remis. */
	public boolean envoyerEmail(String adresse, String objet, String texte) {
		return envoyer(Canal.EMAIL, adresse, objet, texte);
	}

	/** Renvoie false si le message n'a pas pu être remis. */
	public boolean envoyerSms(String numero, String texte) {
		return envoyer(Canal.SMS, numero, null, texte);
	}

	/** Dernier message capturé du canal ; vide hors environnement de test. */
	public Optional<MessageTest> dernier(Canal canal) {
		return capture ? messages.findById(canal) : Optional.empty();
	}

	private boolean envoyer(Canal canal, String destinataire, String objet, String texte) {
		if (!capture) {
			// Ni destinataire ni contenu dans le log : seulement le canal.
			log.warn("Aucun fournisseur d'envoi configuré pour le canal {}", canal);
			return false;
		}
		messages.save(new MessageTest(canal, destinataire, objet, texte, OffsetDateTime.now(horloge)));
		return true;
	}

}
