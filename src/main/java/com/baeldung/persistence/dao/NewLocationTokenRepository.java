package com.baeldung.persistence.dao;

import com.baeldung.persistence.model.NewLocationToken;
import com.baeldung.persistence.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewLocationTokenRepository extends JpaRepository<NewLocationToken, Long> {

    NewLocationToken findByToken(String token);

    NewLocationToken findByUserLocation(User userLocation);

}
