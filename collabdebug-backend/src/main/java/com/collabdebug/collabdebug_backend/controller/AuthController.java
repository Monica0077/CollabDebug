package com.collabdebug.collabdebug_backend.controller;

import com.collabdebug.collabdebug_backend.model.User;
import com.collabdebug.collabdebug_backend.repository.UserRepository;
import com.collabdebug.collabdebug_backend.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;
     @Autowired
    private PasswordEncoder passwordEncoder;
     @Autowired
    private JwtService  jwtService;

     @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user)
     {
         if(userRepository.findByUsername(user.getUsername()).isPresent())
         {
             return ResponseEntity.badRequest().body("Username already Exists");
         }
         user.setPassword(passwordEncoder.encode(user.getPassword()));
         userRepository.save(user);
         return ResponseEntity.ok("User Registered Successfully");
     }

     @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user)
     {
         Optional<User> existingUser= userRepository.findByUsername(user.getUsername());
         if(existingUser.isEmpty() || !passwordEncoder.matches(user.getPassword(),existingUser.get().getPassword()))
         {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Credentials");
         }
         String token= jwtService.generateToken(existingUser.get());
         return ResponseEntity.ok(Map.of("token",token));
     }



}
