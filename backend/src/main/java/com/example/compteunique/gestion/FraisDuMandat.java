package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.ordres.PositionsContrat;

/**
 * CU-314 / CU-338 : frais de la gestion sous mandat. La cliente paie le taux tout compris (1,5 % par an, paramètre)
 * sur l'encours ; il couvre les frais de mandat et ceux des supports. Si les supports coûtent plus, l'écart est à la
 * charge de l'établissement. Les frais sont calculés et enregistrés, jamais prélevés ici (décision PO du 26/11/2026) : le
 * prélèvement sera fait par le traitement des frais annuels (PER-24 et une future story AV), après TECH-10. Calcul
 * déclenché par la route de test POST /test/gestion/frais ; les positions ne sont jamais débitées ici.
 */
@Service
@Loggable
public class FraisDuMandat {

	private static final BigDecimal CENT = new BigDecimal("100");

	private final ContratGestionRepository contrats;
	private final FraisMandatRepository frais;
	private final ModeDeGestion gestion;
	private final PositionsContrat positions;
	private final CatalogueService catalogue;
	private final DateMetier dateMetier;

	public FraisDuMandat(ContratGestionRepository contrats, FraisMandatRepository frais, ModeDeGestion gestion,
			PositionsContrat positions, CatalogueService catalogue, DateMetier dateMetier) {
		this.contrats = contrats;
		this.frais = frais;
		this.gestion = gestion;
		this.positions = positions;
		this.catalogue = catalogue;
		this.dateMetier = dateMetier;
	}

	/** Calcul annuel des frais du mandat d'un contrat sous mandat, enregistré sans débiter les positions. */
	@Transactional
	public FraisMandat calculer(String numeroContrat) {
		ContratGestion contrat = contrats.findByNumeroContrat(numeroContrat == null ? "" : numeroContrat.trim())
				.orElseThrow(ModeDeGestion.ContratIntrouvable::new);
		if (contrat.getMode() != ModeGestion.MANDAT) {
			throw new HorsMandat();
		}
		BigDecimal taux = gestion.mandat(contrat.getProduit()).tauxToutComprisPct();
		Map<String, BigDecimal> parSupport = positions.encoursParSupport(contrat.getNumeroContrat());
		BigDecimal encours = BigDecimal.ZERO;
		BigDecimal coutSupports = BigDecimal.ZERO;
		for (Map.Entry<String, BigDecimal> position : parSupport.entrySet()) {
			encours = encours.add(position.getValue());
			BigDecimal fraisSupport = catalogue.support(position.getKey()).map(SupportUc::fraisSupportPct)
					.orElse(BigDecimal.ZERO);
			coutSupports = coutSupports.add(position.getValue().multiply(fraisSupport));
		}
		BigDecimal fraisCliente = encours.multiply(taux).divide(CENT, 2, RoundingMode.HALF_EVEN);
		BigDecimal coutReel = coutSupports.divide(CENT, 2, RoundingMode.HALF_EVEN);
		BigDecimal ecart = coutReel.subtract(fraisCliente).max(BigDecimal.ZERO);
		return frais.save(new FraisMandat(contrat.getNumeroContrat(), dateMetier.maintenant().toOffsetDateTime(),
				encours.setScale(2, RoundingMode.HALF_EVEN), taux, fraisCliente, coutReel, ecart));
	}

	/** Les 50 derniers calculs (route de test). */
	@Transactional(readOnly = true)
	public List<FraisMandat> derniers() {
		return frais.findTop50ByOrderByIdDesc();
	}

	/** Contrat qui n'est pas sous mandat (409). */
	public static class HorsMandat extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public HorsMandat() {
			super("Ce contrat n'est pas en gestion sous mandat : aucun frais de mandat à calculer.");
		}

	}

}
