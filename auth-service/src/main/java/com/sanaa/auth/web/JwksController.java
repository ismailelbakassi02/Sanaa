package com.sanaa.auth.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sanaa.auth.config.JwtKeyProvider;

/**
 * Public JWKS endpoint. Only public key material is returned; the private key never leaves the
 * process.
 */
@RestController
public class JwksController {

    private final JwtKeyProvider keyProvider;

    public JwksController(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(keyProvider.publicJwk().toJSONObject()));
    }
}
