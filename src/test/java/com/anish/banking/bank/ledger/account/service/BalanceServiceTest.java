package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.exception.AccountNotFoundException;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the read-only, single-account paths of BalanceService.
 *
 * Deposit and withdraw are intentionally NOT unit-tested here. Since Phase 4 they
 * coordinate three repositories and two account mutations (customer + settlement)
 * across a single transaction — integration behavior whose correctness is proven
 * end-to-end, against a real database and the seeded settlement account, by
 * DoubleEntryInvariantTest. Mocking that coordination would mostly restate the
 * implementation rather than verify it. The AFTER_COMMIT cache-eviction side effect
 * they trigger is, for the same reason, proven in BalanceCacheEvictionTest instead.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private LedgerEntryRepository ledger;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    // @Spy (not a plain field): @InjectMocks only wires @Mock/@Spy fields into the
    // constructor. Spying a real mapper (rather than @Mock-ing it) is deliberate too — cache
    // hit/miss tests need actual JSON round-tripping, not a stubbed-out one.
    @Spy
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @InjectMocks
    private BalanceService balanceService;

    @BeforeEach
    void stubRedisValueOps() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void returnsBalanceForExistingAccountOnCacheMiss() {
        Account account = new Account("Anish", "CAD");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.credit(new BigDecimal("900.00"));

        when(valueOps.get("balance:1")).thenReturn(null);       // cache miss
        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.getBalance(1L);

        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.balance()).isEqualByComparingTo("900.00");

        verify(accounts).findById(1L);
        verifyNoInteractions(ledger);
        // miss is populated back into the cache
        verify(valueOps).set(eq("balance:1"), anyString(), any(java.time.Duration.class));
    }

    @Test
    void returnsCachedBalanceWithoutHittingTheDatabase() {
        String cachedJson = """
                {"accountId":1,"currency":"CAD","balance":900.00}""";
        when(valueOps.get("balance:1")).thenReturn(cachedJson);

        BalanceResponse response = balanceService.getBalance(1L);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("900.00");

        verifyNoInteractions(accounts);   // cache hit -> DB never touched
        verifyNoInteractions(ledger);
        verify(valueOps, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void fallsBackToDbWhenCacheReadFails() {
        when(valueOps.get("balance:1")).thenThrow(new RuntimeException("redis down"));
        Account account = new Account("Anish", "CAD");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.credit(new BigDecimal("900.00"));
        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.getBalance(1L);

        assertThat(response.balance()).isEqualByComparingTo("900.00");
        verify(accounts).findById(1L);
    }

    @Test
    void throwsWhenAccountDoesNotExist() {
        when(valueOps.get("balance:99")).thenReturn(null);
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance(99L))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accounts).findById(99L);
        verifyNoInteractions(ledger);
    }
}
