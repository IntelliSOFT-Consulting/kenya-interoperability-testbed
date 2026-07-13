package ke.go.dha.itb.broker.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import ke.go.dha.itb.broker.config.BrokerProperties;
import org.springframework.stereotype.Service;

@Service
public class CertificateService {

    private final BrokerProperties properties;

    public CertificateService(BrokerProperties properties) {
        this.properties = properties;
    }

    public String save(String systemName, String itbSessionId, byte[] pdfBytes) {
        // System names come from free-text admin input; keep filenames filesystem-safe.
        String safeName = systemName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path dir = Path.of(properties.getStorage().getCertificatePath());
        Path file = dir.resolve(safeName + "_" + itbSessionId + ".pdf");
        try {
            Files.createDirectories(dir);
            Files.write(file, pdfBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save certificate " + file, e);
        }
        return file.toString();
    }
}
