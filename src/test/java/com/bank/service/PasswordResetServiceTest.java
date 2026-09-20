package com.bank.service;

import com.bank.exception.AuthenticationException;
import com.bank.exception.InvalidPasswordException;
import com.bank.model.entity.PasswordResetToken;
import com.bank.model.entity.User;
import com.bank.model.enums.UserStatus;
import com.bank.model.repository.PasswordResetRepository;
import com.bank.model.repository.UserRepository;
import com.bank.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private PasswordResetRepository passwordResetRepository;

    private AuthService authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, accountService, passwordResetRepository);
        testUser = User.builder()
                .userId(3L)
                .username("senghak")
                .email("men.senghak@gmail.com")
                .fullName("MEN SENGHAK")
                .passwordHash(PasswordHasher.hash("OldPass@123"))
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Initiate password reset generates 6-digit OTP and 3-minute TTL")
    void testInitiatePasswordResetSuccess() {
        when(userRepository.findByUsername("men.senghak@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("men.senghak@gmail.com")).thenReturn(Optional.of(testUser));
        when(passwordResetRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthService.PasswordResetInitiationResult result = authService.initiatePasswordReset("men.senghak@gmail.com");

        assertNotNull(result);
        assertEquals(testUser, result.user());
        assertNotNull(result.otpCode());
        assertEquals(6, result.otpCode().length());
        assertTrue(result.otpCode().matches("\\d{6}"));
        assertEquals("m***k@gmail.com", result.maskedEmail());
        assertTrue(result.expiresAt().isAfter(LocalDateTime.now().plusMinutes(2)));

        verify(passwordResetRepository).invalidateAllForUser(3L);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertEquals(3L, saved.getUserId());
        assertEquals(result.otpCode(), saved.getOtpCode());
        assertEquals(0, saved.getAttempts());
        assertFalse(saved.getIsUsed());
    }

    @Test
    @DisplayName("Initiate password reset throws friendly error on unknown credentials")
    void testInitiatePasswordResetUserNotFound() {
        when(userRepository.findByUsername("unknown@bank.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@bank.com")).thenReturn(Optional.empty());

        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.initiatePasswordReset("unknown@bank.com")
        );
        assertEquals("No account registered with provided credentials.", ex.getMessage());
    }

    @Test
    @DisplayName("Verify OTP succeeds with matching OTP code")
    void testVerifyOtpSuccess() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenId(10L)
                .userId(3L)
                .otpCode("849201")
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .isUsed(false)
                .build();

        when(passwordResetRepository.findLatestActiveToken(3L)).thenReturn(Optional.of(token));

        boolean valid = authService.verifyOtp(3L, "849201");
        assertTrue(valid);
        verify(passwordResetRepository, never()).updateAttempts(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Verify OTP fails with wrong code and increments attempt count")
    void testVerifyOtpFailureIncrementsAttempts() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenId(10L)
                .userId(3L)
                .otpCode("849201")
                .attempts(1)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .isUsed(false)
                .build();

        when(passwordResetRepository.findLatestActiveToken(3L)).thenReturn(Optional.of(token));

        boolean valid = authService.verifyOtp(3L, "111111");
        assertFalse(valid);
        verify(passwordResetRepository).updateAttempts(10L, 2);
    }

    @Test
    @DisplayName("Verify OTP fails if token has 3 or more failed attempts")
    void testVerifyOtpMaxAttemptsExceeded() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenId(10L)
                .userId(3L)
                .otpCode("849201")
                .attempts(3)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .isUsed(false)
                .build();

        when(passwordResetRepository.findLatestActiveToken(3L)).thenReturn(Optional.of(token));

        boolean valid = authService.verifyOtp(3L, "849201");
        assertFalse(valid);
    }

    @Test
    @DisplayName("Reset password with OTP validates complexity and updates password hash")
    void testResetPasswordWithOtpSuccess() {
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenId(10L)
                .userId(3L)
                .otpCode("849201")
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .isUsed(false)
                .build();

        when(passwordResetRepository.findByUserIdAndOtp(3L, "849201")).thenReturn(Optional.of(token));
        when(userRepository.findById(3L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPasswordWithOtp(3L, "849201", "SecurePass@2026", "SecurePass@2026");

        verify(passwordResetRepository).markAsUsed(10L);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User updated = userCaptor.getValue();
        assertTrue(PasswordHasher.verify("SecurePass@2026", updated.getPasswordHash()));
    }

    @Test
    @DisplayName("Reset password with OTP fails if passwords do not match")
    void testResetPasswordMismatch() {
        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.resetPasswordWithOtp(3L, "849201", "Pass@1234", "Pass@5678")
        );
        assertEquals("Passwords do not match", ex.getMessage());
    }

    @Test
    @DisplayName("Reset password with OTP fails if password complexity fails")
    void testResetPasswordComplexityFailure() {
        assertThrows(InvalidPasswordException.class, () ->
                authService.resetPasswordWithOtp(3L, "849201", "weak", "weak")
        );
    }
}
