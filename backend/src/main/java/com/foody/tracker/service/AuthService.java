package com.foody.tracker.service;

import com.foody.tracker.dto.LoginRequest;
import com.foody.tracker.dto.LoginResponse;
import com.foody.tracker.dto.RegisterRequest;
import com.foody.tracker.dto.UserResponse;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.exception.EmailAlreadyUsedException;
import com.foody.tracker.exception.InvalidCredentialsException;
import com.foody.tracker.repository.UserRepository;
import com.foody.tracker.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()), Role.CLIENT);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(jwtService.generateToken(user), jwtService.getExpirationSeconds(),
                UserResponse.from(user));
    }
}
