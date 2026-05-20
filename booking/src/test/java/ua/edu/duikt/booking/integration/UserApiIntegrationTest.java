package ua.edu.duikt.booking.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ua.edu.duikt.booking.dto.UserRequestDto;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiIntegrationTest extends BaseApiIntegrationTest {

    private static final HttpServer KEYCLOAK_SERVER = startFakeKeycloak();

    @DynamicPropertySource
    static void registerKeycloakProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://localhost:" + KEYCLOAK_SERVER.getAddress().getPort();

        registry.add("keycloak.server-url", () -> baseUrl);
        registry.add("keycloak.realm", () -> "booking-realm");
        registry.add("keycloak.admin-realm", () -> "master");
        registry.add("keycloak.admin-client-id", () -> "admin-cli");
        registry.add("keycloak.admin-client-secret", () -> "");
        registry.add("keycloak.admin-username", () -> "admin");
        registry.add("keycloak.admin-password", () -> "admin");
    }

    @AfterAll
    static void stopServer() {
        KEYCLOAK_SERVER.stop(0);
    }

    @Test
    void ts15_shouldCreateUserAsAdmin() throws Exception {
        persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);

        UserRequestDto request = new UserRequestDto(
                "Марія",
                "Бондар",
                "bondar1@university.local",
                "bondar1@university.local",
                UserRole.USER,
                UserStatus.ACTIVE
        );

        mockMvc.perform(post("/api/users")
                        .with(userJwt("admin@university.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Марія"))
                .andExpect(jsonPath("$.lastName").value("Бондар"))
                .andExpect(jsonPath("$.email").value("bondar1@university.local"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ts16_shouldBlockUserAsAdmin() throws Exception {
        persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);
        User targetUser = persistUser("bondar1@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("501", true);

        Reservation reservation = persistReservation(
                targetUser,
                classroom,
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара",
                ReservationStatus.CREATED
        );

        mockMvc.perform(patch("/api/users/{id}/block", targetUser.getId())
                        .with(userJwt("admin@university.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.email").value("bondar1@university.local"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        User blockedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        Reservation canceledReservation = reservationRepository.findById(reservation.getId()).orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(UserStatus.BLOCKED, blockedUser.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.CANCELED, canceledReservation.getStatus());
    }


    private static HttpServer startFakeKeycloak() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

            server.createContext("/realms/master/protocol/openid-connect/token",
                    exchange -> sendJson(exchange, 200, "{\"access_token\":\"admin-token\"}"));

            server.createContext("/admin/realms/booking-realm/users", exchange -> {
                String method = exchange.getRequestMethod();

                if ("POST".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().add(
                            "Location",
                            "http://localhost:" + server.getAddress().getPort() + "/admin/realms/booking-realm/users/kc-user-1"
                    );
                    exchange.sendResponseHeaders(201, -1);
                    exchange.close();
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, "[{\"id\":\"kc-user-1\"}]");
                    return;
                }

                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            });

            server.createContext("/admin/realms/booking-realm/users/kc-user-1", exchange -> {
                String method = exchange.getRequestMethod();

                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, """
                            {
                              "id":"kc-user-1",
                              "username":"bondar1@university.local",
                              "email":"bondar1@university.local",
                              "firstName":"Марія",
                              "lastName":"Бондар",
                              "enabled":true
                            }
                            """);
                    return;
                }

                if ("PUT".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }

                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            });

            server.createContext("/admin/realms/booking-realm/users/kc-user-1/reset-password", exchange -> {
                if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                }
            });

            server.createContext("/admin/realms/booking-realm/users/kc-user-1/logout", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                }
            });

            server.createContext("/admin/realms/booking-realm/roles/USER",
                    exchange -> sendJson(exchange, 200, "{\"id\":\"role-user-id\",\"name\":\"USER\"}"));

            server.createContext("/admin/realms/booking-realm/roles/ADMIN",
                    exchange -> sendJson(exchange, 200, "{\"id\":\"role-admin-id\",\"name\":\"ADMIN\"}"));

            server.createContext("/admin/realms/booking-realm/users/kc-user-1/role-mappings/realm", exchange -> {
                String method = exchange.getRequestMethod();

                if ("GET".equalsIgnoreCase(method)) {
                    sendJson(exchange, 200, "[]");
                    return;
                }

                if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }

                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            });

            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося запустити fake Keycloak для інтеграційних тестів", e);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}