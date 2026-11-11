package com.example.data_warehouse.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("cve")
public record Cve(@Id String id,
                  String description,
                  String severity,
                  @Column("cvss_score") Double cvssScore,
                  LocalDate published,
                  @Version Long version) {
}

