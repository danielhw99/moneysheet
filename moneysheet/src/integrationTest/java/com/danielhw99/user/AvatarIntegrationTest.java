package com.danielhw99.user;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.imageio.ImageIO;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.service.AccountService;
import com.danielhw99.user.service.AvatarService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers=AvatarIntegrationTest.Database.class)
class AvatarIntegrationTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.13").withDatabaseName("avatar_test")
        .withCreateContainerCmdModifier(command->command.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),com.github.dockerjava.api.model.ExposedPort.tcp(3306))));
    static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            MYSQL.start();
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).cleanDisabled(true).createSchemas(false).load().migrate();
            TestPropertyValues.of("spring.datasource.url="+MYSQL.getJdbcUrl(),"spring.datasource.username="+MYSQL.getUsername(),"spring.datasource.password="+MYSQL.getPassword()).applyTo(context);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired AvatarService avatars;
    @Autowired AccountService accounts;
    @Autowired PlatformTransactionManager transactions;
    static final String PASSWORD="a unique avatar password!";
    User user() {
        String email=UUID.randomUUID()+"@example.com";
        var user=new User(email,email,java.util.UUID.randomUUID().toString(),encoder.encode(PASSWORD),"v1",LocalDateTime.now());
        user.verifyEmail();return users.saveAndFlush(user);
    }
    MockHttpSession login(User user) throws Exception {
        var session=(MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/api/v1/auth/login").session(session).with(csrf()).contentType("application/json")
            .content("{\"email\":\""+user.getEmail()+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk());
        return session;
    }
    MockMultipartFile photo() throws Exception {
        return photo(java.awt.Color.WHITE);
    }
    MockMultipartFile photo(java.awt.Color color) throws Exception {
        var image=new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();graphics.setColor(color);graphics.fillRect(0,0,400,300);graphics.dispose();
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);image.flush();
        return new MockMultipartFile("file","private-original.png","image/png",bytes.toByteArray());
    }
    MockMultipartHttpServletRequestBuilder upload() throws Exception {
        var request=multipart("/api/v1/me/avatar").file(photo());
        request.with(r->{r.setMethod("PUT");return r;});return request;
    }
    @Test void normalizesPersistsReplacesAndDeletesWithoutRevokingSessions() throws Exception {
        var owner=user();var a=login(owner);var b=login(owner);var other=login(user());
        mvc.perform(upload().session(a)).andExpect(status().isForbidden());
        mvc.perform(upload().session(a).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isString());
        String version=users.findById(owner.getId()).orElseThrow().getAvatarVersion();
        byte[] stored=jdbc.queryForObject("SELECT image_data FROM user_profile_images WHERE user_id=?",byte[].class,owner.getId());
        var decoded=ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertThat(decoded.getWidth()).isEqualTo(256);assertThat(decoded.getHeight()).isEqualTo(256);decoded.flush();
        mvc.perform(get("/api/v1/me").session(b)).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value("/api/v1/me/avatar?v="+version));
        mvc.perform(get("/api/v1/me/avatar").session(b)).andExpect(status().isOk()).andExpect(content().bytes(stored)).andExpect(content().contentType("image/jpeg")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/v1/me/avatar").session(other)).andExpect(status().isNotFound());
        mvc.perform(upload().session(a).with(csrf())).andExpect(status().isOk());
        assertThat(users.findById(owner.getId()).orElseThrow().getAvatarVersion()).isNotEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_images WHERE user_id=?",Integer.class,owner.getId())).isEqualTo(1);
        mvc.perform(delete("/api/v1/me/avatar").session(a).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
        mvc.perform(delete("/api/v1/me/avatar").session(a).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/avatar").session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me").session(other)).andExpect(status().isOk());
    }
    @Test void invalidReplacementLeavesExistingPhotoAndVersionUntouched() throws Exception {
        var owner=user();var session=login(owner);avatars.save(owner.getId(),photo());
        String version=users.findById(owner.getId()).orElseThrow().getAvatarVersion();byte[] before=avatars.read(owner.getId());
        mvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3})).with(r->{r.setMethod("PUT");return r;}).session(session).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
        assertThat(avatars.read(owner.getId())).isEqualTo(before);assertThat(users.findById(owner.getId()).orElseThrow().getAvatarVersion()).isEqualTo(version);
    }
    @Test void servletRejectsOversizedMultipartWithStructured413() throws Exception {
        var owner=user();
        var cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
        var client=java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build();
        String base="http://127.0.0.1:"+port;
        var csrfResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        String token=com.jayway.jsonpath.JsonPath.read(csrfResponse.body(),"$.token");
        var login=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"email\":\""+owner.getEmail()+"\",\"password\":\""+PASSWORD+"\"}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        csrfResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        token=com.jayway.jsonpath.JsonPath.read(csrfResponse.body(),"$.token");
        String boundary="avatar-boundary";
        var body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(new byte[5*1024*1024+1]);body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/me/avatar")).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",token)
            .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat((String)com.jayway.jsonpath.JsonPath.read(response.body(),"$.code")).isEqualTo("VALIDATION");
        assertThat((String)com.jayway.jsonpath.JsonPath.read(response.body(),"$.fieldErrors[0].field")).isEqualTo("file");
        assertThat(users.findById(owner.getId()).orElseThrow().getAvatarVersion()).isNull();
    }
    @Test void imageAndProfileChangesRollBackTogetherAndAccountDeletionCascades() throws Exception {
        var owner=user();var other=user();var file=photo();avatars.save(owner.getId(),file);avatars.save(other.getId(),file);
        String version=users.findById(owner.getId()).orElseThrow().getAvatarVersion();byte[] before=avatars.read(owner.getId());
        var replacement=photo(java.awt.Color.RED);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            avatars.save(owner.getId(),replacement);
            assertThat(avatars.read(owner.getId())).isNotEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(users.findById(owner.getId()).orElseThrow().getAvatarVersion()).isEqualTo(version);
        assertThat(avatars.read(owner.getId())).isEqualTo(before);
        new TransactionTemplate(transactions).executeWithoutResult(status->{avatars.delete(owner.getId());status.setRollbackOnly();});
        assertThat(avatars.read(owner.getId())).isEqualTo(before);
        accounts.deleteAccount(owner.getId(),PASSWORD);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_images WHERE user_id=?",Integer.class,owner.getId())).isZero();
        assertThat(avatars.read(other.getId())).isNotEmpty();
    }
    @Test void concurrentReplacementsKeepTheVersionAndImageFromTheSameSave() throws Exception {
        var owner=user();var red=photo(java.awt.Color.RED);var blue=photo(java.awt.Color.BLUE);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{start.await();return avatars.save(owner.getId(),red);});
            var b=executor.submit(()->{start.await();return avatars.save(owner.getId(),blue);});
            start.countDown();
            var savedA=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var savedB=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
            String current="/api/v1/me/avatar?v="+users.findById(owner.getId()).orElseThrow().getAvatarVersion();
            assertThat(current).isIn(savedA.avatarUrl(),savedB.avatarUrl());
            var last=current.equals(savedA.avatarUrl())?red:blue;
            assertThat(avatars.read(owner.getId())).isEqualTo(new com.danielhw99.user.service.AvatarNormalizer().normalize(last));
        }
    }
}
