package com.stingercheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.cache.annotation.EnableCaching
public class StingerCheckApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(StingerCheckApiApplication.class, args);
	}

}
