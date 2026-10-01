package com.shopsphere.userservice.service;

import com.shopsphere.userservice.dto.AdminUserResponse;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks AdminUserService service;

    @Test
    @SuppressWarnings("unchecked")
    void search_mapsUsers_withoutExposingPasswords() {
        User user = User.builder().id(7L).firstName("Jane").lastName("Doe").email("jane@example.com")
                .passwordHash("$2a$10$secret").role(Role.CUSTOMER).enabled(true).emailVerified(false)
                .createdAt(Instant.parse("2026-09-01T10:00:00Z")).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(user)));

        Page<AdminUserResponse> page = service.search("jane", Role.CUSTOMER, true, false, pageable);

        AdminUserResponse row = page.getContent().get(0);
        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.email()).isEqualTo("jane@example.com");
        assertThat(row.enabled()).isTrue();
        assertThat(row.emailVerified()).isFalse();
        assertThat(AdminUserResponse.class.getRecordComponents())
                .noneMatch(c -> c.getName().toLowerCase().contains("password"));
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void escapeLike_neutralisesWildcards() {
        assertThat(AdminUserService.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
    }

    @Test
    void spec_isAlwaysBuilt() {
        assertThat(AdminUserService.spec(null, null, null, null)).isNotNull();
        assertThat(AdminUserService.spec("42", Role.ADMIN, true, true)).isNotNull();
    }
}
