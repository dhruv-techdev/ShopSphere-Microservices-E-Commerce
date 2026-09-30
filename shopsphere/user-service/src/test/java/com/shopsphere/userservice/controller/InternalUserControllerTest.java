package com.shopsphere.userservice.controller;

import com.shopsphere.userservice.dto.UserContactResponse;
import com.shopsphere.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock UserService userService;

    @Test
    void correctToken_returnsContact() {
        var controller = new InternalUserController(userService, "s3cret");
        when(userService.getContact(7L)).thenReturn(new UserContactResponse(7L, "jane@example.com", "Jane", true));

        var response = controller.contact(7L, "s3cret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo("jane@example.com");
    }

    @Test
    void wrongOrMissingToken_isRejected() {
        var controller = new InternalUserController(userService, "s3cret");

        assertThat(controller.contact(7L, "nope").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.contact(7L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userService);
    }

    @Test
    void noTokenConfigured_failsClosed() {
        var controller = new InternalUserController(userService, "");

        assertThat(controller.contact(7L, "").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userService);
    }
}
