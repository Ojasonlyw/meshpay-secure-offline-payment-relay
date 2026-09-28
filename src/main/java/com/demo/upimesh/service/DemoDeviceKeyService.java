package com.demo.upimesh.service;

import com.demo.upimesh.exception.InvalidSignatureException;
import com.demo.upimesh.model.Device;
import com.demo.upimesh.model.DeviceKey;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.repository.DeviceKeyRepository;
import com.demo.upimesh.repository.DeviceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only server-held sender keys are local, dev/test-only simulation keys. */
@Service
public class DemoDeviceKeyService implements ApplicationListener<ApplicationReadyEvent> {
    private static final Set<String> DEMO_IDS = Set.of("phone-alice", "phone-stranger1",
            "phone-stranger2", "phone-stranger3", "phone-bridge");
    private final DeviceRepository devices;
    private final DeviceKeyRepository keys;
    private final DeviceSignatureService signatures;
    private final Environment environment;
    private final Path directory;

    public DemoDeviceKeyService(DeviceRepository devices, DeviceKeyRepository keys,
            DeviceSignatureService signatures, Environment environment,
            @Value("${upi.mesh.demo-key-dir:${java.io.tmpdir}/upi-mesh-demo-keys}") String directory) {
        this.devices = devices;
        this.keys = keys;
        this.signatures = signatures;
        this.environment = environment;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled()) return;
        for (String id : DEMO_IDS) {
            devices.findByDeviceId(id).ifPresent(this::provision);
        }
    }

    private boolean enabled() {
        return environment.matchesProfiles("dev | test");
    }

    @Transactional
    public synchronized void sign(PaymentInstruction instruction) {
        if (!enabled() || !DEMO_IDS.contains(instruction.getDeviceId()))
            throw new InvalidSignatureException("Demo signing is disabled for this device");
        Device device = devices.findByDeviceId(instruction.getDeviceId())
                .orElseThrow(() -> new InvalidSignatureException("Unknown demo device"));
        if (device.getStatus() != Device.Status.ACTIVE || device.getTrustStatus() != Device.TrustStatus.TRUSTED
                || !device.getUser().getVpa().equals(instruction.getSenderVpa()))
            throw new InvalidSignatureException("Demo device cannot sign for sender");
        KeyPair pair = provision(device);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(pair.getPrivate());
            signer.update(signatures.canonicalPayload(instruction));
            instruction.setSignature(Base64.getEncoder().encodeToString(signer.sign()));
            instruction.setSignatureAlgorithm("Ed25519");
        } catch (Exception e) {
            throw new IllegalStateException("Demo signing failed", e);
        }
    }

    private synchronized KeyPair provision(Device device) {
        Path privateFile = directory.resolve(device.getDeviceId() + ".pk8");
        Path publicFile = directory.resolve(device.getDeviceId() + ".pub");
        try {
            boolean privateExists = Files.exists(privateFile);
            boolean publicExists = Files.exists(publicFile);
            if (privateExists != publicExists) throw new IllegalStateException("Incomplete demo key pair");
            DeviceKey registered = keys.findByDevice_IdAndActiveTrue(device.getId()).orElse(null);
            if (!privateExists && registered != null)
                throw new IllegalStateException("Registered demo key has no local private key");
            if (!privateExists) {
                Files.createDirectories(directory);
                KeyPair generated = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                Files.write(privateFile, generated.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
                Files.write(publicFile, generated.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
            }
            byte[] privateBytes = Files.readAllBytes(privateFile);
            byte[] publicBytes = Files.readAllBytes(publicFile);
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            KeyPair pair = new KeyPair(factory.generatePublic(new java.security.spec.X509EncodedKeySpec(publicBytes)),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
            Signature check = Signature.getInstance("Ed25519");
            check.initSign(pair.getPrivate());
            check.update(new byte[]{1, 2, 3});
            byte[] proof = check.sign();
            check.initVerify(pair.getPublic());
            check.update(new byte[]{1, 2, 3});
            if (!check.verify(proof)) throw new IllegalStateException("Demo key pair does not match");
            String fingerprint = DeviceSignatureService.sha256Hex(publicBytes);
            if (registered == null) {
                DeviceKey key = new DeviceKey();
                key.setDevice(device);
                key.setAlgorithm("Ed25519");
                key.setPublicKey(Base64.getEncoder().encodeToString(publicBytes));
                key.setKeyFingerprint(fingerprint);
                keys.saveAndFlush(key);
            } else if (!registered.getKeyFingerprint().equals(fingerprint)) {
                throw new IllegalStateException("Demo private key differs from registered public key");
            }
            return pair;
        } catch (Exception e) {
            throw new IllegalStateException("Demo key provisioning failed for " + device.getDeviceId(), e);
        }
    }
}
