package com.example.data_warehouse.controller;

import com.example.data_warehouse.models.Cve;
import com.example.data_warehouse.models.CvePage;
import com.example.data_warehouse.service.CveService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
public class CveController {
    private final CveService service;

    public CveController(CveService service) { this.service = service; }

    @GetMapping("/cve/")
    public Cve get(@RequestParam String id) { return service.get(id); }

    @GetMapping("/cves/")
    public CvePage page(@RequestParam(defaultValue = "0") int offset,
                        @RequestParam(defaultValue = "10") int limit) {
        return service.getPage(offset, limit);
    }

    @PutMapping("/cve/")
    @ResponseStatus(HttpStatus.CREATED)
    public Cve add(@RequestBody Cve cve) { return service.add(cve); }

    @PostMapping("/cve/")
    public Cve update(@RequestParam String id, @RequestBody Cve cve) { return service.update(id, cve); }

}
