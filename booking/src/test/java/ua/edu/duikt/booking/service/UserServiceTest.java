package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ua.edu.duikt.booking.dto.UserMeDto;
import ua.edu.duikt.booking.dto.UserRequestDto;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ReservationRepository;
import ua.edu.duikt.booking.repository.UserRepository;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private UserService userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "httpClient", httpClient);
        ReflectionTestUtils.setField(userService, "keycloakServerUrl", "http://localhost:808");
        ReflectionTestUtils.setField(userService, "keycloakRealm", "booking");
        ReflectionTestUtils.setField(userService, "keycloakAdminRealm", "master");
        ReflectionTestUtils.setField(userService, "keycloakAdminClientId", "admin-cli");
        ReflectionTestUtils.setField(userService, "keycloakAdminClientSecret", "test-secret");
        ReflectionTestUtils.setField(userService, "keycloakAdminUsername", "");
        ReflectionTestUtils.setField(userService, "keycloakAdminPassword", "");
    }

    @Test
    void getAllUsers_ShouldReturnAllUsers() {
        List<User> users = List.of(
                user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.ACTIVE),
                user(2L, "Олена", "Коваль", "olena@test.com", "hash", UserRole.ADMIN, UserStatus.ACTIVE)
        );

        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertEquals(users, result);
        verify(userRepository).findAll();
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        User user = user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertEquals(user, result);
        verify(userRepository).findById(1L);
    }

    @Test
    void getUserById_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
        verify(userRepository).findById(99L);
    }

    @Test
    void getCurrentUserInfo_ShouldNormalizeEmailAndReturnDto() {
        User user = user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));

        UserMeDto dto = userService.getCurrentUserInfo("  IVAN@Test.com  ");

        assertEquals("Іван", dto.firstName());
        assertEquals("Петренко", dto.lastName());
        assertEquals("ivan@test.com", dto.email());
        verify(userRepository).findByEmail("ivan@test.com");
    }

    @Test
    void getCurrentUserInfo_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUserInfo("missing@test.com"));
        verify(userRepository).findByEmail("missing@test.com");
    }

    @Test
    void createUser_ShouldHashPasswordSetDefaultsAndCreateUserInKeycloak() throws Exception {
        UserRequestDto request = new UserRequestDto(
                " Іван ",
                " Петренко ",
                "  IVAN@Test.com ",
                " secret123 ",
                null,
                null
        );

        when(userRepository.existsByEmail("ivan@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        mockCreateUserKeycloakFlow();

        User saved = userService.createUser(request);

        assertEquals("Іван", saved.getFirstName());
        assertEquals("Петренко", saved.getLastName());
        assertEquals("ivan@test.com", saved.getEmail());
        assertEquals(UserRole.USER, saved.getRole());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertNotEquals("secret123", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("secret123", saved.getPasswordHash()));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ivan@test.com", captor.getValue().getEmail());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
        assertTrue(passwordEncoder.matches("secret123", captor.getValue().getPasswordHash()));
    }

    @Test
    void createUser_ShouldThrowBadRequestException_WhenEmailAlreadyExists() {
        UserRequestDto request = new UserRequestDto("Іван", "Петренко", "ivan@test.com", "secret123", null, null);
        when(userRepository.existsByEmail("ivan@test.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> userService.createUser(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(httpClient);
    }

    @Test
    void createUser_ShouldThrowBadRequestException_WhenPasswordIsMissing() {
        UserRequestDto request = new UserRequestDto("Іван", "Петренко", "ivan@test.com", "   ", null, null);

        assertThrows(BadRequestException.class, () -> userService.createUser(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(httpClient);
    }

    @Test
    void updateUser_ShouldUpdateFieldsKeepCurrentHashWhenPasswordMissing_AndSyncToKeycloak() throws Exception {
        User existingUser = user(1L, "Старе", "Ім'я", "old@test.com", "old-hash", UserRole.USER, UserStatus.ACTIVE);
        UserRequestDto request = new UserRequestDto(
                " Нове ",
                " Прізвище ",
                "  NEW@test.com ",
                null,
                UserRole.ADMIN,
                UserStatus.ACTIVE
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockUpdateExistingKeycloakUserFlow();

        User updated = userService.updateUser(1L, request);

        assertEquals("Нове", updated.getFirstName());
        assertEquals("Прізвище", updated.getLastName());
        assertEquals("new@test.com", updated.getEmail());
        assertEquals("old-hash", updated.getPasswordHash());
        assertEquals(UserRole.ADMIN, updated.getRole());
        assertEquals(UserStatus.ACTIVE, updated.getStatus());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUser_ShouldHashNewPassword_WhenPasswordProvided() throws Exception {
        User existingUser = user(1L, "Іван", "Петренко", "ivan@test.com", "old-hash", UserRole.USER, UserStatus.ACTIVE);
        UserRequestDto request = new UserRequestDto(
                "Іван",
                "Петренко",
                "ivan@test.com",
                "new-secret",
                UserRole.USER,
                UserStatus.ACTIVE
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockUpdateExistingKeycloakUserWithPasswordFlow();

        User updated = userService.updateUser(1L, request);

        assertNotEquals("old-hash", updated.getPasswordHash());
        assertTrue(passwordEncoder.matches("new-secret", updated.getPasswordHash()));
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUser_ShouldThrowBadRequestException_WhenNewEmailAlreadyBelongsToAnotherUser() {
        User existingUser = user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.ACTIVE);
        UserRequestDto request = new UserRequestDto("Іван", "Петренко", "other@test.com", null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("other@test.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> userService.updateUser(1L, request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(httpClient);
    }

    @Test
    void updateUser_ShouldThrowBadRequestException_WhenUserNotFoundInKeycloakAndPasswordMissing() throws Exception {
        User existingUser = user(1L, "Іван", "Петренко", "old@test.com", "hash", UserRole.USER, UserStatus.ACTIVE);
        UserRequestDto request = new UserRequestDto("Іван", "Петренко", "new@test.com", null, UserRole.USER, UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockKeycloakTokenAndMissingUsersForUpdate();

        assertThrows(BadRequestException.class, () -> userService.updateUser(1L, request));

        verify(userRepository).save(existingUser);
    }

    @Test
    void blockUser_ShouldSetBlockedStatusCancelReservationsAndDisableUserInKeycloak() throws Exception {
        User user = user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.ACTIVE);
        Reservation reservation1 = Reservation.builder().id(10L).status(ReservationStatus.CREATED).build();
        Reservation reservation2 = Reservation.builder().id(11L).status(ReservationStatus.CREATED).build();
        List<Reservation> reservations = List.of(reservation1, reservation2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(reservationRepository.findActiveAndFutureReservationsByUserId(
                eq(1L), any(LocalDate.class), any(LocalTime.class), eq(ReservationStatus.CREATED)
        )).thenReturn(reservations);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockDisableKeycloakUserFlow();

        User blockedUser = userService.blockUser(1L);

        assertEquals(UserStatus.BLOCKED, blockedUser.getStatus());
        assertEquals(ReservationStatus.CANCELED, reservation1.getStatus());
        assertEquals(ReservationStatus.CANCELED, reservation2.getStatus());
        verify(reservationRepository).saveAll(reservations);
        verify(userRepository).save(user);
    }

    @Test
    void blockUser_ShouldOnlyEnsureKeycloakDisabled_WhenUserAlreadyBlocked() throws Exception {
        User user = user(1L, "Іван", "Петренко", "ivan@test.com", "hash", UserRole.USER, UserStatus.BLOCKED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        mockDisableKeycloakUserFlow();

        User result = userService.blockUser(1L);

        assertEquals(UserStatus.BLOCKED, result.getStatus());
        verify(reservationRepository, never()).findActiveAndFutureReservationsByUserId(anyLong(), any(), any(), any());
        verify(reservationRepository, never()).saveAll(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void blockUser_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.blockUser(77L));

        verifyNoInteractions(httpClient);
    }

    private void mockCreateUserKeycloakFlow() throws Exception {
        doReturn(
                responseJson(200, "{\"access_token\":\"admin-token\"}"),
                responseWithLocation(201, "http://localhost:808/admin/realms/booking-realm/users/kc-user-1"),
                response(204),
                responseJson(200, "{\"id\":\"role-user-id\",\"name\":\"USER\"}"),
                responseJson(200, "{\"id\":\"role-admin-id\",\"name\":\"ADMIN\"}"),
                responseJson(200, "[]"),
                response(204)
        ).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void mockUpdateExistingKeycloakUserFlow() throws Exception {
        doReturn(
                responseJson(200, "{\"access_token\":\"admin-token\"}"),
                responseJson(200, "[{\"id\":\"kc-user-1\"}]"),
                responseJson(200, "{\"id\":\"kc-user-1\",\"enabled\":true}"),
                response(204),
                responseJson(200, "{\"id\":\"role-user-id\",\"name\":\"USER\"}"),
                responseJson(200, "{\"id\":\"role-admin-id\",\"name\":\"ADMIN\"}"),
                responseJson(200, "[]"),
                response(204)
        ).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void mockUpdateExistingKeycloakUserWithPasswordFlow() throws Exception {
        doReturn(
                responseJson(200, "{\"access_token\":\"admin-token\"}"),
                responseJson(200, "[{\"id\":\"kc-user-1\"}]"),
                responseJson(200, "{\"id\":\"kc-user-1\",\"enabled\":true}"),
                response(204),
                response(204),
                responseJson(200, "{\"id\":\"role-user-id\",\"name\":\"USER\"}"),
                responseJson(200, "{\"id\":\"role-admin-id\",\"name\":\"ADMIN\"}"),
                responseJson(200, "[]"),
                response(204)
        ).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void mockKeycloakTokenAndMissingUsersForUpdate() throws Exception {
        doReturn(
                responseJson(200, "{\"access_token\":\"admin-token\"}"),
                responseJson(200, "[]"),
                responseJson(200, "[]")
        ).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void mockDisableKeycloakUserFlow() throws Exception {
        doReturn(
                responseJson(200, "{\"access_token\":\"admin-token\"}"),
                responseJson(200, "[{\"id\":\"kc-user-1\"}]"),
                responseJson(200, "{\"id\":\"kc-user-1\",\"enabled\":true}"),
                response(204),
                response(204)
        ).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseJson(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWithLocation(int status, String location) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of(location)), (a, b) -> true));
        return response;
    }

    private User user(Long id,
                      String firstName,
                      String lastName,
                      String email,
                      String passwordHash,
                      UserRole role,
                      UserStatus status) {
        return User.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .passwordHash(passwordHash)
                .role(role)
                .status(status)
                .build();
    }
}