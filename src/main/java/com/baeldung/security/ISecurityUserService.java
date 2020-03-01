package com.baeldung.security;

public interface ISecurityUserService {

    String validatePasswordResetToken(long id, String token);

}
