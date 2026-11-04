package com.example.compteunique.messagerie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTestRepository extends JpaRepository<MessageTest, Canal> {
}
