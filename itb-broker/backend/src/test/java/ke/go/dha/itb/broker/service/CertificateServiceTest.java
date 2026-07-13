package ke.go.dha.itb.broker.service;

import java.nio.file.Files;
import java.nio.file.Path;

import ke.go.dha.itb.broker.config.BrokerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesPdfUnderSystemNameAndSessionId() throws Exception {
        BrokerProperties properties = new BrokerProperties();
        properties.getStorage().setCertificatePath(tempDir.toString());
        CertificateService service = new CertificateService(properties);
        byte[] pdf = "%PDF-1.4 cert".getBytes();

        String path = service.save("Aga Khan EMR v2.1", "SESSION123", pdf);

        assertThat(path).endsWith("Aga_Khan_EMR_v2.1_SESSION123.pdf");
        assertThat(Files.readAllBytes(Path.of(path))).isEqualTo(pdf);
    }

    @Test
    void createsCertificateDirectoryIfMissing() {
        BrokerProperties properties = new BrokerProperties();
        properties.getStorage().setCertificatePath(tempDir.resolve("nested/dir").toString());
        CertificateService service = new CertificateService(properties);

        String path = service.save("sys", "S1", new byte[]{1, 2, 3});

        assertThat(Files.exists(Path.of(path))).isTrue();
    }
}
