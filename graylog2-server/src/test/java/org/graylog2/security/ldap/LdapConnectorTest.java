/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.security.ldap;

import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.annotations.LoadSchema;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.partition.impl.avl.AvlPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.graylog2.shared.security.ldap.LdapEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {
        @CreateTransport(protocol = "LDAP")
})
@CreateDS(
        name = "LdapConnectorTest",
        partitions = {
                @CreatePartition(
                        name = "example.com",
                        type = AvlPartition.class,
                        suffix = "dc=example,dc=com",
                        contextEntry = @ContextEntry(
                                entryLdif = "dn: dc=example,dc=com\n" +
                                            "dc: example\n" +
                                            "objectClass: top\n" +
                                            "objectClass: domain\n\n"

                        ),
                        indexes = {
                                @CreateIndex(attribute = "objectClass"),
                                @CreateIndex(attribute = "dc"),
                                @CreateIndex(attribute = "ou")
                        }

                )
        },
        loadedSchemas = {
                @LoadSchema(name = "nis", enabled = true)
        }
)
@ApplyLdifs(
        {
                "dn: ou=users,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: users",

                "dn: ou=groups,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "objectClass: top",
                "ou: groups",

                "dn: cn=John Doe,ou=users,dc=example,dc=com",
                "gidNumber: 1001",
                "objectClass: posixAccount",
                "objectClass: top",
                "objectClass: person",
                "uidNumber: 1001",
                "uid: john",
                "homeDirectory: /home/john",
                "sn: Doe",
                "cn: John Doe",
                "userPassword:: dGVzdA==",

                "dn: cn=Developers,ou=groups,dc=example,dc=com",
                "gidNumber: 2000",
                "objectClass: posixGroup",
                "objectClass: top",
                "cn: Developers",
                "memberUid: john",

                "dn: cn=Engineers,ou=groups,dc=example,dc=com",
                "objectClass: groupOfUniqueNames",
                "objectClass: top",
                "cn: Engineers",
                "uniqueMember: cn=John Doe,ou=users,dc=example,dc=com",

                "dn: cn=QA,ou=groups,dc=example,dc=com",
                "objectClass: groupOfNames",
                "objectClass: top",
                "cn: QA",
                "member: cn=John Doe,ou=users,dc=example,dc=com"
        }
)
public class LdapConnectorTest extends AbstractLdapTestUnit {
    private static final String ADMIN_DN = "uid=admin,ou=system";
    private static final String ADMIN_PASSWORD = "secret";

    private LdapConnector connector;
    private LdapNetworkConnection connection;

    @Before
    public void setUp() throws Exception {
        final LdapServer server = getLdapServer();
        final LdapConnectionConfig config = new LdapConnectionConfig();

        config.setLdapHost("localHost");
        config.setLdapPort(server.getPort());
        config.setName(ADMIN_DN);
        config.setCredentials(ADMIN_PASSWORD);

        connector = new LdapConnector(10000);
        connection = connector.connect(config);
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void testUserLookup() throws Exception {
        final LdapEntry entry = connector.search(connection,
                "ou=users,dc=example,dc=com",
                "(&(objectClass=posixAccount)(uid={0}))",
                "cn",
                "john",
                false,
                "ou=groups,dc=example,dc=com",
                "cn",
                "(|(objectClass=groupOfNames)(objectClass=posixGroup))");

        assertThat(entry).isNotNull();
        assertThat(entry.getDn())
                .isNotNull()
                .isEqualTo("cn=John Doe,ou=users,dc=example,dc=com");

        assertThat(entry.getGroups())
                .hasSize(2)
                .contains("QA", "Developers");
    }

    @Test
    public void testGroupOfNamesLookup() throws Exception {
        final LdapEntry entry = connector.search(connection,
                "ou=users,dc=example,dc=com",
                "(&(objectClass=posixAccount)(uid={0}))",
                "cn",
                "john",
                false,
                "ou=groups,dc=example,dc=com",
                "cn",
                "(objectClass=groupOfNames)");

        assertThat(entry).isNotNull();
        assertThat(entry.getDn())
                .isNotNull()
                .isEqualTo("cn=John Doe,ou=users,dc=example,dc=com");

        assertThat(entry.getGroups()).hasSize(1).contains("QA");
    }

    @Test
    public void testGroupOfUniqueNamesLookup() throws Exception {
        final LdapEntry entry = connector.search(connection,
                "ou=users,dc=example,dc=com",
                "(&(objectClass=posixAccount)(uid={0}))",
                "cn",
                "john",
                false,
                "ou=groups,dc=example,dc=com",
                "cn",
                "(objectClass=groupOfUniqueNames)");

        assertThat(entry).isNotNull();
        assertThat(entry.getDn())
                .isNotNull()
                .isEqualTo("cn=John Doe,ou=users,dc=example,dc=com");

        assertThat(entry.getGroups()).hasSize(1).contains("Engineers");
    }

    @Test
    public void testPosixGroupLookup() throws Exception {
        final LdapEntry entry = connector.search(connection,
                "ou=users,dc=example,dc=com",
                "(&(objectClass=posixAccount)(uid={0}))",
                "cn",
                "john",
                false,
                "ou=groups,dc=example,dc=com",
                "cn",
                "(objectClass=posixGroup)");

        assertThat(entry).isNotNull();
        assertThat(entry.getDn())
                .isNotNull()
                .isEqualTo("cn=John Doe,ou=users,dc=example,dc=com");

        assertThat(entry.getGroups()).hasSize(1).contains("Developers");
    }

    @Test
    public void testAllGroupClassesLookup() throws Exception {
        final LdapEntry entry = connector.search(connection,
                "ou=users,dc=example,dc=com",
                "(&(objectClass=posixAccount)(uid={0}))",
                "cn",
                "john",
                false,
                "ou=groups,dc=example,dc=com",
                "cn",
                "(|(objectClass=posixGroup)(objectClass=groupOfNames)(objectclass=groupOfUniqueNames))");

        assertThat(entry).isNotNull();
        assertThat(entry.getDn())
                .isNotNull()
                .isEqualTo("cn=John Doe,ou=users,dc=example,dc=com");

        assertThat(entry.getGroups())
                .hasSize(3)
                .contains("Developers", "QA", "Engineers");
    }

    @Test
    public void testListGroups() throws Exception {
        final Set<String> groups = connector.listGroups(connection, "ou=groups,dc=example,dc=com", "(objectClass=top)", "cn");

        assertThat(groups)
                .hasSize(3)
                .contains("Developers", "QA", "Engineers");
    }
}