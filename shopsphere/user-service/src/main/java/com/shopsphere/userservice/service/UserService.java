package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.UserContactResponse;
import com.shopsphere.userservice.dto.UserResponse;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.exception.UserNotFoundException;
import com.shopsphere.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** US39 — used by notification-service to address emails. */
    @Transactional(readOnly = true)
    public UserContactResponse getContact(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id=" + userId));
        return new UserContactResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getEnabled());
    }
}
