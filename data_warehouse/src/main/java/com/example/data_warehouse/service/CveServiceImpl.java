package com.example.data_warehouse.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.data_warehouse.exception.ApiException;
import com.example.data_warehouse.models.Cve;
import com.example.data_warehouse.models.CvePage;
import com.example.data_warehouse.repo.CveRepository;



@Service
public class CveServiceImpl implements CveService {
    private static final int MAX_LIMIT = 100;
    private static final Pattern ID_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,}");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final CveRepository repo;

    public CveServiceImpl(CveRepository repo) {
        this.repo = repo;
    }

    /** PUT: добавить новую запись. */
    @Transactional
    public Cve add(Cve cve) {
        validate(cve);
        if (repo.existsById(cve.id())) {
            throw new ApiException(409, "CVE already exists: " + cve.id());
        }
        // version = null, поэтому Spring Data выполнит INSERT
        return repo.save(withVersion(cve, null));
    }

    /** GET одной записи. */
    
    @Transactional(readOnly = true)     // чтение -> replica
    public Cve get(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ApiException(404, "CVE not found: " + id));
    }

    /** GET списка (страница). */
    @Transactional(readOnly = true)  
    public CvePage getPage(int offset, int limit) {
        if (offset < 0) {
            throw new ApiException(400, "offset must be >= 0");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(400, "limit must be between 1 and " + MAX_LIMIT);
        }
        List<Cve> items = repo.findPage(offset, limit);
        return new CvePage(items, offset, limit);
    }

    /** POST: изменить существующую запись. */
    @Transactional
    public Cve update(String id, Cve cve) {
        validate(cve);
        if (!id.equals(cve.id())) {
            throw new ApiException(400, "id in URL and in body must match");
        }
        Cve existing = get(id);                    // 404, если записи нет
        try {
            return repo.save(withVersion(cve, existing.version()));
        } catch (OptimisticLockingFailureException e) {
            throw new ApiException(409, "CVE was changed by someone else, try again: " + id);
        }
    }

    private Cve withVersion(Cve c, Long version) {
        return new Cve(c.id(), c.description(), c.severity(), c.cvssScore(), c.published(), version);
    }

    private void validate(Cve cve) {
        if (cve == null) {
            throw new ApiException(400, "Body is required");
        }
        if (cve.id() == null || !ID_PATTERN.matcher(cve.id()).matches()) {
            throw new ApiException(400, "id must look like CVE-2024-1234");
        }
        if (cve.description() == null || cve.description().isBlank()) {
            throw new ApiException(400, "description is required");
        }
        if (cve.severity() == null || !SEVERITIES.contains(cve.severity())) {
            throw new ApiException(400, "severity must be one of " + SEVERITIES);
        }
        if (cve.cvssScore() != null && (cve.cvssScore() < 0 || cve.cvssScore() > 10)) {
            throw new ApiException(400, "cvssScore must be between 0 and 10");
        }
    }
}