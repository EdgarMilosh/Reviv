package lt.reviv.bloodtests.service.upload;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.context.ApplicationEventPublisher;
import java.util.HexFormat;

@Service
public class BloodTestService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;



    private final BloodTestRepository bloodTestRepository;
    private final AppUserRepository appUserRepository;

    private final ApplicationEventPublisher eventPublisher;


    public BloodTestService(
            BloodTestRepository bloodTestRepository,
            AppUserRepository appUserRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.appUserRepository = appUserRepository;
        this.eventPublisher = eventPublisher;
    }


    @Transactional
    public BloodTest upload(MultipartFile file, Jwt jwt, boolean allowDuplicate) {

        AppUser user = appUserRepository
                .findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pasirinkite PDF dokumentą."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Failas negali viršyti 10 MB."
            );
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null ||
                !fileName.toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".pdf")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Galima įkelti tik PDF dokumentus."
            );
        }

        fileName = fileName.replace('\\', '/');
        fileName = fileName.substring(
                fileName.lastIndexOf('/') + 1
        );

        if (fileName.isBlank() || fileName.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Netinkamas failo pavadinimas."
            );
        }

        byte[] pdfBytes;

        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nepavyko perskaityti dokumento."
            );
        }

        validatePdf(pdfBytes);

        String sha256 = calculateSha256(pdfBytes);

        boolean duplicate = bloodTestRepository
                .existsByUserIdAndFileSha256(
                        user.getId(),
                        sha256
                );

        if (duplicate && !allowDuplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Šis dokumentas jau buvo įkeltas."
            );
        }

        BloodTest bloodTest = new BloodTest(
                user.getId(),
                fileName,
                file.getSize(),
                sha256,
                pdfBytes
        );

        BloodTest saved = bloodTestRepository.save(bloodTest);

        eventPublisher.publishEvent(
                new BloodTestUploadedEvent(saved.getId())
        );

        return saved;
    }

    private void validatePdf(byte[] bytes) {
        if (bytes.length < 5 ||
                bytes[0] != '%' ||
                bytes[1] != 'P' ||
                bytes[2] != 'D' ||
                bytes[3] != 'F' ||
                bytes[4] != '-') {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Failas nėra galiojantis PDF dokumentas."
            );
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Slaptažodžiu apsaugoti PDF nepriimami."
                );
            }

            if (document.getNumberOfPages() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PDF dokumente nėra puslapių."
                );
            }

        } catch (InvalidPasswordException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Slaptažodžiu apsaugoti PDF nepriimami."
            );

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF dokumentas sugadintas arba netinkamas."
            );
        }
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(bytes)
            );

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algoritmas nepasiekiamas.",
                    e
            );
        }
    }
}
