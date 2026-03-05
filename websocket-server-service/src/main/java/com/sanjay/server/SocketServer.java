package com.sanjay.server;

import com.sanjay.handler.ClientHandler;
import com.sanjay.service.CsvService;
import com.sanjay.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

@Component
public class SocketServer {
    @Value("${socket.port}")
    private int port;

    @Value("${socket.timeout.ms}")
    private int timeout;

    private final UserService userService;
    private final CsvService csvService;
    private static final Logger logger = LoggerFactory.getLogger(SocketServer.class);

    public SocketServer(UserService userService, CsvService csvService) {
        this.userService = userService;
        this.csvService = csvService;
    }

    public void start() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                logger.info("Server started on port {}", port);
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(new ClientHandler(client, timeout, userService, csvService)).start();
                }
            } catch (IOException e) {
                logger.error("Server error", e);
            }
        });
    }
}