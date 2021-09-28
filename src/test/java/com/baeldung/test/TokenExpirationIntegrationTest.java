package com.baeldung.test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import com.baeldung.persistence.dao.UserRepository;
import com.baeldung.persistence.dao.VerificationTokenRepository;
import com.baeldung.persistence.model.User;
import com.baeldung.persistence.model.VerificationToken;
import com.baeldung.spring.TestDbConfig;
import com.baeldung.spring.TestTaskConfig;
import com.baeldung.task.TokensPurgeTask;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { TestDbConfig.class, TestTaskConfig.class })
@Transactional
public class TokenExpirationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private TokensPurgeTask tokensPurgeTask;

    @PersistenceContext
    private EntityManager entityManager;

    private Long token_id;
    private Long user_id;

    //

    @BeforeEach
    public void givenUserWithExpiredToken() {

        // we need a clear token repository
        tokenRepository.deleteAll();

        User user = new User();
        user.setEmail(UUID.randomUUID().toString() + "@example.com");
        user.setPassword(UUID.randomUUID().toString());
        user.setFirstName("First");
        user.setLastName("Last");

        entityManager.persist(user);
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        verificationToken.setExpiryDate(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        entityManager.persist(verificationToken);

        /*
            flush managed entities to the database to populate identifier field
         */
        entityManager.flush();

        /*
            remove managed entities from the persistence context
            so that subsequent SQL queries hit the database
         */
        entityManager.clear();

        token_id = verificationToken.getId();
        user_id = user.getId();
    }

    @Test
    public void whenContextLoad_thenCorrect() {
    	Assertions.assertNotNull(user_id);
    	Assertions.assertNotNull(token_id);
    	Assertions.assertTrue(userRepository.findById(user_id).isPresent());

        Optional<VerificationToken> verificationToken = tokenRepository.findById(token_id);
        Assertions.assertTrue(verificationToken.isPresent());
        Assertions.assertTrue(tokenRepository.findAllByExpiryDateLessThan(Date.from(Instant.now())).anyMatch((token) -> token.equals(verificationToken.get())));
    }

    @AfterEach
    public void flushAfter() {
        entityManager.flush();
    }

    @Test
    public void whenRemoveByGeneratedQuery_thenCorrect() {
        tokenRepository.deleteByExpiryDateLessThan(Date.from(Instant.now()));
        Assertions.assertEquals(0, tokenRepository.count());
    }

    @Test
    public void whenRemoveByJPQLQuery_thenCorrect() {
        tokenRepository.deleteAllExpiredSince(Date.from(Instant.now()));
        Assertions.assertEquals(0, tokenRepository.count());
    }

    @Test
    public void whenPurgeTokenTask_thenCorrect() {
        tokensPurgeTask.purgeExpired();
        Assertions.assertFalse(tokenRepository.findById(token_id).isPresent());
    }
}
