package com.example.chat_application;

import org.springframework.boot.SpringApplication;

public class TestChatApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChatApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
