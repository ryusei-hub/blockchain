package blockchain;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class KeyPairs {

    private KeyPair keyPair;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public KeyPairs() {
        try {

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            keyPairGenerator.initialize(ecSpec);

            keyPair = keyPairGenerator.generateKeyPair();

        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchProviderException e) {
            throw new RuntimeException("Key pair generation failed", e);
        }
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }
}
