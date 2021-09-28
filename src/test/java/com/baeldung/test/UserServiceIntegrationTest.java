package com.baeldung.test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import com.baeldung.persistence.dao.RoleRepository;
import com.baeldung.persistence.dao.UserRepository;
import com.baeldung.persistence.dao.VerificationTokenRepository;
import com.baeldung.persistence.model.Role;
import com.baeldung.persistence.model.User;
import com.baeldung.persistence.model.VerificationToken;
import com.baeldung.service.IUserService;
import com.baeldung.service.UserService;
import com.baeldung.spring.LoginNotificationConfig;
import com.baeldung.spring.ServiceConfig;
import com.baeldung.spring.TestDbConfig;
import com.baeldung.spring.TestIntegrationConfig;
import com.baeldung.validation.EmailExistsException;
import com.baeldung.web.dto.UserDto;
import com.baeldung.web.error.UserAlreadyExistException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { TestDbConfig.class, ServiceConfig.class, TestIntegrationConfig.class, LoginNotificationConfig.class})
public class UserServiceIntegrationTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Test
    public void givenNewUser_whenRegistered_thenCorrect() throws EmailExistsException {
        final String userEmail = UUID.randomUUID().toString();
        final UserDto userDto = createUserDto(userEmail);

        final User user = userService.registerNewUserAccount(userDto);

        Assertions.assertNotNull(user);
        Assertions.assertNotNull(user.getEmail());
        Assertions.assertEquals(userEmail, user.getEmail());
        Assertions.assertNotNull(user.getId());
    }

    @Test
    public void givenDetachedUser_whenAccessingEntityAssociations_thenCorrect() {
        Role role = roleRepository.findByName("ROLE_USER");
        if (role == null) {
            roleRepository.saveAndFlush(new Role("ROLE_USER"));
        }

        // detached entity
        final User user = registerUser();

        // only roles are eagerly fetched
        Assertions.assertNotNull(user.getRoles());

        List<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        Assertions.assertEquals(1, roles.size());
        Assertions.assertEquals("ROLE_USER", roles.iterator().next());
    }

    @Test
    public void givenDetachedUser_whenServiceLoadById_thenCorrect() throws EmailExistsException {
        final User user = registerUser();
        final User user2 = userService.getUserByID(user.getId()).get();
        Assertions.assertEquals(user, user2);
    }

    @Test
    public void givenDetachedUser_whenServiceLoadByEmail_thenCorrect() throws EmailExistsException {
        final User user = registerUser();
        final User user2 = userService.findUserByEmail(user.getEmail());
        Assertions.assertEquals(user, user2);
    }

    @Test
    public void givenUserRegistered_whenDuplicatedRegister_thenCorrect() {
    	Assertions.assertThrows(UserAlreadyExistException.class, () -> {
    		
            final String email = UUID.randomUUID().toString();
            final UserDto userDto = createUserDto(email);
            userService.registerNewUserAccount(userDto);
            userService.registerNewUserAccount(userDto);
    	});

    }

    @Transactional
    public void givenUserRegistered_whenDtoRoleAdmin_thenUserNotAdmin() {
    	Assertions.assertNotNull(roleRepository);
        final UserDto userDto = new UserDto();
        userDto.setEmail(UUID.randomUUID().toString());
        userDto.setPassword("SecretPassword");
        userDto.setMatchingPassword("SecretPassword");
        userDto.setFirstName("First");
        userDto.setLastName("Last");
        Assertions.assertNotNull(roleRepository.findByName("ROLE_ADMIN"));
        final Long adminRoleId = roleRepository.findByName("ROLE_ADMIN").getId();
        Assertions.assertNotNull(adminRoleId);
        userDto.setRole(adminRoleId.intValue());
        final User user = userService.registerNewUserAccount(userDto);
        Assertions.assertFalse(user.getRoles().stream().map(Role::getId).anyMatch(ur -> ur.equals(adminRoleId)));
    }

    @Test
    public void givenUserRegistered_whenCreateToken_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
    }

    @Test
    public void givenUserRegistered_whenCreateTokenCreateDuplicate_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        userService.createVerificationTokenForUser(user, token);
    }

    @Test
    public void givenUserAndToken_whenLoadToken_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        final VerificationToken verificationToken = userService.getVerificationToken(token);
        Assertions.assertNotNull(verificationToken);
        Assertions.assertNotNull(verificationToken.getId());
        Assertions.assertNotNull(verificationToken.getUser());
        Assertions.assertEquals(user, verificationToken.getUser());
        Assertions.assertEquals(user.getId(), verificationToken.getUser().getId());
        Assertions.assertEquals(token, verificationToken.getToken());
        Assertions.assertTrue(verificationToken.getExpiryDate().toInstant().isAfter(Instant.now()));
    }

    @Test
    public void givenUserAndToken_whenRemovingUser_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        userService.deleteUser(user);
    }

    @Test
    public void givenUserAndToken_whenRemovingUserByDao_thenFKViolation() {
    	Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            final User user = registerUser();
            final String token = UUID.randomUUID().toString();
            userService.createVerificationTokenForUser(user, token);
            final long userId = user.getId();
            userService.getVerificationToken(token).getId();
            userRepository.deleteById(userId);
    	});
    }

    @Test
    public void givenUserAndToken_whenRemovingTokenThenUser_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        final long userId = user.getId();
        final long tokenId = userService.getVerificationToken(token).getId();
        tokenRepository.deleteById(tokenId);
        userRepository.deleteById(userId);
    }

    @Test
    public void givenUserAndToken_whenRemovingToken_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        final long tokenId = userService.getVerificationToken(token).getId();
        tokenRepository.deleteById(tokenId);
    }

    @Test
    public void givenUserAndToken_whenNewTokenRequest_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        final VerificationToken origToken = userService.getVerificationToken(token);
        final VerificationToken newToken = userService.generateNewVerificationToken(token);
        Assertions.assertNotEquals(newToken.getToken(), origToken.getToken());
        Assertions.assertNotEquals(newToken.getExpiryDate(), origToken.getExpiryDate());
        Assertions.assertNotEquals(newToken, origToken);
    }

    @Test
    public void givenTokenValidation_whenValid_thenUserEnabled_andTokenRemoved() {
        User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        final long userId = user.getId();
        final String token_status = userService.validateVerificationToken(token);
        Assertions.assertEquals(token_status, UserService.TOKEN_VALID);
        user = userService.getUserByID(userId).get();
        Assertions.assertTrue(user.isEnabled());
    }

    @Test
    public void givenTokenValidation_whenInvalid_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        final String invalid_token = "INVALID_" + UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        userService.getVerificationToken(token).getId();
        final String token_status = userService.validateVerificationToken(invalid_token);
        token_status.equals(UserService.TOKEN_INVALID);
    }

    @Test
    public void givenTokenValidation_whenExpired_thenCorrect() {
        final User user = registerUser();
        final String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        user.getId();
        final VerificationToken verificationToken = userService.getVerificationToken(token);
        verificationToken.setExpiryDate(Date.from(verificationToken.getExpiryDate().toInstant().minus(2, ChronoUnit.DAYS)));
        tokenRepository.saveAndFlush(verificationToken);
        final String token_status = userService.validateVerificationToken(token);
        Assertions.assertNotNull(token_status);
        token_status.equals(UserService.TOKEN_EXPIRED);
    }

    //

    private UserDto createUserDto(final String email) {
        final UserDto userDto = new UserDto();
        userDto.setEmail(email);
        userDto.setPassword("SecretPassword");
        userDto.setMatchingPassword("SecretPassword");
        userDto.setFirstName("First");
        userDto.setLastName("Last");
        userDto.setRole(0);
        return userDto;
    }

    private User registerUser() {
        final String email = UUID.randomUUID().toString();
        final UserDto userDto = createUserDto(email);
        final User user = userService.registerNewUserAccount(userDto);
        Assertions.assertNotNull(user);
        Assertions.assertNotNull(user.getId());
        Assertions.assertEquals(email, user.getEmail());
        return user;
    }

}
