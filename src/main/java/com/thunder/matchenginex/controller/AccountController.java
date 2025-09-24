package com.thunder.matchenginex.controller;

import com.thunder.matchenginex.controller.dto.AddBalanceRequest;
import com.thunder.matchenginex.controller.dto.AddBalanceResponse;
import com.thunder.matchenginex.controller.dto.BalanceResponse;
import com.thunder.matchenginex.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/balance")
    public ResponseEntity<AddBalanceResponse> addBalance(@Valid @RequestBody AddBalanceRequest request) {
        try {
            boolean success = accountService.addBalance(
                    request.getUserId(),
                    request.getCurrency(),
                    new BigDecimal(request.getAmount())
            );

            AddBalanceResponse response = AddBalanceResponse.builder()
                    .success(success)
                    .message("Balance added successfully")
                    .newBalance(accountService.getTotalBalance(request.getUserId(), request.getCurrency()))
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid balance request: {}", e.getMessage());
            AddBalanceResponse response = AddBalanceResponse.builder()
                    .success(false)
                    .message("Invalid request: " + e.getMessage())
                    .newBalance(BigDecimal.ZERO)
                    .build();
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("Error adding balance: {}", e.getMessage(), e);
            AddBalanceResponse response = AddBalanceResponse.builder()
                    .success(false)
                    .message("Error adding balance: " + e.getMessage())
                    .newBalance(BigDecimal.ZERO)
                    .build();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long userId,
            @RequestParam String currency) {
        try {
            BigDecimal availableBalance = accountService.getAvailableBalance(userId, currency);
            BigDecimal totalBalance = accountService.getTotalBalance(userId, currency);

            BalanceResponse response = BalanceResponse.builder()
                    .userId(userId)
                    .currency(currency)
                    .availableBalance(availableBalance)
                    .totalBalance(totalBalance)
                    .frozenBalance(totalBalance.subtract(availableBalance))
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting balance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}