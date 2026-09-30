package com.shopsphere.notificationservice.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Resolver + real UserDirectoryClient against a mocked user-service. */
class RecipientResolverTest {

    private MockRestServiceServer userService;
    private RecipientResolver resolver;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        userService = MockRestServiceServer.bindTo(builder).build();
        UserDirectoryClient client = new UserDirectoryClient(builder, "http://user-service", "internal-token");

        NotificationProperties props = new NotificationProperties();
        props.setAdminEmail("ops@shopsphere.test");
        resolver = new RecipientResolver(client, props);
    }

    @Test
    void knownUser_resolvesToEmailAndFirstName_sendingInternalToken() {
        userService.expect(requestTo("http://user-service/internal/v1/users/7/contact"))
                .andExpect(header("X-Internal-Token", "internal-token"))
                .andRespond(withSuccess("""
                        {"id":7,"email":"jane@example.com","firstName":"Jane","enabled":true}
                        """, MediaType.APPLICATION_JSON));

        var recipient = resolver.resolve(7L).orElseThrow();

        assertThat(recipient.email()).isEqualTo("jane@example.com");
        assertThat(recipient.displayName()).isEqualTo("Jane");
    }

    @Test
    void unknownUser_resolvesToEmpty() {
        userService.expect(requestTo("http://user-service/internal/v1/users/8/contact"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(resolver.resolve(8L)).isEmpty();
    }

    @Test
    void disabledUser_isNotEmailed() {
        userService.expect(requestTo("http://user-service/internal/v1/users/9/contact"))
                .andRespond(withSuccess("""
                        {"id":9,"email":"gone@example.com","firstName":"Gone","enabled":false}
                        """, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(9L)).isEmpty();
    }

    @Test
    void systemAlert_goesToAdminAddress() {
        var recipient = resolver.resolve(null).orElseThrow();

        assertThat(recipient.email()).isEqualTo("ops@shopsphere.test");
    }
}
