package com.example.compteunique.messagerie;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// CU-87 : dernier e-mail et dernier SMS capturés en environnement de test.
// GET /test/emails, GET /test/sms : 404 s'il n'y en a pas, ou hors environnement de test.
// Via le gateway : https://gateway.<dépôt>.<env>.check-consulting.net/backend/test/...
@RestController
@RequestMapping("/test")
public class MessagesTestController {

	private static final Pattern LIEN = Pattern.compile("https?://\\S+");

	private final Messagerie messagerie;

	public MessagesTestController(Messagerie messagerie) {
		this.messagerie = messagerie;
	}

	/** lien : premier lien du texte (lien d'activation du SMS), pour le rendre cliquable. */
	public record MessageCapture(Canal canal, String destinataire, String objet, String texte, String lien,
			OffsetDateTime envoyeLe) {

		static MessageCapture de(MessageTest message) {
			Matcher lien = LIEN.matcher(message.getTexte());
			return new MessageCapture(message.getCanal(), message.getDestinataire(), message.getObjet(),
					message.getTexte(), lien.find() ? lien.group() : null, message.getEnvoyeLe());
		}

	}

	@GetMapping("/emails")
	public ResponseEntity<MessageCapture> dernierEmail() {
		return dernier(Canal.EMAIL);
	}

	@GetMapping("/sms")
	public ResponseEntity<MessageCapture> dernierSms() {
		return dernier(Canal.SMS);
	}

	private ResponseEntity<MessageCapture> dernier(Canal canal) {
		return messagerie.dernier(canal).map(MessageCapture::de).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
