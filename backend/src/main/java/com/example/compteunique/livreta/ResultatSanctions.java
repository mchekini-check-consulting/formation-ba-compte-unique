package com.example.compteunique.livreta;

// CU-28 : résultat du contrôle sanctions et personnes politiquement exposées (API Gels des avoirs), transmis
// par l'écran. SANS_REPONSE : pas de réponse en 5 secondes, ni après les 3 relances automatiques.
public enum ResultatSanctions {
	AUCUNE_CORRESPONDANCE,
	CORRESPONDANCE_SANCTIONS,
	CORRESPONDANCE_PEP,
	SANS_REPONSE
}
