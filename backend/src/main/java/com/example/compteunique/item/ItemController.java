package com.example.compteunique.item;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// GET /items : liste ; POST /items {nom, description} : création (nom obligatoire).
// Exposé par la passerelle : https://api.<env>.check-consulting.net/formation-ba-compte-unique/backend/items
@RestController
@RequestMapping("/items")
public class ItemController {

	private final ItemRepository items;

	public ItemController(ItemRepository items) {
		this.items = items;
	}

	@GetMapping
	public List<Item> lister() {
		return items.findAll(Sort.by("id"));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Item creer(@RequestBody ItemRequest requete) {
		if (requete.nom() == null || requete.nom().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nom obligatoire");
		}
		String description = requete.description() == null || requete.description().isBlank()
				? null : requete.description().trim();
		return items.save(new Item(requete.nom().trim(), description));
	}

}
