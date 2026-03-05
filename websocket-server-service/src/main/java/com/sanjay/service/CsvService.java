package com.sanjay.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvService {
    private List<String[]> data = new ArrayList<>();

    @Value("${csv.file}")
    private String csvFile;

    @PostConstruct
    public void init() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(csvFile)) {
            if (is == null) {
                throw new FileNotFoundException("CSV file not found: " + csvFile);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                data.add(line.split(","));
            }
        }

    }

    public List<String[]> getData() {
        return data;
    }
}
