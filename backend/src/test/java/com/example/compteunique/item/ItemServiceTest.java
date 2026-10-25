package com.example.compteunique.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ItemServiceTest {

	private final ItemRepository items = mock(ItemRepository.class);
	private final ItemService service = new ItemService(items);

	@Test
	void listerRenvoieLesItemsParId() {
		List<Item> attendus = List.of(new Item("a", null));
		when(items.findAll(Sort.by("id"))).thenReturn(attendus);
		assertEquals(attendus, service.lister());
	}

	@Test
	void creerEnregistreLItemNettoye() {
		when(items.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
		Item cree = service.creer(new ItemRequest("  Premier  ", "  "));
		assertEquals("Premier", cree.getNom());
		assertNull(cree.getDescription());
	}

	@Test
	void creerRefuseUnNomVide() {
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class,
				() -> service.creer(new ItemRequest(" ", "x")));
		assertEquals(HttpStatus.BAD_REQUEST, erreur.getStatusCode());
		verify(items, never()).save(any(Item.class));
	}

	@Test
	void creerRefuseUnNomNull() {
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class,
				() -> service.creer(new ItemRequest(null, "x")));
		assertEquals(HttpStatus.BAD_REQUEST, erreur.getStatusCode());
		verify(items, never()).save(any(Item.class));
	}

	@Test
	void creerAccepteUneDescriptionNulle() {
		when(items.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
		Item cree = service.creer(new ItemRequest("Premier", null));
		assertNull(cree.getDescription());
	}

	@Test
	void creerConserveUneDescriptionNonVideNettoyee() {
		when(items.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
		Item cree = service.creer(new ItemRequest("Premier", "  Une description  "));
		assertEquals("Une description", cree.getDescription());
	}

}
