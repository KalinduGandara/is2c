package com.is2c.biostarsync;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("sync.data-dir", () -> dataDir.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("The page renders before anything is configured")
    void page() throws Exception {
        mvc.perform(get("/").header("Host", "localhost:8095")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To sync, set up")));
    }

    @Test
    @DisplayName("Requests for an unknown host name are refused (DNS rebinding)")
    void unknownHost() throws Exception {
        mvc.perform(get("/").header("Host", "evil.example:8095")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Form posts from another site are refused; posts from the page itself work")
    void crossSitePost() throws Exception {
        mvc.perform(post("/reload").header("Host", "localhost:8095").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reload").header("Host", "localhost:8095").header("Origin", "http://localhost:8095"))
                .andExpect(status().is3xxRedirection());
    }
}
