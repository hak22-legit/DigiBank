package com.bank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveCurrencyServiceTest {

    @Test
    @DisplayName("Verify LiveCurrencyService falls back to default rates when offline or API fails")
    void testOfflineFallback() {
        LiveCurrencyService service = new LiveCurrencyService("http://invalid-unreachable-domain-123456.xyz",
                Duration.ofHours(1),
                HttpClient.newHttpClient(),
                new ObjectMapper());

        Map<String, BigDecimal> rates = service.getRates();

        assertNotNull(rates);
        assertTrue(rates.containsKey("USD"));
        assertTrue(rates.containsKey("KHR"));
        assertTrue(rates.containsKey("EUR"));
        assertTrue(rates.containsKey("THB"));
        assertEquals(new BigDecimal("1.0000"), rates.get("USD"));
        assertEquals(new BigDecimal("4100.0000"), rates.get("KHR"));
    }

    @Test
    @DisplayName("Verify in-memory TTL caching prevents redundant fetches")
    @SuppressWarnings("unchecked")
    void testTtlCaching() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        String jsonPayload = """
                {
                  "result": "success",
                  "base_code": "USD",
                  "rates": {
                    "USD": 1.0,
                    "KHR": 4120.0,
                    "EUR": 0.93,
                    "THB": 35.8
                  }
                }
                """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonPayload);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        LiveCurrencyService service = new LiveCurrencyService(
                "https://open.er-api.com/v6/latest/USD",
                Duration.ofHours(1),
                mockHttpClient,
                new ObjectMapper());

        // First call should invoke HTTP client
        Map<String, BigDecimal> rates1 = service.getRates();
        assertEquals(new BigDecimal("4120.0"), rates1.get("KHR"));
        assertTrue(service.isCacheValid());

        // Second call within 1-hour TTL must use cached map without invoking HTTP client again
        Map<String, BigDecimal> rates2 = service.getRates();
        assertSame(rates1, rates2);

        // Verify HTTP send was only called once
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Verify manual refreshRates bypasses cache and re-fetches")
    @SuppressWarnings("unchecked")
    void testManualRefresh() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        String jsonPayload = """
                {
                  "result": "success",
                  "base_code": "USD",
                  "rates": {
                    "USD": 1.0,
                    "KHR": 4090.0
                  }
                }
                """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonPayload);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        LiveCurrencyService service = new LiveCurrencyService(
                "https://open.er-api.com/v6/latest/USD",
                Duration.ofHours(1),
                mockHttpClient,
                new ObjectMapper());

        service.getRates();
        assertEquals(new BigDecimal("4090.0"), service.getRates().get("KHR"));

        // Force manual refresh
        service.refreshRates();

        verify(mockHttpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Verify convert and getRate delegates to CurrencyConverter")
    void testConvertAndGetRateDelegates() {
        LiveCurrencyService service = new LiveCurrencyService("http://invalid-unreachable-domain-123456.xyz",
                Duration.ofHours(1),
                HttpClient.newHttpClient(),
                new ObjectMapper());

        BigDecimal converted = service.convert(new BigDecimal("10.00"), "USD", "KHR");
        assertEquals(new BigDecimal("41000.00"), converted);

        BigDecimal rate = service.getRate("USD", "KHR");
        assertEquals(new BigDecimal("4100.0000"), rate);
    }
}
