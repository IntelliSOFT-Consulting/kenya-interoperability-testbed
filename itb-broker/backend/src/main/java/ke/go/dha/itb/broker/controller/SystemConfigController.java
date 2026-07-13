package ke.go.dha.itb.broker.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import ke.go.dha.itb.broker.dto.SystemConfigRequest;
import ke.go.dha.itb.broker.model.SystemConfig;
import ke.go.dha.itb.broker.repository.SystemConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/systems")
public class SystemConfigController {

    private final SystemConfigRepository repository;

    public SystemConfigController(SystemConfigRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<SystemConfig> create(@RequestBody SystemConfigRequest request) {
        SystemConfig config = new SystemConfig();
        apply(config, request);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(config));
    }

    @GetMapping
    public List<SystemConfig> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SystemConfig> get(@PathVariable UUID id) {
        return repository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemConfig> update(@PathVariable UUID id,
                                               @RequestBody SystemConfigRequest request) {
        return repository.findById(id)
            .map(config -> {
                apply(config, request);
                config.setUpdatedAt(LocalDateTime.now());
                return ResponseEntity.ok(repository.save(config));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void apply(SystemConfig config, SystemConfigRequest request) {
        config.setSystemName(request.systemName());
        config.setSutBaseUrl(request.sutBaseUrl());
        config.setOrganizationName(request.organizationName());
        config.setSystemVersion(request.systemVersion());
        config.setAuthType(request.authType());
        config.setAuthToken(request.authToken());
        config.setCertificationPortalSystemId(request.certificationPortalSystemId());
    }
}
