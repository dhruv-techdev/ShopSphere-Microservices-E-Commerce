package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.AdminUserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** US46 — account search for the admin console. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> search(String q, Role role, Boolean enabled, Boolean emailVerified, Pageable pageable) {
        return userRepository.findAll(spec(q, role, enabled, emailVerified), pageable).map(AdminUserResponse::from);
    }

    /** q matches email, first/last/full name (case-insensitive) or an exact numeric id. */
    static Specification<User> spec(String q, Role role, Boolean enabled, Boolean emailVerified) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String term = q.trim();
            String like = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> {
                var fullName = cb.concat(cb.concat(root.<String>get("firstName"), " "), root.<String>get("lastName"));
                var byText = cb.or(
                        cb.like(cb.lower(root.<String>get("email")), like, '\\'),
                        cb.like(cb.lower(fullName), like, '\\'));
                return term.matches("\\d{1,18}")
                        ? cb.or(byText, cb.equal(root.get("id"), Long.parseLong(term)))
                        : byText;
            });
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }
        if (emailVerified != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("emailVerified"), emailVerified));
        }
        return spec;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
