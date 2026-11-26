package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.FraisDuMandat.HorsMandat;
import com.example.compteunique.gestion.ModeDeGestion.ContratIntrouvable;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;

// CU-314 / CU-338 : routes de test /test/gestion (sans session, comme les autres /test/...), sans écran (décision BA
// du 26/11/2026) : à appeler depuis Swagger en dev et qualif.
// GET  /test/gestion                    : {grille, quotas, mandats, contrats, frais}
// PUT  /test/gestion/grille/{id}        : {partMinFaibleRisquePct, partMinNonCotePct} (grille PER par horizon)
// PUT  /test/gestion/quotas/{profil}    : {partMinNonCotePct} (quota de non coté AV)
// PUT  /test/gestion/mandats/{produit}  : {tauxToutComprisPct, montantMinimum}
// POST /test/gestion/frais              : {numeroContrat} -> calcul des frais du mandat, sans prélèvement (201 ; 404, 409)
// GET  /test/gestion/signalements       : les 50 derniers signalements au back-office (non coté hors profil, AV-07)
// Absente en preprod et prod (ORDRES_SIMULES=false, helm.yaml : 404), comme les contrats de test.
@RestController
@RequestMapping("/test/gestion")
@ConditionalOnProperty(name = "app.test.ordres-simules", havingValue = "true")
public class GestionTestController {

	private final ParametresGestion parametres;
	private final FraisDuMandat frais;
	private final ModeDeGestion gestion;

	public GestionTestController(ParametresGestion parametres, FraisDuMandat frais, ModeDeGestion gestion) {
		this.parametres = parametres;
		this.frais = frais;
		this.gestion = gestion;
	}

	public record LigneGrille(Long id, ProfilGestion profil, int anneesMin, BigDecimal partMinFaibleRisquePct,
			BigDecimal partMinNonCotePct, boolean aValider, OffsetDateTime modifieLe) {
	}

	public record Quota(ProfilGestion profil, BigDecimal partMinNonCotePct, OffsetDateTime modifieLe) {
	}

	public record Mandat(Produit produit, BigDecimal tauxToutComprisPct, BigDecimal montantMinimum,
			OffsetDateTime modifieLe) {
	}

	public record Contrat(String numeroContrat, Produit produit, ModeGestion mode, ProfilGestion profil,
			OffsetDateTime changeLe) {
	}

	public record Frais(String numeroContrat, OffsetDateTime calculeLe, BigDecimal encours,
			BigDecimal tauxToutComprisPct, BigDecimal fraisCliente, BigDecimal coutReelSupports,
			BigDecimal ecartEtablissement) {
	}

	public record Parametres(List<LigneGrille> grille, List<Quota> quotas, List<Mandat> mandats,
			List<Contrat> contrats, List<Frais> frais) {
	}

	public record ModificationGrille(BigDecimal partMinFaibleRisquePct, BigDecimal partMinNonCotePct) {
	}

	public record ModificationQuota(BigDecimal partMinNonCotePct) {
	}

	public record ModificationMandat(BigDecimal tauxToutComprisPct, BigDecimal montantMinimum) {
	}

	public record Signalement(String numeroContrat, String codeIsin, BigDecimal montant, int niveauRisque,
			ProfilGestion profil, OffsetDateTime signaleLe, String statut) {
	}

	public record DemandeFrais(String numeroContrat) {
	}

	public record Refus(String message) {
	}

	@GetMapping
	public Parametres parametres() {
		return new Parametres(parametres.grille().stream().map(GestionTestController::ligne).toList(),
				parametres.quotas().stream()
						.map(q -> new Quota(q.getProfil(), q.getPartMinNonCotePct(), paris(q.getModifieLe())))
						.toList(),
				parametres.mandats().stream().map(GestionTestController::mandat).toList(),
				parametres.contrats().stream().map(c -> new Contrat(c.getNumeroContrat(), c.getProduit(),
						c.getMode(), c.getProfil(), paris(c.getChangeLe()))).toList(),
				frais.derniers().stream().map(GestionTestController::frais).toList());
	}

	@PutMapping("/grille/{id}")
	public LigneGrille modifierGrille(@PathVariable("id") long id, @RequestBody ModificationGrille modification) {
		return ligne(parametres.modifierGrille(id, modification.partMinFaibleRisquePct(),
				modification.partMinNonCotePct()));
	}

	@PutMapping("/quotas/{profil}")
	public Quota modifierQuota(@PathVariable("profil") ProfilGestion profil,
			@RequestBody ModificationQuota modification) {
		QuotaNonCoteAv q = parametres.modifierQuota(profil, modification.partMinNonCotePct());
		return new Quota(q.getProfil(), q.getPartMinNonCotePct(), paris(q.getModifieLe()));
	}

	@PutMapping("/mandats/{produit}")
	public Mandat modifierMandat(@PathVariable("produit") Produit produit,
			@RequestBody ModificationMandat modification) {
		return mandat(parametres.modifierMandat(produit, modification.tauxToutComprisPct(),
				modification.montantMinimum()));
	}

	@PostMapping("/frais")
	@ResponseStatus(HttpStatus.CREATED)
	public Frais calculer(@RequestBody DemandeFrais demande) {
		return frais(frais.calculer(demande.numeroContrat()));
	}

	@GetMapping("/signalements")
	public List<Signalement> signalements() {
		return gestion.signalements().stream().map(s -> new Signalement(s.getNumeroContrat(), s.getCodeIsin(),
				s.getMontant(), s.getNiveauRisque(), s.getProfil(), paris(s.getSignaleLe()), s.getStatut())).toList();
	}

	@ExceptionHandler(DemandeInvalide.class)
	public ResponseEntity<Refus> invalide(DemandeInvalide refus) {
		return ResponseEntity.badRequest().body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(ContratIntrouvable.class)
	public ResponseEntity<Refus> introuvable(ContratIntrouvable refus) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Refus(refus.getMessage()));
	}

	@ExceptionHandler(HorsMandat.class)
	public ResponseEntity<Refus> horsMandat(HorsMandat refus) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refus(refus.getMessage()));
	}

	private static LigneGrille ligne(GrilleHorizonPer l) {
		return new LigneGrille(l.getId(), l.getProfil(), l.getAnneesMin(), l.getPartMinFaibleRisquePct(),
				l.getPartMinNonCotePct(), l.isAValider(), paris(l.getModifieLe()));
	}

	private static Mandat mandat(ParametreMandat m) {
		return new Mandat(m.getProduit(), m.getTauxToutComprisPct(), m.getMontantMinimum(), paris(m.getModifieLe()));
	}

	private static Frais frais(FraisMandat f) {
		return new Frais(f.getNumeroContrat(), paris(f.getCalculeLe()), f.getEncours(), f.getTauxToutComprisPct(),
				f.getFraisCliente(), f.getCoutReelSupports(), f.getEcartEtablissement());
	}

	private static OffsetDateTime paris(OffsetDateTime heure) {
		return DateMetier.heureDeParis(heure);
	}

}
