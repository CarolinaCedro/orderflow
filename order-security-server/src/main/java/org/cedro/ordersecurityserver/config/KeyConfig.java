package org.cedro.ordersecurityserver.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.cedro.ordersecurityserver.model.JwkKeyDocument;
import org.cedro.ordersecurityserver.repository.JwkKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class KeyConfig {

    private static final String KEY_ID = "main-key";

    @Autowired
    private JwkKeyRepository jwkKeyRepository;

    @Bean
    public RSAKey rsaKey() {
        return jwkKeyRepository.findById(KEY_ID)
                .map(this::fromDocument)
                .orElseGet(() -> {
                    RSAKey key = generateKey();
                    jwkKeyRepository.save(toDocument(key));
                    return key;
                });
    }

    private RSAKey generateKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey(pair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    private JwkKeyDocument toDocument(RSAKey key) {
        try {
            JwkKeyDocument doc = new JwkKeyDocument();
            doc.setId(KEY_ID);
            doc.setPublicKey(Base64.getEncoder().encodeToString(key.toPublicKey().getEncoded()));
            doc.setPrivateKey(Base64.getEncoder().encodeToString(key.toPrivateKey().getEncoded()));
            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize RSA key", e);
        }
    }

    private RSAKey fromDocument(JwkKeyDocument doc) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] pubBytes = Base64.getDecoder().decode(doc.getPublicKey());
            byte[] privBytes = Base64.getDecoder().decode(doc.getPrivateKey());
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            RSAPrivateKey priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            return new RSAKey.Builder(pub)
                    .privateKey(priv)
                    .keyID(KEY_ID)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA key from MongoDB", e);
        }
    }
}
