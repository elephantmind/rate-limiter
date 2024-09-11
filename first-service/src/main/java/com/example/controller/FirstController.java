package com.example.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/first")
public class FirstController {

	@GetMapping("/weather")
	public String weather(@RequestHeader("first-request") String header) {
		System.out.println(header);
		return "It is sunny";
	}
}
