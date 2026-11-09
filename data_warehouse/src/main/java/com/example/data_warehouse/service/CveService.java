package com.example.data_warehouse.service;

import com.example.data_warehouse.models.Cve;
import com.example.data_warehouse.models.CvePage;

public interface CveService {
    Cve update(String id, Cve cve);
    CvePage getPage(int offset, int limit);
    Cve get(String id);
    Cve add(Cve cve);
}
