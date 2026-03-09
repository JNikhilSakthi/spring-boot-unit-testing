package com.ecommerce.unittesting.webmvctest;

import com.ecommerce.unittesting.controller.UserController;
import com.ecommerce.unittesting.dto.UserRequest;
import com.ecommerce.unittesting.dto.UserResponse;
import com.ecommerce.unittesting.exception.DuplicateResourceException;
import com.ecommerce.unittesting.exception.ResourceNotFoundException;
import com.ecommerce.unittesting.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest — Tests ONLY the UserController web layer.
 * UserService is mocked with @MockBean.
 */
@WebMvcTest(controllers = UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRequest userRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        userResponse = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== POST — Create User ====================

    @Test
    @DisplayName("POST /api/users — Should create user and return 201")
    void createUser_ShouldReturn201() throws Exception {
        when(userService.createUser(any(UserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /api/users — Should return 400 when email is invalid")
    void createUser_WithInvalidEmail_ShouldReturn400() throws Exception {
        UserRequest invalidRequest = UserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("not-an-email")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /api/users — Should return 400 when firstName is blank")
    void createUser_WithBlankFirstName_ShouldReturn400() throws Exception {
        UserRequest invalidRequest = UserRequest.builder()
                .firstName("")
                .lastName("Doe")
                .email("john@example.com")
                .phone("9876543210")
                .role("CUSTOMER")
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    @DisplayName("POST /api/users — Should return 409 when email already exists")
    void createUser_WithDuplicateEmail_ShouldReturn409() throws Exception {
        when(userService.createUser(any(UserRequest.class)))
                .thenThrow(new DuplicateResourceException("User already exists with email: john@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already exists with email: john@example.com"));
    }

    // ==================== GET — Get By ID ====================

    @Test
    @DisplayName("GET /api/users/1 — Should return user and 200")
    void getUserById_ShouldReturn200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(userResponse);

        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /api/users/99 — Should return 404 when not found")
    void getUserById_WhenNotExists_ShouldReturn404() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 99"));
    }

    // ==================== GET — Get By Email ====================

    @Test
    @DisplayName("GET /api/users/email/john@example.com — Should return user")
    void getUserByEmail_ShouldReturn200() throws Exception {
        when(userService.getUserByEmail("john@example.com")).thenReturn(userResponse);

        mockMvc.perform(get("/api/users/email/{email}", "john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    // ==================== GET — Get All ====================

    @Test
    @DisplayName("GET /api/users — Should return user list and 200")
    void getAllUsers_ShouldReturn200() throws Exception {
        UserResponse user2 = UserResponse.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .role("ADMIN")
                .build();

        when(userService.getAllUsers()).thenReturn(Arrays.asList(userResponse, user2));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    // ==================== GET — By Role ====================

    @Test
    @DisplayName("GET /api/users/role/CUSTOMER — Should return filtered users")
    void getUsersByRole_ShouldReturn200() throws Exception {
        when(userService.getUsersByRole("CUSTOMER")).thenReturn(List.of(userResponse));

        mockMvc.perform(get("/api/users/role/{role}", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"));
    }

    // ==================== GET — Search ====================

    @Test
    @DisplayName("GET /api/users/search?firstName=John — Should return matching users")
    void searchUsers_ShouldReturn200() throws Exception {
        when(userService.searchUsers("John")).thenReturn(List.of(userResponse));

        mockMvc.perform(get("/api/users/search")
                        .param("firstName", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName", containsString("John")));
    }

    // ==================== PUT — Update ====================

    @Test
    @DisplayName("PUT /api/users/1 — Should update user and return 200")
    void updateUser_ShouldReturn200() throws Exception {
        UserResponse updatedResponse = UserResponse.builder()
                .id(1L)
                .firstName("Johnny")
                .lastName("Doe Updated")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("ADMIN")
                .build();

        when(userService.updateUser(eq(1L), any(UserRequest.class))).thenReturn(updatedResponse);

        UserRequest updateRequest = UserRequest.builder()
                .firstName("Johnny")
                .lastName("Doe Updated")
                .email("johnny@example.com")
                .phone("1111111111")
                .role("ADMIN")
                .build();

        mockMvc.perform(put("/api/users/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ==================== DELETE ====================

    @Test
    @DisplayName("DELETE /api/users/1 — Should delete and return 204")
    void deleteUser_ShouldReturn204() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(1L);
    }

    @Test
    @DisplayName("DELETE /api/users/99 — Should return 404 when not found")
    void deleteUser_WhenNotExists_ShouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("User not found with id: 99"))
                .when(userService).deleteUser(99L);

        mockMvc.perform(delete("/api/users/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
