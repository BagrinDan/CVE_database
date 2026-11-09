package com.example.data_warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.ServerSocket;

@SpringBootApplication
public class DataWarehouseApplication {

	public static void main(String[] args) {
		if (System.getenv("SERVER_PORT") == null) {
			System.setProperty("server.port", String.valueOf(findFreePort(10000)));
		}
		SpringApplication.run(DataWarehouseApplication.class, args);
	}

	/** Первый свободный порт, начиная с start. */
	private static int findFreePort(int start) {
		for (int port = start; port < start + 100; port++) {
			try (ServerSocket socket = new ServerSocket(port)) {
				return port;
			} catch (IOException ignored) {
			}
		}
		throw new IllegalStateException("No free port in " + start + ".." + (start + 99));
	}


}
