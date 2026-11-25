package com.example.compteunique.alertes;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.datemetier.DateMetier;

// CU-382 : journal des alertes à l'équipe, affiché sur les pages de test /test/catalogue et /test/evenements (sans
// session, comme les autres /test/...). Absent en preprod et prod (CATALOGUE_SIMULE et EVENEMENTS_SIMULES à false,
// helm.yaml : 404) ; les alertes y sont quand même envoyées et journalisées.
// GET /test/alertes : les 50 dernières alertes {dateMetier (heure de Paris), origine, message, emailEnvoye}.
@RestController
@ConditionalOnExpression("${app.test.catalogue-simule:false} or ${app.test.evenements-simules:false}")
public class AlertesTestController {

	private final AlertesEquipe alertes;

	public AlertesTestController(AlertesEquipe alertes) {
		this.alertes = alertes;
	}

	public record AlerteVue(String dateMetier, String origine, String message, boolean emailEnvoye) {
	}

	@GetMapping("/test/alertes")
	public List<AlerteVue> journal() {
		return alertes.dernieres().stream()
				.map(a -> new AlerteVue(
						DateMetier.heureDeParis(a.getDateMetier()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
						a.getOrigine(), a.getMessage(), a.isEmailEnvoye()))
				.toList();
	}

}
