package com.example.compteunique.securite;

// CU-48 critère 8 : le journal de sécurité ne peut pas être écrit ; l'action est refusée.
public class JournalIndisponibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JournalIndisponibleException(Throwable cause) {
		super("Journal de sécurité indisponible", cause);
	}

}
