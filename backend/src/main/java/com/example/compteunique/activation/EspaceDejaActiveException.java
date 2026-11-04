package com.example.compteunique.activation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EspaceDejaActiveException extends ResponseStatusException {

	private static final long serialVersionUID = 1L;

	public EspaceDejaActiveException() {
		super(HttpStatus.CONFLICT, ActivationService.MESSAGE_DEJA_ACTIVE);
	}

}
