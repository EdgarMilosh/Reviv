package lt.reviv.push.service;

import lt.reviv.push.config.VapidKeys;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Generates a VAPID key pair (P-256) in the format browsers and the web-push library expect:
 * the public key is the 65-byte uncompressed point, the private key is the 32-byte scalar,
 * both Base64 URL-encoded without padding (87 and 43 characters), exactly like
 * "npx web-push generate-vapid-keys".
 */
public final class VapidKeyGenerator {

    private static final int COORDINATE_LENGTH = 32;

    private VapidKeyGenerator() {
    }

    public static VapidKeys generate(String subject) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
            ECPrivateKey privateKey = (ECPrivateKey) pair.getPrivate();

            byte[] point = new byte[1 + 2 * COORDINATE_LENGTH];
            point[0] = 0x04;
            System.arraycopy(fixedLength(publicKey.getW().getAffineX(), COORDINATE_LENGTH), 0, point, 1, COORDINATE_LENGTH);
            System.arraycopy(fixedLength(publicKey.getW().getAffineY(), COORDINATE_LENGTH), 0, point,
                    1 + COORDINATE_LENGTH, COORDINATE_LENGTH);

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new VapidKeys(
                    encoder.encodeToString(point),
                    encoder.encodeToString(fixedLength(privateKey.getS(), COORDINATE_LENGTH)),
                    subject);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate VAPID keys", e);
        }
    }

    /** BigInteger.toByteArray() may add a sign byte or drop leading zeros; VAPID needs a fixed length. */
    public static byte[] fixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] result = new byte[length];
        if (raw.length > length) {
            System.arraycopy(raw, raw.length - length, result, 0, length);
        } else {
            System.arraycopy(raw, 0, result, length - raw.length, raw.length);
        }
        return result;
    }
}