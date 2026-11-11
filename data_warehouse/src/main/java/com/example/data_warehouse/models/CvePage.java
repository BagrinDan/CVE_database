package com.example.data_warehouse.models;

import java.util.List;

public record CvePage(List<Cve> items, int offset, int limit) {}
