package com.example.compteunique.item;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.logging.Loggable;

@Service
@Loggable
public class ItemService {

	private final ItemRepository items;

	public ItemService(ItemRepository items) {
		this.items = items;
	}

	public List<Item> lister() {
		return items.findAll(Sort.by("id"));
	}

	public Item creer(ItemRequest requete) {
		if (requete.nom() == null || requete.nom().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nom obligatoire");
		}
		String description = requete.description() == null || requete.description().isBlank()
				? null : requete.description().trim();
		return items.save(new Item(requete.nom().trim(), description));
	}

}
