package com.example.data_warehouse.repo;

import com.example.data_warehouse.models.Cve;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CveRepository extends CrudRepository<Cve, String> {
    @Query("SELECT * FROM cve ORDER BY id LIMIT :limit OFFSET :offset")
    List<Cve> findPage(@Param("offset") int offset, @Param("limit") int limit);
}
