package com.example.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/second")
public class SecondController {

    @GetMapping("/day")
    public String day(@RequestHeader("second-request") String header) {
        System.out.println(header);
        return "Today is Monday";
    }
}
