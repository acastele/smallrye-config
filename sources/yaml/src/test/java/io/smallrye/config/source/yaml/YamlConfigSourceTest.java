package io.smallrye.config.source.yaml;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class YamlConfigSourceTest {
    @Test
    void flatten() throws Exception {
        YamlConfigSource yaml = new YamlConfigSource("yaml",
                YamlConfigSourceTest.class.getResourceAsStream("/example-216.yml"));
        String value = yaml.getValue("admin.users");
        Users users = new UserConverter().convert(value);
        assertEquals(2, users.getUsers().size());
        assertEquals(users.users.get(0).getEmail(), "joe@gmail.com");
        assertEquals(users.users.get(0).getRoles(), Stream.of("Moderator", "Admin").collect(toList()));

        assertEquals("joe@gmail.com", yaml.getValue("admin.users.[0].email"));
    }

    @Test
    void profiles() throws Exception {
        YamlConfigSource yaml = new YamlConfigSource("yaml",
                YamlConfigSourceTest.class.getResourceAsStream("/example-profiles.yml"));

        assertEquals("default", yaml.getValue("foo.bar"));
        assertEquals("dev", yaml.getValue("%dev.foo.bar"));
        assertEquals("prod", yaml.getValue("%prod.foo.bar"));
    }

    @Test
    void list() {
        String yaml = "quarkus:\n" +
                "  http:\n" +
                "    ssl:\n" +
                "      protocols:\n" +
                "        - TLSv1.2\n" +
                "        - TLSv1.3";

        SmallRyeConfig config = new SmallRyeConfigBuilder().withSources(new YamlConfigSource("Yaml", yaml)).build();
        String[] values = config.getValue("quarkus.http.ssl.protocols", String[].class);
        assertEquals(2, values.length);
        assertEquals("TLSv1.2", values[0]);
        assertEquals("TLSv1.3", values[1]);

        List<String> list = config.getValues("quarkus.http.ssl.protocols", String.class, ArrayList::new);
        assertEquals(2, list.size());
        assertEquals("TLSv1.2", list.get(0));
        assertEquals("TLSv1.3", list.get(1));
    }

    @Test
    void config() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource("yaml", YamlConfigSourceTest.class.getResourceAsStream("/example-216.yml")))
                .withConverter(Users.class, 100, new UserConverter())
                .build();

        final Users users = config.getValue("admin.users", Users.class);
        assertEquals(2, users.getUsers().size());
        assertEquals(users.users.get(0).getEmail(), "joe@gmail.com");
        assertEquals(users.users.get(0).getRoles(), Stream.of("Moderator", "Admin").collect(toList()));

        assertEquals("joe@gmail.com", config.getRawValue("admin.users.[0].email"));
    }

    @Test
    void propertyNames() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource("yaml", YamlConfigSourceTest.class.getResourceAsStream("/example.yml")))
                .withConverter(Users.class, 100, new UserConverter())
                .build();

        final List<String> propertyNames = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .collect(toList());

        assertTrue(propertyNames.contains("quarkus.http.port"));
        assertTrue(propertyNames.contains("quarkus.http.ssl-port"));
        assertTrue(propertyNames.contains("quarkus.http.ssl.protocols"));
        assertFalse(propertyNames.contains("quarkus.http.ssl.protocols.[0]"));
        assertEquals("TLSv1.2", config.getRawValue("quarkus.http.ssl.protocols.[0]"));
        assertFalse(propertyNames.contains("quarkus.http.ssl.protocols.[1]"));
        assertEquals("TLSv1.3", config.getRawValue("quarkus.http.ssl.protocols.[1]"));
    }

    @Test
    void quotedProperties() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(
                        new YamlConfigSource("yaml", YamlConfigSourceTest.class.getResourceAsStream("/example-quotes.yml")))
                .withConverter(Users.class, 100, new UserConverter())
                .build();

        final List<String> propertyNames = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .collect(toList());

        assertTrue(propertyNames.contains("quarkus.log.category.liquibase.level"));
        assertTrue(propertyNames.contains("quarkus.log.category.\"liquibase.changelog.ChangeSet\".level"));
        assertNotNull(config.getRawValue("quarkus.log.category.\"liquibase.changelog.ChangeSet\".level"));
    }

    @Test
    void commas() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(
                        new YamlConfigSource("yaml", YamlConfigSourceTest.class.getResourceAsStream("/example.yml")))
                .withConverter(Users.class, 100, new UserConverter())
                .build();

        String[] values = config.getValue("quarkus.jib.jvm-arguments", String[].class);
        assertEquals(3, values.length);
        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", values[0]);
    }

    @Test
    void intKeys() throws Exception {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(
                        new YamlConfigSource("yaml", YamlConfigSourceTest.class.getResourceAsStream("/example-int-keys.yml")))
                .withConverter(Users.class, 100, new UserConverter())
                .build();

    }

    @BeforeAll
    static void beforeAll() {
        System.setProperty("block_expression", "[{type: test, host: localhost, port: 443, scheme: https}]");
    }

    @Test
    void expressions() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultSources()
                .addDefaultInterceptors()
                .withSources(new YamlConfigSource("yaml", "block:\n" +
                        "  expression:\n" +
                        "     servers: ${block_expression}\n" +
                        "  inline:\n" +
                        "     servers: [{type: test, host: localhost, port: 443, scheme: https}]"))
                .withConverter(Servers.class, 100, value -> new Yaml().loadAs(value, Servers.class))
                .build();

        assertEquals("[{type: test, host: localhost, port: 443, scheme: https}]", config.getRawValue("block.expression.servers"));
        assertEquals("{\"servers\": [{\"type\": \"test\", \"host\": \"localhost\", \"port\": !!int \"443\", \"scheme\": \"https\"}]}\n", config.getRawValue("block.inline.servers"));

        assertThrows(Exception.class, () -> config.getValue("block.expression.servers", Servers.class));
        assertEquals(443, config.getValue("block.inline.servers", Servers.class).getServers().get(0).port);
    }

    public static class Servers {
        List<Server> servers;

        public List<Server> getServers() {
            return servers;
        }

        public void setServers(List<Server> servers) {
            this.servers = servers;
        }
    }

    public static class Server {
        String type;
        String host;
        int port;
        String scheme;

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(final String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(final int port) {
            this.port = port;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(final String scheme) {
            this.scheme = scheme;
        }
    }

    public static class Users {
        List<User> users;

        public List<User> getUsers() {
            return users;
        }

        public void setUsers(final List<User> users) {
            this.users = users;
        }
    }

    public static class User {
        String email;
        String username;
        String password;
        List<String> roles;

        public String getEmail() {
            return email;
        }

        public void setEmail(final String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(final String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(final List<String> roles) {
            this.roles = roles;
        }
    }

    static class UserConverter implements Converter<Users> {
        @Override
        public Users convert(final String value) {
            return new Yaml().loadAs(value, Users.class);
        }
    }
}
