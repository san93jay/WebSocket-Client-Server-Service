package com.sanjay.client.service.client;

import com.sanjay.client.service.protocol.MessageProtocol;
import com.sanjay.client.service.protocol.MessageProtocol.Message;
import com.sanjay.client.service.service.HeartbeatService;
import com.sanjay.client.service.utils.CryptoUtil;
import com.sanjay.client.service.utils.Iso8583Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SocketClient {
    @Value("${socket.host}")
    private String host;

    @Value("${socket.port}")
    private int port;

    @Value("${client-username}")
    private String username;

    @Value("${client-password}")
    private String password;

    @Value("${reconnect.delay.ms}")
    private int reconnectDelay;

    @Value("${heartbeat.interval.ms}")
    private int heartbeatInterval;

    private final HeartbeatService heartbeatService;
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);

    private DataInputStream in;
    private DataOutputStream out;
    private ScheduledExecutorService heartbeatScheduler;
    private final Object ioLock = new Object();
    private volatile boolean connected = false;

    public SocketClient(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    public void start() {
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                Socket socket = null;
                CountDownLatch disconnectLatch = new CountDownLatch(1);
                try {
                    socket = new Socket(host, port);
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());

                    logger.info("Connecting to server {}:{}", host, port);

                    // === RSA Handshake ===
                    Message pubKeyMsg = MessageProtocol.receiveRaw(in);
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyMsg.rawPayload);
                    PublicKey serverPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

                    KeyGenerator aesGen = KeyGenerator.getInstance("AES");
                    aesGen.init(128);
                    SecretKey aesKey = aesGen.generateKey();

                    Cipher rsaCipher = Cipher.getInstance("RSA");
                    rsaCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
                    MessageProtocol.sendRaw(out, (byte) 0x21, rsaCipher.doFinal(aesKey.getEncoded()));

                    CryptoUtil.setSecretKey(aesKey);
                    logger.info("AES key set, ready for encrypted communication");

                    // === Authentication ===
                    synchronized (ioLock) {
                        MessageProtocol.send(out, (byte) 0x01, username + ":" + password);
                        Message authResponse = MessageProtocol.receive(in);
                        if (authResponse.opcode == 0x02) {
                            logger.info("Authentication successful");
                        } else {
                            logger.error("Authentication failed");
                            return;
                        }
                    }

                    // === Receive CSV data ===
                    synchronized (ioLock) {
                        Message rowCountMsg = MessageProtocol.receive(in);
                        int csvRowCount = Integer.parseInt(rowCountMsg.payload);
                        logger.info("Expecting {} CSV rows", csvRowCount);
                        for (int i = 0; i < csvRowCount; i++) {
                            Message csvMsg = MessageProtocol.receive(in);
                            if (csvMsg.opcode == 0x04) {
                                logger.info("Received CSV row: {}", csvMsg.payload);
                            }
                        }
                    }

                    connected = true;
                    final Socket finalSocket = socket;

                    heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
                    heartbeatScheduler.scheduleAtFixedRate(() -> {
                        if (!connected || out == null || in == null) {
                            disconnectLatch.countDown();
                            return;
                        }
                        try {
                            synchronized (ioLock) {
                                heartbeatService.sendHeartbeat(out, in);
                            }
                        } catch (Exception e) {
                            logger.error("Heartbeat failed, triggering reconnect", e);
                            connected = false;
                            out = null;
                            in = null;
                            try { finalSocket.close(); } catch (Exception ignored) {}
                            disconnectLatch.countDown();
                        }
                    }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);

                    disconnectLatch.await();

                } catch (Exception e) {
                    logger.error("Connection lost, retrying...", e);
                } finally {
                    connected = false;
                    out = null;
                    in = null;
                    CryptoUtil.setSecretKey((SecretKey) null); // clear stale key
                    if (socket != null) {
                        try { socket.close(); } catch (Exception ignored) {}
                    }
                    if (heartbeatScheduler != null) {
                        heartbeatScheduler.shutdownNow();
                        try { heartbeatScheduler.awaitTermination(2, TimeUnit.SECONDS); }
                        catch (InterruptedException ignored) {}
                        heartbeatScheduler = null;
                    }
                    try { Thread.sleep(reconnectDelay); }
                    catch (InterruptedException ignored) {}
                }
            }
        });
    }

    public String sendQuery(String query) {
        try {
            synchronized (ioLock) {
                MessageProtocol.send(out, (byte) 0x06, query);

                // Receive raw first, then handle by opcode
                MessageProtocol.Message msg = MessageProtocol.receiveRaw(in);

                if (msg.opcode == (byte) 0x07) return "No data found";
                if (msg.opcode == (byte) 0x05) return Iso8583Util.parseResponse(msg.rawPayload);

                // Encrypted text response (opcode 0x06 etc) — decrypt manually
                String encrypted = new String(msg.rawPayload, StandardCharsets.UTF_8);
                return CryptoUtil.decrypt(encrypted);
            }
        } catch (Exception e) {
            logger.error("Failed to send query", e);
            return "Error: " + e.getMessage();
        }
    }

    public String sendBalanceEnquiry(String company) {
        try {
            synchronized (ioLock) {
                MessageProtocol.sendRaw(out, (byte) 0x05, Iso8583Util.buildRequest(company));
                Message resp = MessageProtocol.receiveRaw(in);
                return Iso8583Util.parseResponse(resp.rawPayload);
            }
        } catch (Exception e) {
            logger.error("Balance enquiry failed", e);
            return "Error: " + e.getMessage();
        }
    }
}