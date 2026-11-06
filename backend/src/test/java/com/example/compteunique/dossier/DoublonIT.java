package com.example.compteunique.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.dossier.OuvertureRefuseeException.EtapeReprise;
import com.example.compteunique.dossier.OuvertureRefuseeException.Refus;

// CU-9 contre un vrai PostgreSQL (requête de normalisation, migration V15, tâche d'inactivité).
// Chaque test utilise sa propre identité et sa propre pièce : la base est partagée.
@SpringBootTest
@Testcontainers
class DoublonIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private DossierService dossiers;

	@Autowired
	private InactiviteDossiers inactivite;

	@Autowired
	private IdentifiantConnexionService identifiants;

	@Autowired
	private JdbcTemplate base;

	private static IdentiteRequete identite(String nom, String prenoms, String commune, String piece) {
		return new IdentiteRequete("Mme", nom, null, prenoms, LocalDate.of(1985, 3, 2), commune, PaysNaissance.FRANCE,
				Nationalite.FRANCE, "1 rue de Test", null, "75001", "Paris", "France", "test@exemple.fr",
				"0600000000", null, TypePiece.CARTE_NATIONALE_IDENTITE, piece, LocalDate.of(2034, 1, 1));
	}

	private static String ref() {
		return UUID.randomUUID().toString();
	}

	private Refus refus(IdentiteRequete identite) {
		return assertThrows(OuvertureRefuseeException.class, () -> dossiers.renseignerIdentite(ref(), identite))
				.getRefus();
	}

	/** Dossier passé à « Compte ouvert » avec son compte ACTIF et un identifiant activé ou non. */
	private void profil(IdentiteRequete identite, boolean active) {
		String reference = ref();
		dossiers.renseignerIdentite(reference, identite);
		IdentifiantConnexion identifiant = identifiants.creer();
		if (active) {
			identifiants.definirMotDePasse(identifiant.getId(), "Abcdefghij1!");
		}
		Long dossierId = base.queryForObject("select id from dossier where reference = ?", Long.class, reference);
		base.update("update dossier set statut = 'COMPTE_OUVERT' where id = ?", dossierId);
		String numero = String.format("%011d", dossierId);
		base.update("insert into compte (dossier_id, customer_id, iban, numero_compte, statut, ouvert_le, "
				+ "identifiant_connexion_id, solde_centimes, solde_mis_a_jour_le) values (?, ?, ?, ?, 'ACTIF', now(), ?, 0, now())",
				dossierId, UUID.randomUUID(), "FR769999900001" + numero + "00", numero,
				identifiant.getId());
	}

	@Test
	void compteActiveMemePieceEcriteAutrementPuisPieceRenouveleeAvecUnTiretSontBloquees() {
		profil(identite("Durand", "Marie-Claire", "Saint-Malo", "AB12-34 56"), true);
		int avant = base.queryForObject("select count(*) from journal_securite where evenement = 'DOUBLON_DETECTE'",
				Integer.class);

		assertThat(refus(identite("Autre", "Nom", "Brest", "ab123456"))).isEqualTo(Refus.ESPACE_EXISTANT);
		assertThat(refus(identite("DURAND", "Marie Claire", "Saint Malo", "CD000002"))).isEqualTo(Refus.ESPACE_EXISTANT);

		assertThat(base.queryForObject("select count(*) from journal_securite where evenement = 'DOUBLON_DETECTE'",
				Integer.class)).isEqualTo(avant + 2);
		assertThat(base.queryForObject("select count(*) from journal_securite where motif like '%123456%' "
				+ "or identifiant_saisi like '%123456%'", Integer.class)).isZero();
	}

	@Test
	void compteNonActiveDonneLeMessageDActivation() {
		profil(identite("Lefevre", "Anne", "Rennes", "EF000001"), false);
		assertThat(refus(identite("Lefevre", "Anne", "Rennes", "EF000002"))).isEqualTo(Refus.ACTIVATION_ATTENDUE);
	}

	@Test
	void unDossierEnCoursEstReprisSansSecondDossier() {
		String existante = ref();
		dossiers.renseignerIdentite(existante, identite("Martin", "Alice", "Nantes", "GH000001"));
		int dossiersAvant = base.queryForObject("select count(*) from dossier", Integer.class);

		OuvertureRefuseeException refus = assertThrows(OuvertureRefuseeException.class,
				() -> dossiers.renseignerIdentite(ref(), identite("Martin", "Alice", "Nantes", "GH000001")));

		assertThat(refus.getRefus()).isEqualTo(Refus.OUVERTURE_EN_COURS);
		assertThat(refus.getReferenceReprise()).isEqualTo(existante);
		assertThat(refus.getEtapeReprise()).isEqualTo(EtapeReprise.IDENTITE);
		assertThat(base.queryForObject("select count(*) from dossier", Integer.class)).isEqualTo(dossiersAvant);
		// La reprise sur la référence existante est acceptée.
		assertThat(dossiers.renseignerIdentite(existante, identite("Martin", "Alice", "Nantes", "GH000001"))
				.reference()).isEqualTo(existante);
	}

	@Test
	void unDossierAnnuleParControleBloqueAvecLeMessageConseiller() {
		String sanctionne = ref();
		dossiers.renseignerIdentite(sanctionne, identite("Petit", "Jeanne", "Lille", "IJ000001"));
		dossiers.enregistrerControles(sanctionne, new ControlesRequete(ResultatControle.NOK, ResultatControle.OK));

		assertThat(refus(identite("Petit", "Jeanne", "Lille", "IJ000009"))).isEqualTo(Refus.OUVERTURE_ANNULEE);

		String pieceExpiree = ref();
		dossiers.renseignerIdentite(pieceExpiree, identite("Moreau", "Paul", "Metz", "IJ000002"));
		dossiers.annuler(pieceExpiree, MotifAnnulation.CONTROLE_AUTOMATIQUE);
		assertThat(refus(identite("Autre", "Personne", "Pau", "IJ000002"))).isEqualTo(Refus.OUVERTURE_ANNULEE);
	}

	@Test
	void unDossierAnnuleParLaClienteNeBloquePas() {
		String annule = ref();
		dossiers.renseignerIdentite(annule, identite("Roux", "Emma", "Dijon", "KL000001"));
		dossiers.annuler(annule, MotifAnnulation.DEMANDE_CLIENTE);

		assertThat(dossiers.renseignerIdentite(ref(), identite("Roux", "Emma", "Dijon", "KL000002")).statut())
				.isEqualTo(StatutDossier.EN_COURS_DE_VERIFICATION);
	}

	@Test
	void unHomonymeNeAilleursNestPasBloque() {
		dossiers.renseignerIdentite(ref(), identite("Bernard", "Louis", "Toulouse", "MN000001"));
		assertThat(dossiers.renseignerIdentite(ref(), identite("Bernard", "Louis", "Bordeaux", "MN000002")).reference())
				.isNotNull();
	}

	@Test
	void apres30JoursSansActionLeDossierEnAttenteEstAnnuleMaisPasCeuxQuiAttendentLaBanque() {
		String enAttente = ref();
		String signe = ref();
		String analyste = ref();
		dossiers.renseignerIdentite(enAttente, identite("Faure", "Lea", "Caen", "OP000001"));
		dossiers.renseignerIdentite(signe, identite("Faure", "Hugo", "Caen", "OP000002"));
		dossiers.renseignerIdentite(analyste, identite("Faure", "Ines", "Caen", "OP000003"));
		base.update("update dossier set derniere_action_le = now() - interval '31 days' where reference in (?, ?, ?)",
				enAttente, signe, analyste);
		base.update("update dossier set statut = 'CONVENTION_SIGNEE' where reference = ?", signe);
		base.update("update dossier set attente_analyste = true where reference = ?", analyste);

		assertThat(inactivite.annulerLesDossiersInactifs()).isPositive();

		assertThat(base.queryForObject("select statut || '/' || motif_annulation from dossier where reference = ?",
				String.class, enAttente)).isEqualTo("ANNULE/INACTIVITE");
		assertThat(base.queryForObject("select statut from dossier where reference = ?", String.class, signe))
				.isEqualTo("CONVENTION_SIGNEE");
		assertThat(base.queryForObject("select statut from dossier where reference = ?", String.class, analyste))
				.isEqualTo("EN_COURS_DE_VERIFICATION");
		// Une nouvelle ouverture redevient possible pour la personne du dossier annulé.
		assertThat(dossiers.renseignerIdentite(ref(), identite("Faure", "Lea", "Caen", "OP000001")).reference())
				.isNotNull();
	}

}
