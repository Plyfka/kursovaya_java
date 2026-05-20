package ua.edu.duikt.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.dto.UserMeDto;
import ua.edu.duikt.booking.dto.UserRequestDto;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ReservationRepository;
import ua.edu.duikt.booking.repository.UserRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.admin-realm:master}")
    private String keycloakAdminRealm;

    @Value("${keycloak.admin-client-id}")
    private String keycloakAdminClientId;

    @Value("${keycloak.admin-client-secret:}")
    private String keycloakAdminClientSecret;

    @Value("${keycloak.admin-username:}")
    private String keycloakAdminUsername;

    @Value("${keycloak.admin-password:}")
    private String keycloakAdminPassword;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з id=" + id + " не знайдено"));
    }

    public UserMeDto getCurrentUserInfo(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з email=" + email + " не знайдено"));

        return new UserMeDto(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }

    @Transactional
    public User createUser(UserRequestDto request) {
        validateRequestFields(request, true);

        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Користувач з email=" + normalizedEmail + " вже існує");
        }

        String rawPassword = extractRequiredPassword(request.password());

        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(resolveRole(request.role()));
        user.setStatus(resolveStatus(request.status()));

        User savedUser = userRepository.save(user);
        createKeycloakUser(savedUser, rawPassword);
        return savedUser;
    }

    @Transactional
    public User updateUser(Long id, UserRequestDto request) {
        validateRequestFields(request, false);

        User user = getUserById(id);
        String previousEmail = user.getEmail();
        String normalizedEmail = normalizeEmail(request.email());

        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Користувач з email=" + normalizedEmail + " вже існує");
        }

        String rawPassword = extractOptionalPassword(request.password());

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(normalizedEmail);

        if (rawPassword != null) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }

        user.setRole(request.role() != null ? request.role() : user.getRole());
        user.setStatus(request.status() != null ? request.status() : user.getStatus());

        User savedUser = userRepository.save(user);
        syncUserToKeycloak(savedUser, previousEmail, rawPassword);
        return savedUser;
    }

    @Transactional
    public User blockUser(Long id) {
        User user = getUserById(id);

        if (user.getStatus() == UserStatus.BLOCKED) {
            ensureKeycloakUserDisabled(user.getEmail());
            return user;
        }

        List<Reservation> reservationsToCancel = reservationRepository.findActiveAndFutureReservationsByUserId(
                id,
                LocalDate.now(),
                LocalTime.now(),
                ReservationStatus.CREATED
        );

        reservationsToCancel.forEach(reservation -> reservation.setStatus(ReservationStatus.CANCELED));
        reservationRepository.saveAll(reservationsToCancel);

        user.setStatus(UserStatus.BLOCKED);
        User savedUser = userRepository.save(user);
        ensureKeycloakUserDisabled(savedUser.getEmail());
        return savedUser;
    }

    private void validateRequestFields(UserRequestDto request, boolean passwordRequired) {
        if (request == null) {
            throw new BadRequestException("Тіло запиту є обов'язковим");
        }
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new BadRequestException("Ім'я користувача є обов'язковим");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new BadRequestException("Прізвище користувача є обов'язковим");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email користувача є обов'язковим");
        }
        if (passwordRequired && (request.password() == null || request.password().isBlank())) {
            throw new BadRequestException("Пароль є обов'язковим");
        }
    }

    private String extractRequiredPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadRequestException("Пароль є обов'язковим");
        }
        return rawPassword.trim();
    }

    private String extractOptionalPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        return rawPassword.trim();
    }

    private UserRole resolveRole(UserRole role) {
        return role != null ? role : UserRole.USER;
    }

    private UserStatus resolveStatus(UserStatus status) {
        return status != null ? status : UserStatus.ACTIVE;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void createKeycloakUser(User user, String rawPassword) {
        String adminToken = getKeycloakAdminToken();

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("username", user.getEmail());
            payload.put("email", user.getEmail());
            payload.put("firstName", user.getFirstName());
            payload.put("lastName", user.getLastName());
            payload.put("enabled", user.getStatus() != UserStatus.BLOCKED);
            payload.put("emailVerified", false);

            HttpResponse<String> response = sendJsonRequest(
                    "POST",
                    adminUsersUrl(),
                    adminToken,
                    objectMapper.writeValueAsString(payload)
            );

            if (response.statusCode() == HttpStatus.CONFLICT.value()) {
                throw new ConflictException("Користувач уже існує в Keycloak: " + user.getEmail());
            }
            if (response.statusCode() != HttpStatus.CREATED.value()) {
                throw buildKeycloakException(response, "Не вдалося створити користувача в Keycloak");
            }

            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("Keycloak не повернув Location для нового користувача"));
            String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);

            resetKeycloakPassword(keycloakUserId, rawPassword, adminToken);
            syncKeycloakUserRole(keycloakUserId, user.getRole(), adminToken);

            if (user.getStatus() == UserStatus.BLOCKED) {
                logoutKeycloakUser(keycloakUserId, adminToken);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Не вдалося синхронізувати користувача з Keycloak", e);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося синхронізувати користувача з Keycloak", e);
        }
    }

    private void syncUserToKeycloak(User user, String previousEmail, String rawPassword) {
        String adminToken = getKeycloakAdminToken();

        try {
            String keycloakUserId = findKeycloakUserIdByEmail(previousEmail, adminToken);
            if (keycloakUserId == null && !previousEmail.equalsIgnoreCase(user.getEmail())) {
                keycloakUserId = findKeycloakUserIdByEmail(user.getEmail(), adminToken);
            }

            if (keycloakUserId == null) {
                if (rawPassword == null) {
                    throw new BadRequestException("Користувача не знайдено в Keycloak. Передайте пароль, щоб створити його там.");
                }
                createKeycloakUser(user, rawPassword);
                return;
            }

            ObjectNode keycloakUser = getKeycloakUser(keycloakUserId, adminToken);
            keycloakUser.put("username", user.getEmail());
            keycloakUser.put("email", user.getEmail());
            keycloakUser.put("firstName", user.getFirstName());
            keycloakUser.put("lastName", user.getLastName());
            keycloakUser.put("enabled", user.getStatus() != UserStatus.BLOCKED);

            HttpResponse<String> updateResponse = sendJsonRequest(
                    "PUT",
                    adminUsersUrl() + "/" + keycloakUserId,
                    adminToken,
                    objectMapper.writeValueAsString(keycloakUser)
            );

            if (updateResponse.statusCode() != HttpStatus.NO_CONTENT.value()) {
                throw buildKeycloakException(updateResponse, "Не вдалося оновити користувача в Keycloak");
            }

            if (rawPassword != null) {
                resetKeycloakPassword(keycloakUserId, rawPassword, adminToken);
            }

            syncKeycloakUserRole(keycloakUserId, user.getRole(), adminToken);

            if (user.getStatus() == UserStatus.BLOCKED) {
                logoutKeycloakUser(keycloakUserId, adminToken);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Не вдалося синхронізувати оновлення користувача з Keycloak", e);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося синхронізувати оновлення користувача з Keycloak", e);
        }
    }

    private void ensureKeycloakUserDisabled(String email) {
        String adminToken = getKeycloakAdminToken();

        try {
            String keycloakUserId = findKeycloakUserIdByEmail(email, adminToken);
            if (keycloakUserId == null) {
                return;
            }

            ObjectNode keycloakUser = getKeycloakUser(keycloakUserId, adminToken);
            keycloakUser.put("enabled", false);

            HttpResponse<String> disableResponse = sendJsonRequest(
                    "PUT",
                    adminUsersUrl() + "/" + keycloakUserId,
                    adminToken,
                    objectMapper.writeValueAsString(keycloakUser)
            );

            if (disableResponse.statusCode() != HttpStatus.NO_CONTENT.value()) {
                throw buildKeycloakException(disableResponse, "Не вдалося заблокувати користувача в Keycloak");
            }

            logoutKeycloakUser(keycloakUserId, adminToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Не вдалося заблокувати користувача в Keycloak", e);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося заблокувати користувача в Keycloak", e);
        }
    }

    private String getKeycloakAdminToken() {
        validateKeycloakConfiguration();

        try {
            String form;
            if (!keycloakAdminClientSecret.isBlank()) {
                form = formData(
                        "grant_type", "client_credentials",
                        "client_id", keycloakAdminClientId,
                        "client_secret", keycloakAdminClientSecret
                );
            } else {
                if (keycloakAdminUsername.isBlank() || keycloakAdminPassword.isBlank()) {
                    throw new IllegalStateException("Не задано дані доступу до Keycloak admin API");
                }
                form = formData(
                        "grant_type", "password",
                        "client_id", keycloakAdminClientId,
                        "username", keycloakAdminUsername,
                        "password", keycloakAdminPassword
                );
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw buildKeycloakException(response, "Не вдалося отримати admin token від Keycloak");
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode accessToken = json.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new IllegalStateException("Keycloak не повернув access_token");
            }
            return accessToken.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Не вдалося підключитися до Keycloak", e);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося підключитися до Keycloak", e);
        }
    }

    private ObjectNode getKeycloakUser(String keycloakUserId, String adminToken) throws IOException, InterruptedException {
        HttpResponse<String> response = sendRequest(
                "GET",
                adminUsersUrl() + "/" + keycloakUserId,
                adminToken,
                null,
                "application/json"
        );

        if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
            throw new ResourceNotFoundException("Користувача не знайдено в Keycloak");
        }
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw buildKeycloakException(response, "Не вдалося отримати користувача з Keycloak");
        }

        return (ObjectNode) objectMapper.readTree(response.body());
    }

    private String findKeycloakUserIdByEmail(String email, String adminToken) throws IOException, InterruptedException {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return null;
        }

        String url = adminUsersUrl()
                + "?email=" + urlEncode(normalizedEmail)
                + "&exact=true"
                + "&max=2";

        HttpResponse<String> response = sendRequest("GET", url, adminToken, null, "application/json");
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw buildKeycloakException(response, "Не вдалося знайти користувача в Keycloak");
        }

        JsonNode users = objectMapper.readTree(response.body());
        if (!users.isArray() || users.isEmpty()) {
            return null;
        }

        JsonNode firstUser = users.get(0);
        JsonNode idNode = firstUser.get("id");
        return idNode == null || idNode.asText().isBlank() ? null : idNode.asText();
    }

    private void resetKeycloakPassword(String keycloakUserId, String rawPassword, String adminToken) throws IOException, InterruptedException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "password");
        payload.put("temporary", false);
        payload.put("value", rawPassword);

        HttpResponse<String> response = sendJsonRequest(
                "PUT",
                adminUsersUrl() + "/" + keycloakUserId + "/reset-password",
                adminToken,
                objectMapper.writeValueAsString(payload)
        );

        if (response.statusCode() != HttpStatus.NO_CONTENT.value()) {
            throw buildKeycloakException(response, "Не вдалося встановити пароль користувачу в Keycloak");
        }
    }

    private void syncKeycloakUserRole(String keycloakUserId, UserRole role, String adminToken) throws IOException, InterruptedException {
        List<JsonNode> managedRoles = loadManagedKeycloakRoles(adminToken);
        List<JsonNode> currentlyAssignedManagedRoles = getAssignedManagedRoles(keycloakUserId, managedRoles, adminToken);

        if (!currentlyAssignedManagedRoles.isEmpty()) {
            HttpResponse<String> deleteResponse = sendJsonRequest(
                    "DELETE",
                    adminUsersUrl() + "/" + keycloakUserId + "/role-mappings/realm",
                    adminToken,
                    objectMapper.writeValueAsString(currentlyAssignedManagedRoles)
            );

            if (deleteResponse.statusCode() != HttpStatus.NO_CONTENT.value()) {
                throw buildKeycloakException(deleteResponse, "Не вдалося очистити ролі користувача в Keycloak");
            }
        }

        JsonNode targetRole = findMatchingManagedRole(managedRoles, role)
                .orElseThrow(() -> new ResourceNotFoundException("Роль " + role.name() + " не знайдена в Keycloak"));

        ArrayNode payload = objectMapper.createArrayNode();
        payload.add(targetRole);

        HttpResponse<String> addResponse = sendJsonRequest(
                "POST",
                adminUsersUrl() + "/" + keycloakUserId + "/role-mappings/realm",
                adminToken,
                objectMapper.writeValueAsString(payload)
        );

        if (addResponse.statusCode() != HttpStatus.NO_CONTENT.value()) {
            throw buildKeycloakException(addResponse, "Не вдалося призначити роль користувачу в Keycloak");
        }
    }

    private List<JsonNode> loadManagedKeycloakRoles(String adminToken) throws IOException, InterruptedException {
        List<JsonNode> roles = new ArrayList<>();

        JsonNode userRole = findKeycloakRealmRole(UserRole.USER, adminToken);
        if (userRole != null) {
            roles.add(userRole);
        }

        JsonNode adminRole = findKeycloakRealmRole(UserRole.ADMIN, adminToken);
        if (adminRole != null) {
            roles.add(adminRole);
        }

        return roles;
    }

    private JsonNode findKeycloakRealmRole(UserRole role, String adminToken) throws IOException, InterruptedException {
        HttpResponse<String> uppercaseResponse = sendRequest(
                "GET",
                adminRolesUrl() + "/" + role.name(),
                adminToken,
                null,
                "application/json"
        );

        if (uppercaseResponse.statusCode() == HttpStatus.OK.value()) {
            return objectMapper.readTree(uppercaseResponse.body());
        }
        if (uppercaseResponse.statusCode() != HttpStatus.NOT_FOUND.value()) {
            throw buildKeycloakException(uppercaseResponse, "Не вдалося отримати роль " + role.name() + " з Keycloak");
        }

        HttpResponse<String> lowercaseResponse = sendRequest(
                "GET",
                adminRolesUrl() + "/" + role.name().toLowerCase(Locale.ROOT),
                adminToken,
                null,
                "application/json"
        );

        if (lowercaseResponse.statusCode() == HttpStatus.OK.value()) {
            return objectMapper.readTree(lowercaseResponse.body());
        }
        if (lowercaseResponse.statusCode() == HttpStatus.NOT_FOUND.value()) {
            return null;
        }

        throw buildKeycloakException(lowercaseResponse, "Не вдалося отримати роль " + role.name() + " з Keycloak");
    }

    private java.util.Optional<JsonNode> findMatchingManagedRole(List<JsonNode> managedRoles, UserRole role) {
        return managedRoles.stream()
                .filter(roleNode -> role.name().equalsIgnoreCase(roleNode.path("name").asText()))
                .findFirst();
    }

    private List<JsonNode> getAssignedManagedRoles(String keycloakUserId,
                                                   List<JsonNode> managedRoles,
                                                   String adminToken) throws IOException, InterruptedException {
        if (managedRoles.isEmpty()) {
            return List.of();
        }

        HttpResponse<String> response = sendRequest(
                "GET",
                adminUsersUrl() + "/" + keycloakUserId + "/role-mappings/realm",
                adminToken,
                null,
                "application/json"
        );

        if (response.statusCode() != HttpStatus.OK.value()) {
            throw buildKeycloakException(response, "Не вдалося отримати ролі користувача з Keycloak");
        }

        JsonNode assignedRoles = objectMapper.readTree(response.body());
        if (!assignedRoles.isArray()) {
            return List.of();
        }

        List<String> managedRoleIds = managedRoles.stream()
                .map(role -> role.path("id").asText())
                .filter(id -> !id.isBlank())
                .toList();

        List<JsonNode> result = new ArrayList<>();
        assignedRoles.forEach(role -> {
            if (managedRoleIds.contains(role.path("id").asText())) {
                result.add(role);
            }
        });
        return result;
    }

    private void logoutKeycloakUser(String keycloakUserId, String adminToken) throws IOException, InterruptedException {
        HttpResponse<String> response = sendRequest(
                "POST",
                adminUsersUrl() + "/" + keycloakUserId + "/logout",
                adminToken,
                "",
                null
        );

        if (response.statusCode() != HttpStatus.NO_CONTENT.value()) {
            throw buildKeycloakException(response, "Не вдалося завершити сесії користувача в Keycloak");
        }
    }

    private HttpResponse<String> sendJsonRequest(String method, String url, String bearerToken, String body)
            throws IOException, InterruptedException {
        return sendRequest(method, url, bearerToken, body, "application/json");
    }

    private HttpResponse<String> sendRequest(String method,
                                             String url,
                                             String bearerToken,
                                             String body,
                                             String contentType) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + bearerToken);

        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }

        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            case "DELETE" -> builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            default -> throw new IllegalArgumentException("Непідтримуваний HTTP метод: " + method);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private RuntimeException buildKeycloakException(HttpResponse<String> response, String prefix) {
        String body = response.body();
        String message = body == null || body.isBlank() ? prefix : prefix + ": " + body;

        if (response.statusCode() == HttpStatus.CONFLICT.value()) {
            return new ConflictException(message);
        }
        if (response.statusCode() == HttpStatus.BAD_REQUEST.value()) {
            return new BadRequestException(message);
        }
        if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
            return new ResourceNotFoundException(message);
        }
        return new IllegalStateException(message);
    }

    private void validateKeycloakConfiguration() {
        if (keycloakServerUrl == null || keycloakServerUrl.isBlank()) {
            throw new IllegalStateException("Не задано keycloak.server-url");
        }
        if (keycloakRealm == null || keycloakRealm.isBlank()) {
            throw new IllegalStateException("Не задано keycloak.realm");
        }
        if (keycloakAdminClientId == null || keycloakAdminClientId.isBlank()) {
            throw new IllegalStateException("Не задано keycloak.admin-client-id");
        }
    }

    private String tokenUrl() {
        return normalizeBaseUrl(keycloakServerUrl)
                + "/realms/" + keycloakAdminRealm + "/protocol/openid-connect/token";
    }

    private String adminUsersUrl() {
        return normalizeBaseUrl(keycloakServerUrl)
                + "/admin/realms/" + keycloakRealm + "/users";
    }

    private String adminRolesUrl() {
        return normalizeBaseUrl(keycloakServerUrl)
                + "/admin/realms/" + keycloakRealm + "/roles";
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String formData(String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Форма повинна містити парну кількість ключів і значень");
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(values[i]))
                    .append('=')
                    .append(urlEncode(values[i + 1]));
        }
        return builder.toString();
    }
}
