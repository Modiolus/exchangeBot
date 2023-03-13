package com.tgBot.exchange_bot.repositories;

import com.tgBot.exchange_bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    String findUserByLastName(String lastName);
}
