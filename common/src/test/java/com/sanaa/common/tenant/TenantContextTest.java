package com.sanaa.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
    private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void setGetAndClear() {
        assertThat(TenantContext.get()).isNull();

        TenantContext.set(TENANT_A);
        assertThat(TenantContext.get()).isEqualTo(TENANT_A);

        TenantContext.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void callAsRestoresPreviousValue() {
        TenantContext.set(TENANT_A);

        String result = TenantContext.callAs(TENANT_B, () -> {
            assertThat(TenantContext.get()).isEqualTo(TENANT_B);
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(TenantContext.get()).isEqualTo(TENANT_A);
    }

    @Test
    void callAsClearsWhenNoPreviousValue() {
        TenantContext.callAs(TENANT_A, () -> null);

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void callAsClearsOnException() {
        assertThatThrownBy(() -> TenantContext.callAs(TENANT_A, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void runAsRestoresPreviousValue() {
        TenantContext.set(TENANT_A);

        TenantContext.runAs(TENANT_B, () -> assertThat(TenantContext.get()).isEqualTo(TENANT_B));

        assertThat(TenantContext.get()).isEqualTo(TENANT_A);
    }

    @Test
    void getOrNilReturnsSentinelWhenUnset() {
        assertThat(TenantContext.getOrNil()).isEqualTo(TenantContext.NIL_TENANT);
    }
}
