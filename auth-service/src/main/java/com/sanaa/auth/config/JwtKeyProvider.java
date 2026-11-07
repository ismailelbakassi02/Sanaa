package com.sanaa.auth.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.security.converter.RsaKeyConverters;

/**
 * Loads the signing key pair from PEM files.
 *
 * <p>WHY files instead of classpath resources: the private key must never be packaged into the
 * jar or committed. The paths come from environment variables, and the key is only read at startup.
 */
public final class JwtKeyProvider {

    private final RSAPublicKey publicKey;
    private final RSAKey signingKey;
    private final String keyId;

    private JwtKeyProvider(RSAPublicKey publicKey, RSAKey signingKey, String keyId) {
        this.publicKey = publicKey;
        this.signingKey = signingKey;
        this.keyId = keyId;
    }

    public static JwtKeyProvider load(Path privateKeyPath, Path publicKeyPath) {
        RSAPrivateKey privateKey = readPrivateKey(privateKeyPath);
        RSAPublicKey publicKey = readPublicKey(publicKeyPath);

        try {
            // RFC 7638 thumbprint of the public key. The kid is stable across restarts and changes
            // only when the key changes, so JWKS consumers can cache it safely.
            String keyId = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            RSAKey signingKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();
            return new JwtKeyProvider(publicKey, signingKey, keyId);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not compute the JWT key thumbprint", exception);
        }
    }

    private static RSAPrivateKey readPrivateKey(Path path) {
        try (InputStream input = open(path, "private")) {
            RSAPrivateKey key = RsaKeyConverters.pkcs8().convert(input);
            if (key == null) {
                throw new IllegalStateException("Private key file is empty: " + path);
            }
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read private key: " + path, exception);
        }
    }

    private static RSAPublicKey readPublicKey(Path path) {
        try (InputStream input = open(path, "public")) {
            RSAPublicKey key = RsaKeyConverters.x509().convert(input);
            if (key == null) {
                throw new IllegalStateException("Public key file is empty: " + path);
            }
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read public key: " + path, exception);
        }
    }

    private static InputStream open(Path path, String kind) throws IOException {
        if (path == null || path.toString().isBlank()) {
            throw new IllegalStateException("JWT " + kind + " key path is not configured");
        }
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("JWT " + kind + " key file is not readable: " + path);
        }
        return Files.newInputStream(path);
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public RSAKey publicJwk() {
        return signingKey.toPublicJWK();
    }

    public String keyId() {
        return keyId;
    }
}
