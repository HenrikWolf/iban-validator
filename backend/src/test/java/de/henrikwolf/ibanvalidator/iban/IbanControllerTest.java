package de.henrikwolf.ibanvalidator.iban;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IbanController.class)
@Import(IbanValidator.class)
class IbanControllerTest {

    private static final String URL = "/api/v1/iban/validation";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsValidResultForValidIban() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iban": "DE89 3704 0044 0532 0130 00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value("DE89370400440532013000"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    void returnsInvalidResultWithReasonForInvalidIban() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iban": "DE88370400440532013000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.countryCode").doesNotExist())
                .andExpect(jsonPath("$.failureReason").value("INVALID_CHECKSUM"));
    }

    @Test
    void rejectsMissingIban() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyIban() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iban": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
