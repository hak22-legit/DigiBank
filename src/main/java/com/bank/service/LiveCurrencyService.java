package com.bank.service;

import com.bank.util.CurrencyConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * REST-based Live Currency Service with in-memory TTL caching and offline fallback.
 * Fetches real-time spot rates from open.er-api.com, caches them for 1 hour,
 * and seamlessly degrades to local static rates if the network or API is unavailable.
 */
public class LiveCurrencyService {
    private static final Logger logger = LoggerFactory.getLogger(LiveCurrencyService.class);

    private static final String DEFAULT_API_URL = "https://open.er-api.com/v6/latest/USD";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final String apiUrl;
    private final Duration cacheTtl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private Map<String, BigDecimal> cachedRates;
    private Instant lastFetchedAt;

    public LiveCurrencyService() {
        this(DEFAULT_API_URL, DEFAULT_TTL, HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build(), new ObjectMapper());
    }

    public LiveCurrencyService(String apiUrl, Duration cacheTtl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.cacheTtl = cacheTtl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves current exchange rates, using the cached values if valid or fetching fresh rates.
     */
    public synchronized Map<String, BigDecimal> getRates() {
        if (isCacheValid()) {
            return cachedRates;
        }
        return refreshRates();
    }

    /**
     * Forces an immediate refresh of the exchange rates from the REST API, bypassing the cache.
     */
    public synchronized Map<String, BigDecimal> refreshRates() {
        try {
            logger.info("Fetching real-time exchange rates from {}", apiUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, BigDecimal> parsed = parseRatesJson(response.body());
                if (!parsed.isEmpty()) {
                    this.cachedRates = Collections.unmodifiableMap(parsed);
                    this.lastFetchedAt = Instant.now();
                    logger.info("Successfully refreshed {} currency rates. Base: USD", parsed.size());
                    return cachedRates;
                }
            } else {
                logger.warn("Currency API returned HTTP status {}: {}", response.statusCode(), response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Exchange rate fetch interrupted, falling back to local rates: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Failed to fetch live exchange rates from {}: {}. Using fallback/cached rates.", apiUrl, e.getMessage());
        }

        // Offline or failure fallback
        if (this.cachedRates == null || this.cachedRates.isEmpty()) {
            this.cachedRates = Collections.unmodifiableMap(new HashMap<>(CurrencyConverter.getFallbackRates()));
            this.lastFetchedAt = Instant.now();
        }
        return cachedRates;
    }

    /**
     * Parses the JSON payload from open.er-api.com.
     */
    private Map<String, BigDecimal> parseRatesJson(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        String result = root.path("result").asText();
        if (!"success".equalsIgnoreCase(result)) {
            logger.warn("Exchange rate API result was '{}', expected 'success'", result);
            return Collections.emptyMap();
        }

        JsonNode ratesNode = root.path("rates");
        if (!ratesNode.isObject()) {
            logger.warn("Exchange rate API JSON response missing 'rates' object");
            return Collections.emptyMap();
        }

        Map<String, BigDecimal> rates = new HashMap<>(CurrencyConverter.getFallbackRates());
        Iterator<Map.Entry<String, JsonNode>> fields = ratesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            try {
                String code = field.getKey().toUpperCase();
                BigDecimal val = new BigDecimal(field.getValue().asText());
                if (val.compareTo(BigDecimal.ZERO) > 0) {
                    rates.put(code, val);
                }
            } catch (Exception ignored) {
                // Skip malformed individual entries
            }
        }
        return rates;
    }

    /**
     * Converts an amount using the live or cached exchange rates.
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        return CurrencyConverter.convert(amount, fromCurrency, toCurrency, getRates());
    }

    /**
     * Retrieves the direct unit exchange rate between two currencies (1 unit of from = X units of to).
     */
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        return CurrencyConverter.getExchangeRate(fromCurrency, toCurrency, getRates());
    }

    public synchronized boolean isCacheValid() {
        return cachedRates != null && !cachedRates.isEmpty() && lastFetchedAt != null
                && Duration.between(lastFetchedAt, Instant.now()).compareTo(cacheTtl) < 0;
    }

    public synchronized Instant getLastFetchedAt() {
        return lastFetchedAt;
    }
}
