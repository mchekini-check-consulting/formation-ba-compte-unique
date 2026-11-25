package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;

/**
 * CU-376 : fonction publique « positions du contrat » (ADR-001, règle 1). En attendant le registre des mouvements
 * (TECH-10), elle lit les positions de test, enregistrées seulement en dev et qualif (app.test.ordres-simules) ;
 * ailleurs elles ne sont ni enregistrées ni lues. TECH-10 remplacera la lecture sans changer les appelants.
 */
@Service
@Loggable
public class PositionsContrat {

	private final PositionTestRepository positionsTest;
	private final CatalogueService catalogue;
	private final DateMetier dateMetier;
	private final boolean positionsDeTestActives;

	@Autowired
	public PositionsContrat(PositionTestRepository positionsTest, CatalogueService catalogue, DateMetier dateMetier,
			@Value("${app.test.ordres-simules:false}") boolean positionsDeTestActives) {
		this.positionsTest = positionsTest;
		this.catalogue = catalogue;
		this.dateMetier = dateMetier;
		this.positionsDeTestActives = positionsDeTestActives;
	}

	/** Encours du contrat sur un support, en euros : montants, plus les parts valorisées à la dernière VL. */
	@Transactional(readOnly = true)
	public BigDecimal encours(String numeroContrat, String codeIsin) {
		if (!positionsDeTestActives) {
			return BigDecimal.ZERO;
		}
		BigDecimal total = BigDecimal.ZERO;
		for (PositionTest position : positionsTest.findByNumeroContratAndCodeIsin(numeroContrat, codeIsin)) {
			total = total.add(position.getMontant() != null ? position.getMontant()
					: valoriser(position.getNombreParts(), codeIsin));
		}
		return total.setScale(2, RoundingMode.HALF_EVEN);
	}

	private BigDecimal valoriser(BigDecimal parts, String codeIsin) {
		return catalogue.support(codeIsin).map(SupportUc::valeurLiquidative).map(parts::multiply)
				.orElse(BigDecimal.ZERO);
	}

	/** Dev et qualif seulement : enregistre une position de test, en euros ou en nombre de parts. */
	@Transactional
	public void enregistrerPositionTest(String numeroContrat, String codeIsin, BigDecimal montant,
			BigDecimal nombreParts) {
		verifierAutorise();
		positionsTest.save(
				new PositionTest(numeroContrat, codeIsin, montant, nombreParts, dateMetier.maintenant().toOffsetDateTime()));
	}

	/** Dev et qualif seulement : les 50 dernières positions de test. */
	@Transactional(readOnly = true)
	public List<PositionTest> positionsDeTest() {
		verifierAutorise();
		return positionsTest.findTop50ByOrderByIdDesc();
	}

	/** Dev et qualif seulement : efface les positions de test. */
	@Transactional
	public void viderPositionsDeTest() {
		verifierAutorise();
		positionsTest.deleteAllInBatch();
	}

	private void verifierAutorise() {
		if (!positionsDeTestActives) {
			throw new PositionsDeTestRefusees();
		}
	}

	/** Positions de test demandées en preprod ou en prod. */
	public static class PositionsDeTestRefusees extends RuntimeException {

		PositionsDeTestRefusees() {
			super("Les positions de test ne sont pas disponibles dans cet environnement.");
		}

	}

}
