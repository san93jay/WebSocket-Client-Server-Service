package com.sanjay;

import com.sanjay.server.SocketServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebsocketServerServiceApplication {

	public static void main(String[] args) {

        SpringApplication.run(WebsocketServerServiceApplication.class, args)
                         .getBean(SocketServer.class)
                         .start();
        ;
	}

}
