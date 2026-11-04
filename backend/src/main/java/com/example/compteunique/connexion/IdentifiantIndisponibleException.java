package com.example.compteunique.connexion;

// CU-13 : 5 identifiants tirés de suite existaient déjà ; le dossier passe à « Création à relancer ».
public class IdentifiantIndisponibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public IdentifiantIndisponibleException() {
		super("aucun identifiant de connexion libre après " + IdentifiantConnexionService.TENTATIVES_MAX
				+ " tentatives");
	}

}
