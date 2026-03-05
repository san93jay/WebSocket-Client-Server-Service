package com.sanjay.client.service.service;

import com.sanjay.client.service.protocol.MessageProtocol;
import com.sanjay.client.service.protocol.MessageProtocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Service
public class HeartbeatService {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

    public void sendHeartbeat(DataOutputStream out, DataInputStream in) throws IOException {
        MessageProtocol.send(out, (byte) 0x08, null);

        Message response = MessageProtocol.receive(in);
        if (response.opcode == 0x09) {
            logger.info("Heartbeat OK (PONG received)");
        } else {
            logger.warn("Unexpected heartbeat response opcode {}", response.opcode);
        }
    }
}