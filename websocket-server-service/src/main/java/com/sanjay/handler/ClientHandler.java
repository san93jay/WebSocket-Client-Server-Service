package com.sanjay.handler;

import com.sanjay.exception.QueryException;
import com.sanjay.protocol.MessageProtocol;
import com.sanjay.service.CsvService;
import com.sanjay.service.UserService;
import com.sanjay.utils.CryptoUtil;
import com.sanjay.utils.Iso8583Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final int timeout;
    private final UserService userService;
    private final CsvService csvService;
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    public ClientHandler(Socket socket, int timeout, UserService userService, CsvService csvService) {
        this.socket = socket;
        this.timeout = timeout;
        this.userService = userService;
        this.csvService = csvService;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(timeout);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // === RSA Handshake ===
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // Send RSA public key RAW
            MessageProtocol.sendRaw(out, (byte)0x20, publicKey.getEncoded());

            // Receive encrypted AES key RAW
            MessageProtocol.Message aesKeyMsg = MessageProtocol.receiveRaw(in);
            byte[] encryptedKeyBytes = aesKeyMsg.rawPayload;

            Cipher rsaCipher = Cipher.getInstance("RSA");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKeyBytes);

            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            CryptoUtil.setSecretKey(aesKey);
            logger.info("AES key set, ready for encrypted communication");

            // === Authentication ===
            MessageProtocol.Message authMsg = MessageProtocol.receive(in);
            String[] creds = authMsg.payload.split(":");
            String user = creds[0];
            String pass = creds.length > 1 ? creds[1] : "";

            if (!userService.authenticate(user, pass)) {
                MessageProtocol.send(out, (byte)0x03, null); // AUTH_FAILED
                logger.info("Authentication failed for user {}", user);
                socket.close();
                return;
            }
            MessageProtocol.send(out, (byte)0x02, null); // AUTH_SUCCESS
            logger.info("User authenticated: {}", user);

            // === Send CSV data ===
            List<String[]> data = csvService.getData();
            MessageProtocol.send(out, (byte)0x0B, String.valueOf(data.size())); // CSV_ROW_COUNT
            for (String[] row : data) {
                MessageProtocol.send(out, (byte)0x04, String.join(",", row)); // CSV_DATA
            }

            // === Query loop ===
            while (true) {
                try {
                    // Always receive raw first — opcode determines how to handle bytes
                    MessageProtocol.Message msg = MessageProtocol.receiveRaw(in);

                    // ISO 8583 binary frame — never encrypted
                    if (msg.opcode == (byte) 0x05) {
                        ByteBuffer buf = ByteBuffer.wrap(msg.rawPayload);
                        buf.getInt();
                        byte[] companyBytes = new byte[20];
                        buf.get(companyBytes);
                        String companyName = new String(companyBytes).trim();

                        Optional<String[]> row = data.stream()
                                .filter(r -> r[0].trim().equalsIgnoreCase(companyName))
                                .findFirst();

                        byte[] response;
                        if (row.isPresent()) {
                            long cents = Long.parseLong(row.get()[2].trim()) * 100;
                            response = Iso8583Util.buildResponse("00", cents);
                            logger.info("ISO8583 APPROVED: {} balance={}c", companyName, cents);
                        } else {
                            response = Iso8583Util.buildResponse("25", 0);
                            logger.warn("ISO8583 NOT FOUND: {}", companyName);
                        }
                        MessageProtocol.sendRaw(out, (byte) 0x05, response);
                        continue;
                    }

                    // PING — payload is empty, no decryption needed
                    if (msg.opcode == (byte) 0x08) {
                        MessageProtocol.send(out, (byte) 0x09, null);
                        continue;
                    }

                    // All other opcodes — decrypt the raw bytes as AES-encrypted text
                    String query;
                    try {
                        String encrypted = new String(msg.rawPayload, StandardCharsets.UTF_8);
                        query = encrypted.isEmpty() ? "" : CryptoUtil.decrypt(encrypted).trim();
                    } catch (Exception e) {
                        logger.error("Decryption failed for opcode 0x{}", String.format("%02X", msg.opcode));
                        continue;
                    }

                    if (query.isEmpty()) {
                        logger.warn("Received empty query, ignoring");
                        throw new QueryException("Received empty query");
                    }

                    logger.info("Query: '{}'", query);

                    Optional<String[]> result = data.stream()
                            .filter(r -> r[0].trim().equalsIgnoreCase(query))
                            .findFirst();

                    if (result.isPresent()) {
                        MessageProtocol.send(out, (byte) 0x06, String.join(",", result.get()));
                    } else {
                        MessageProtocol.send(out, (byte) 0x07, null);
                    }

                } catch (SocketTimeoutException e) {
                    logger.info("Client timed out: {}", socket.getInetAddress());
                    socket.close();
                    break;
                } catch (java.io.EOFException e) {
                    logger.info("Client disconnected: {}", socket.getInetAddress());
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Client error", e);
        }
    }
}