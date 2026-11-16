package lt.reviv.service;

import lt.reviv.push.config.VapidKeys;
import lt.reviv.push.service.VapidKeyGenerator;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: automatically generated VAPID keys must have exactly the format browsers and web-push expect. */
class VapidKeyGeneratorTest {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    @Test
    void TA65_generatedKeysHaveTheStandardLengthAndFormat() {
        VapidKeys keys = VapidKeyGenerator.generate("mailto:t@test.lt");

        assertEquals(87, keys.publicKey().length());
        assertEquals(43, keys.privateKey().length());
        byte[] point = URL_DECODER.decode(keys.publicKey());
        assertEquals(65, point.length);
        assertEquals(0x04, point[0], "Uncompressed EC point starts with 0x04");
        assertEquals(32, URL_DECODER.decode(keys.privateKey()).length);
        assertTrue(keys.configured());
        assertEquals("mailto:t@test.lt", keys.subject());
    }

    @Test
    void TA65_publicKeyBelongsToThePrivateKey() throws Exception {
        VapidKeys keys = VapidKeyGenerator.generate("mailto:t@test.lt");
        byte[] point = URL_DECODER.decode(keys.publicKey());
        byte[] scalar = URL_DECODER.decode(keys.privateKey());

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        ECPrivateKey ourPrivate = (ECPrivateKey) factory.generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, scalar), curve));
        ECPublicKey ourPublic = (ECPublicKey) factory.generatePublic(new ECPublicKeySpec(
                new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                        new BigInteger(1, Arrays.copyOfRange(point, 33, 65))), curve));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair other = generator.generateKeyPair();

        // ECDH both ways gives the same secret only if the public key really matches the private key.
        assertArrayEquals(agree(ourPrivate, other.getPublic()), agree(other.getPrivate(), ourPublic));
    }

    @Test
    void TA65_everyCallProducesDifferentKeys() {
        Set<String> publicKeys = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            publicKeys.add(VapidKeyGenerator.generate("mailto:t@test.lt").publicKey());
        }
        assertEquals(20, publicKeys.size());
    }

    @Test
    void TA65_privateKeyIsAlways32BytesEvenWithLeadingZeros() {
        for (int i = 0; i < 2000; i++) {
            VapidKeys keys = VapidKeyGenerator.generate("mailto:t@test.lt");
            assertEquals(43, keys.privateKey().length());
            assertEquals(87, keys.publicKey().length());
        }
    }

    @Test
    void TA65_fixedLengthPadsAndTrimsSignByte() {
        assertArrayEquals(new byte[]{0, 0, 5}, VapidKeyGenerator.fixedLength(BigInteger.valueOf(5), 3));
        byte[] withSignByte = new byte[]{0, (byte) 0xFF, 1, 2};
        assertArrayEquals(new byte[]{(byte) 0xFF, 1, 2},
                VapidKeyGenerator.fixedLength(new BigInteger(withSignByte), 3));
        assertNotEquals(0, VapidKeyGenerator.fixedLength(new BigInteger(1, new byte[]{(byte) 0xFF}), 2)[1]);
    }

    private static byte[] agree(java.security.PrivateKey privateKey, java.security.PublicKey publicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }
}