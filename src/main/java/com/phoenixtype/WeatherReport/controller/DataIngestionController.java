package com.phoenixtype.WeatherReport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ingest/")
public class DataIngestionController {

    @GetMapping
    public String helloWorld() {
        return "Hello World!";
    }
}
