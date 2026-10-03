package de.henrikwolf.ibanvalidator.iban;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class IbanApiConfiguration {

    @Bean
    RestClient ibanApiRestClient(@Value("${ibanapi.base-url}") String baseUrl,
                                 @Value("${ibanapi.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(baseUrl)
                // Header instead of query parameter, so the key never shows up in URLs or error messages.
                .defaultHeader(HttpHeaders.AUTHORIZATION, apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    IbanApiValidator ibanApiValidator(RestClient ibanApiRestClient) {
        return new IbanApiValidator(ibanApiRestClient, IbanApiValidator.Endpoint.BASIC);
    }

    @Bean
    IbanApiValidator ibanApiExtendedValidator(RestClient ibanApiRestClient) {
        return new IbanApiValidator(ibanApiRestClient, IbanApiValidator.Endpoint.EXTENDED);
    }
}
