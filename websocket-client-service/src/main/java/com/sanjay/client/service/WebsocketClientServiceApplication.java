package com.sanjay.client.service;

import com.sanjay.client.service.client.SocketClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebsocketClientServiceApplication {

	public static void main(String[] args) {

        SpringApplication.run(WebsocketClientServiceApplication.class, args)
                         .getBean(SocketClient.class)
                         .start();

    }

}
