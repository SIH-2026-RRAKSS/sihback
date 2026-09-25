package com.sih.dataservice.auth.jwt;

import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.users.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_BANK_ID = "bankId";
    public static final String CLAIM_JURISDICTION_PATH = "jurisdictionPath";
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        var builder = Jwts.builder()
                .subject(principal.getId().toString())
                .claim(CLAIM_ROLE, principal.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, principal.getTokenVersion())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey);

        if (principal.getBankId() != null) {
            builder.claim(CLAIM_BANK_ID, principal.getBankId().toString());
        }
        if (principal.getJurisdictionPath() != null) {
            builder.claim(CLAIM_JURISDICTION_PATH, principal.getJurisdictionPath());
        }

        return builder.compact();
    }

    public String generateRefreshToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim(CLAIM_TOKEN_VERSION, principal.getTokenVersion())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserRole extractRole(Claims claims) {
        String roleStr = claims.get(CLAIM_ROLE, String.class);
        return roleStr != null ? UserRole.valueOf(roleStr) : null;
    }

    public UUID extractBankId(Claims claims) {
        String bankIdStr = claims.get(CLAIM_BANK_ID, String.class);
        return bankIdStr != null ? UUID.fromString(bankIdStr) : null;
    }

    public String extractJurisdictionPath(Claims claims) {
        return claims.get(CLAIM_JURISDICTION_PATH, String.class);
    }

    public int extractTokenVersion(Claims claims) {
        Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return version != null ? version : 0;
    }

    public String extractTokenType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }
}
