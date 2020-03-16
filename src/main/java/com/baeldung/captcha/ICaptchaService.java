package com.baeldung.captcha;

import com.baeldung.web.error.ReCaptchaInvalidException;

public interface ICaptchaService {
    void processResponse(final String response) throws ReCaptchaInvalidException;

    String getReCaptchaSite();

    String getReCaptchaSecret();
    

    //reCAPTCHA V3
    void processResponseV3(final String response, final String action) throws ReCaptchaInvalidException;
    
    String getReCaptchaSiteV3();

    String getReCaptchaSecretV3();
    
    String getRegisterAction();
}
