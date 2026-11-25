package com.example.compteunique.profil;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// CU-415 : en test (local, dev, qualif), lancer à la main la tâche quotidienne de revalidation des profils, depuis la
// page /test/date-metier (sans session, comme les autres /test/...). Absente en preprod et prod
// (DATE_METIER_MODIFIABLE=false, helm.yaml : 404), où seule la tâche de la nuit tourne.
// POST /test/profils/revalidation : {profilsARevalider} = nombre de profils passés « à revalider » par ce lancement.
@RestController
@ConditionalOnProperty(name = "app.test.date-metier-modifiable", havingValue = "true")
public class ProfilTestController {

	private final PublicationStatutProfil publication;

	public ProfilTestController(PublicationStatutProfil publication) {
		this.publication = publication;
	}

	public record Revalidation(int profilsARevalider) {
	}

	@PostMapping("/test/profils/revalidation")
	public Revalidation lancerRevalidation() {
		return new Revalidation(publication.revaliderLesProfilsEchus());
	}

}
