package com.sanjay.service;

import com.sanjay.handler.ClientHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    private Map<String, String> users = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Value("${users.file}")
    private String usersFile;

    @PostConstruct
    public void init() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(usersFile)) {
            if (is == null) {
                throw new FileNotFoundException("users file not found: " + usersFile);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                }
            }
        }
    }

    public boolean authenticate(String user, String pass) {
        logger.info("Authenticating user={} with pass={}", user, pass);
        return users.containsKey(user) && users.get(user).equals(pass);
    }
}
