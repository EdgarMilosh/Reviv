package lt.reviv.service;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.upload.BloodTestService;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US-3: kraujo tyrimo PDF įkėlimas (DP6 - Tyrimų valdymo komponentas). */
@ExtendWith(MockitoExtension.class)
class BloodTestServiceTest {

    private static final long USER_ID = 7L;
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    @Mock
    private BloodTestRepository bloodTestRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private BloodTestService service;

    private Jwt jwt;
    private AppUser user;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-123")
                .build();
        user = new AppUser("google-123", "user@example.com", "Test User", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private void registeredUser() {
        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.of(user));
    }

    private static MockMultipartFile pdfFile(String name, byte[] contents) {
        return new MockMultipartFile("file", name, "application/pdf", contents);
    }

    private static byte[] validPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] emptyPagesPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] passwordProtectedPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static ResponseStatusException uploadError(Runnable action, HttpStatus expectedStatus) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expectedStatus, exception.getStatusCode());
        return exception;
    }

    @Test
    void TA15_savesValidPdfWithFullBytesAndExpectedMetadata() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        when(bloodTestRepository.save(any(BloodTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BloodTest saved = service.upload(pdfFile("tyrimas.pdf", bytes), jwt, false);

        assertEquals(USER_ID, saved.getUserId().longValue());
        assertEquals("tyrimas.pdf", saved.getFileName());
        assertEquals((long) bytes.length, saved.getFileSize().longValue());
        assertArrayEquals(bytes, saved.getPdfData());
        assertEquals("UPLOADED", saved.getStatus());
        assertNotNull(saved.getUploadedAt());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                saved.getFileSha256());
        verify(bloodTestRepository).existsByUserIdAndFileSha256(USER_ID, saved.getFileSha256());
        verify(bloodTestRepository).save(any(BloodTest.class));
    }

    @Test
    void TA16_acceptsValidPdfAtOrdinarySize() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        when(bloodTestRepository.save(any(BloodTest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertDoesNotThrow(() -> service.upload(pdfFile("OK.PDF", bytes), jwt, false));
    }

    @Test
    void TA17_rejectsNullFile() {
        registeredUser();
        uploadError(() -> service.upload(null, jwt, false), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA17_rejectsEmptyFile() {
        registeredUser();
        uploadError(() -> service.upload(pdfFile("empty.pdf", new byte[0]), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA18_rejectsFileLargerThanTenMiB() {
        registeredUser();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(MAX_SIZE_BYTES + 1);

        ResponseStatusException error = uploadError(() -> service.upload(file, jwt, false),
                HttpStatus.BAD_REQUEST);
        assertTrue(error.getReason().contains("10 MB"));
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA19_rejectsNonPdfExtensionEvenWhenContentsAreValid() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        uploadError(() -> service.upload(pdfFile("tyrimas.txt", bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA19_rejectsNullOriginalFilename() {
        registeredUser();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);
        uploadError(() -> service.upload(file, jwt, false), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA20_rejectsFilenameLongerThan255Characters() throws Exception {
        registeredUser();
        String tooLong = "a".repeat(252) + ".pdf";
        byte[] bytes = validPdf();
        uploadError(() -> service.upload(pdfFile(tooLong, bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA20_stripsDirectoryPathFromUploadedFilename() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        when(bloodTestRepository.save(any(BloodTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BloodTest saved = service.upload(pdfFile("C:\\temp\\tyrimas.pdf", bytes), jwt, false);

        assertEquals("tyrimas.pdf", saved.getFileName());
    }

    @Test
    void TA21_rejectsTextRenamedToPdf() {
        registeredUser();
        byte[] bytes = "this-is-not-a-pdf".getBytes(StandardCharsets.UTF_8);
        uploadError(() -> service.upload(pdfFile("fake.pdf", bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA21_rejectsTruncatedCorruptPdf() {
        registeredUser();
        byte[] bytes = "%PDF-1.7\ntruncated corrupted document".getBytes(StandardCharsets.UTF_8);
        uploadError(() -> service.upload(pdfFile("corrupt.pdf", bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA22_rejectsPdfWithoutPages() throws Exception {
        registeredUser();
        byte[] bytes = emptyPagesPdf();
        uploadError(() -> service.upload(pdfFile("no-pages.pdf", bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA23_rejectsPasswordProtectedPdf() throws Exception {
        registeredUser();
        byte[] bytes = passwordProtectedPdf();
        ResponseStatusException error = uploadError(
                () -> service.upload(pdfFile("locked.pdf", bytes), jwt, false),
                HttpStatus.BAD_REQUEST);
        assertTrue(error.getReason().contains("Slaptažodžiu"));
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA24_handlesFailureToReadFileBytes() throws Exception {
        registeredUser();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("tyrimas.pdf");
        when(file.getBytes()).thenThrow(new IOException("read failed"));

        ResponseStatusException error = uploadError(() -> service.upload(file, jwt, false),
                HttpStatus.BAD_REQUEST);
        assertTrue(error.getReason().contains("perskaityti"));
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA25_rejectsDuplicateWhenNotApproved() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        when(bloodTestRepository.existsByUserIdAndFileSha256(eq(USER_ID), anyString()))
                .thenReturn(true);

        ResponseStatusException error = uploadError(
                () -> service.upload(pdfFile("existing.pdf", bytes), jwt, false), HttpStatus.CONFLICT);
        assertTrue(error.getReason().contains("jau buvo įkeltas"));
        verify(bloodTestRepository, never()).save(any());
    }

    @Test
    void TA26_allowsApprovedDuplicateAndSavesNewRecord() throws Exception {
        registeredUser();
        byte[] bytes = validPdf();
        when(bloodTestRepository.existsByUserIdAndFileSha256(eq(USER_ID), anyString()))
                .thenReturn(true);
        when(bloodTestRepository.save(any(BloodTest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BloodTest saved = service.upload(pdfFile("existing.pdf", bytes), jwt, true);

        assertEquals("UPLOADED", saved.getStatus());
        verify(bloodTestRepository).save(any(BloodTest.class));
    }

    @Test
    void TA27_rejectsUploadWhenJwtUserDoesNotExist() throws Exception {
        when(appUserRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        byte[] bytes = validPdf();
        assertThrows(UserNotFoundException.class,
                () -> service.upload(pdfFile("tyrimas.pdf", bytes), jwt, false));
        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA21_rejectsPdfWithTooShortHeader() {
        registeredUser();

        byte[] bytes = "%PDF".getBytes(StandardCharsets.US_ASCII);

        uploadError(
                () -> service.upload(pdfFile("short.pdf", bytes), jwt, false),
                HttpStatus.BAD_REQUEST
        );

        verifyNoInteractions(bloodTestRepository);
    }

    @Test
    void TA21_rejectsPdfWithInvalidHeaderBytes() {
        registeredUser();

        String[] invalidHeaders = {
                "%XDF-",
                "%PXF-",
                "%PDX-",
                "%PDFX"
        };

        for (String header : invalidHeaders) {
            byte[] bytes = header.getBytes(StandardCharsets.US_ASCII);

            uploadError(
                    () -> service.upload(pdfFile("invalid.pdf", bytes), jwt, false),
                    HttpStatus.BAD_REQUEST
            );
        }

        verifyNoInteractions(bloodTestRepository);
    }

}