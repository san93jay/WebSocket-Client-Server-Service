package com.sanjay.client.service.protocol;

import com.sanjay.client.service.exception.QueryException;
import com.sanjay.client.service.utils.CryptoUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MessageProtocol {

    public static class Message {
        public byte opcode;
        public String payload;     // decrypted text payload
        public byte[] rawPayload;  // raw binary payload
    }

    // Send encrypted text message (auth, CSV, queries, heartbeat)
    public static void send(DataOutputStream out, byte opcode, String payload) throws QueryException {
        try {
            String encrypted = payload != null ? CryptoUtil.encrypt(payload) : "";
            byte[] data = encrypted.getBytes(StandardCharsets.UTF_8);
            out.writeByte(opcode);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        } catch (Exception e) {
            throw new QueryException("Encryption failed", e);
        }
    }

    // Send raw binary message (RSA public key, encrypted AES key)
    public static void sendRaw(DataOutputStream out, byte opcode, byte[] rawData) throws IOException {
        byte[] data = rawData != null ? rawData : new byte[0];
        out.writeByte(opcode);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    // Receive encrypted text message
    public static Message receive(DataInputStream in) throws IOException {
        byte opcode = in.readByte();
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);

        String encryptedPayload = new String(data, StandardCharsets.UTF_8);
        try {
            String decrypted = encryptedPayload.isEmpty() ? "" : CryptoUtil.decrypt(encryptedPayload);
            Message msg = new Message();
            msg.opcode = opcode;
            msg.payload = decrypted;
            return msg;
        } catch (Exception e) {
            throw new IOException("Decryption failed", e);
        }
    }

    // Receive raw binary message
    public static Message receiveRaw(DataInputStream in) throws IOException {
        byte opcode = in.readByte();
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);

        Message msg = new Message();
        msg.opcode = opcode;
        msg.rawPayload = data;
        return msg;
    }
}