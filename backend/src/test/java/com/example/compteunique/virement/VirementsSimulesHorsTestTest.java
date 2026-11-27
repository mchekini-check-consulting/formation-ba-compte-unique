package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// PO (27/11/2026), point 4 : la mention « simulé » ne peut jamais apparaître pour une vraie cliente. Un mouvement
// simulé n'est créé que par VirementTestController (CU-143, TECH-04) ; en preprod et prod, VIREMENTS_SIMULES=false
// (helm.yaml) : la route /test/virements-entrants, comme le refus simulé de CU-147, n'existe pas.
class VirementsSimulesHorsTestTest {

	private final ApplicationContextRunner contexte = new ApplicationContextRunner()
			.withUserConfiguration(VirementTestController.class, RefusBanqueTestController.class);

	@Test
	@DisplayName("app.test.virements-simules=false (preprod, prod) : aucune route de simulation de virement")
	void horsTest() {
		contexte.withPropertyValues("app.test.virements-simules=false").run(c -> {
			assertThat(c).doesNotHaveBean(VirementTestController.class);
			assertThat(c).doesNotHaveBean(RefusBanqueTestController.class);
		});
	}

}
