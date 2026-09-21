package com.sih.dataservice.auth.provider;

public interface IdentityProvider {
    String getProviderName();
    AuthIdentity verifyToken(String credentialToken);
}
