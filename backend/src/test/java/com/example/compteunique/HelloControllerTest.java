package com.example.compteunique;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelloControllerTest {

	@Test
	void helloRetourneLeMessage() {
		assertEquals("Hello, World!", new HelloController().hello());
	}

}
