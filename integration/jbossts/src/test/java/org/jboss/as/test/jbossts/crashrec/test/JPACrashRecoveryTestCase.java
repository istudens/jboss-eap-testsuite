/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.jbossts.crashrec.test;

import org.jboss.arquillian.container.test.api.*;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.base.AbstractMgmtServerSetupTask;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.jbossts.shared.AbstractLoginModuleSecurityDomainTestCaseSetup;
import org.jboss.as.test.jbossts.crashrec.common.RecoveredXid;
import org.jboss.as.test.jbossts.crashrec.jpa.*;
import org.jboss.as.test.jbossts.extension.ManualServerSetup;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.junit.Assert.assertEquals;


/**
 * Crash recovery tests involving a XA datasource.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ManualServerSetup({
        TxEnvironmentSetup.class,
        CrashRecoveryTestBase.TxEnvironmentCheck.class,
        JPACrashRecoveryTestCase.SecurityDomainSetup.class,
        JPACrashRecoveryTestCase.SecurityDomain0Setup.class,
        JPACrashRecoveryTestCase.XADatasourceSetup.class})
public class JPACrashRecoveryTestCase extends CrashRecoveryTestBase {
    private static final Logger log = Logger.getLogger(JPACrashRecoveryTestCase.class);

    private static final String ARCHIVE_NAME = "crashrecovery-jpa";

    private static final String RECOVERY_DATASOURCE_NAME = System.getProperty("jbossts.recovery.datasource", "CrashRecoveryDS");
    private static final String RECOVERY_DATASOURCE_JNDI = "java:jboss/xa-datasources/" + RECOVERY_DATASOURCE_NAME;

    private static final String CREDENTIALS = System.getProperty("jbossts.credentials", "cr1");

    private static final String DS_JDBC_XADATASOURCE = System.getProperty("ds.jdbc.driver-xa");
    private static final String DS_JDBC_URL = System.getProperty("ds.jdbc.url");
    private static final String DS_JDBC_DRIVER_JAR = System.getProperty("ds.jdbc.driver.jar");
    private static final String DS_DB = System.getProperty("ds.db");

    private static final Databases database;
    static {
        try {
            String dbName = (DS_DB.startsWith("db2")) ? "db2" : DS_DB.replaceFirst("\\d+.*$", "");   // strip off any numbers, i.e. version of DB
            database = Databases.valueOf(dbName.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported database " + DS_DB + "! Please provide a proper one with -Dds= parameter according to testsuite/pom.xml.");
        }
    }

    public enum Databases {
        POSTGRESQL, MYSQL, ORACLE, DB2, SYBASE, MSSQL;
    }

    private static final int TEST_ENTITY_INIT_VALUE = 1;

    private static final String persistenceXml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<persistence xmlns=\"http://java.sun.com/xml/ns/persistence\">" +
                    "   <persistence-unit name=\"jbossts-crashrec\">" +
                    "       <jta-data-source>" + RECOVERY_DATASOURCE_JNDI + "</jta-data-source>" +
                    "       <properties>" +
                    "           <property name=\"hibernate.hbm2ddl.auto\" value=\"" + (database == Databases.DB2 ? "none" : "update") + "\"/>" +
                    "       </properties>" +
                    "   </persistence-unit>" +
                    "</persistence>";

    private static final String usersProperties =
            "crashrec=crashrec\n" +
            "crash0=crash0";
    private static final String rolesProperties =
            "crashrec=Users\n" +
            "crash0=Users";

    @Deployment(name = ARCHIVE_NAME, managed = false, testable = false)
    @TargetsContainer(CRASHREC_CONTAINER)
    public static Archive<?> deploy() {
        return ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar")
                .addPackage("org.jboss.as.test.jbossts.common")
                .addPackage("org.jboss.as.test.jbossts.crashrec.common")
                .addPackage("org.jboss.as.test.jbossts.crashrec.jpa")
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.ironjacamar.jdbcadapters,org.jboss.jts\n"), "MANIFEST.MF")
                .addAsManifestResource(new StringAsset(persistenceXml), "persistence.xml")
                .addAsResource(new StringAsset(usersProperties), "users.properties")
                .addAsResource(new StringAsset(rolesProperties), "roles.properties");
    }


    @Override
    protected String getDeploymentName() {
        return ARCHIVE_NAME;
    }

    @Override
    protected void initBeforeTestExecution(String testName) throws Throwable {
        lookupTestEntityHelper().initTestEntity(testName, TEST_ENTITY_INIT_VALUE);
    }

    @Override
    protected void checkAfterTestExecution(String testName, boolean expectRollback) throws Throwable {
        // checking the state of DB after recovering
        TestEntity recoveredEntity = lookupTestEntityHelper().getTestEntity(testName);
        assertEquals("Incorrect data in database after crash recovery.", recoveredEntity.getA(), TEST_ENTITY_INIT_VALUE + (expectRollback ? 0 : 1));
    }

    @Override
    protected void callCrashTest(String testName) throws Throwable {
        lookupCrashBean().testXA(testName);
    }

    @Override
    protected Set<RecoveredXid> checkXidsInDoubt() throws Throwable {
        return lookupCrashHelper().checkXidsInDoubt(RECOVERY_DATASOURCE_JNDI);
    }


    /**
     * Wipes out in-doubt txs from database according to xidsToRecover list.
     */
    @Override
    protected void wipeOutTxsInDoubt(Set<RecoveredXid> xidsToRecover) {
        log.info("wiping out in-doubt txs");
        try {
            lookupCrashHelper().wipeOutTxsInDoubt(RECOVERY_DATASOURCE_JNDI, xidsToRecover);
        } catch (Exception e) {
            log.warn(e);
        }
    }


    private JPACrashBeanRemote lookupCrashBean() throws Exception {
        return lookup(JPACrashBeanRemote.class, JPACrashBean.class, ARCHIVE_NAME);
    }

    private JPACrashHelperRemote lookupCrashHelper() throws Exception {
        return lookup(JPACrashHelperRemote.class, JPACrashHelper.class, ARCHIVE_NAME);
    }

    private TestEntityHelperRemote lookupTestEntityHelper() throws Exception {
        return lookup(TestEntityHelperRemote.class, TestEntityHelper.class, ARCHIVE_NAME);
    }


    static class XADatasourceSetup extends AbstractMgmtServerSetupTask {

        @Override
        protected void doSetup(final ManagementClient managementClient) throws Exception {
            try {
                removeXADataSource();
            } catch (Exception ignored) {
            }
            createXADataSource();
            log.info("===xa-resource created===");
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            removeXADataSource();
            log.info("===xa-resource removed===");
        }

        private void createXADataSource() throws Exception {

            final ModelNode address = new ModelNode();
            address.add("subsystem", "datasources");
            address.add("xa-data-source", getDatasourceName());
            address.protect();

            final ModelNode operation = new ModelNode();
            operation.get(OP).set("add");
            operation.get(OP_ADDR).set(address);

            //-------- DataSource properties
            Properties properties = new Properties();
            properties.put("jndi-name", getDatasourceJndiName());
            if (DS_JDBC_DRIVER_JAR == null)
                throw new RuntimeException("System property ds.jdbc.driver.jar cannot be null!");
            properties.put("driver-name", DS_JDBC_DRIVER_JAR);
            if (CREDENTIALS.equals("cr2")) {
                properties.put("user-name", "crash0");
                properties.put("password", "crash0");
                properties.put("recovery-username", "crashrec");
                properties.put("recovery-password", "crashrec");
            } else if (CREDENTIALS.equals("cr3")) {
                properties.put("security-domain", "CrashRecoveryDomain0");
                properties.put("recovery-username", "crashrec");
                properties.put("recovery-password", "crashrec");
            } else if (CREDENTIALS.equals("cr4")) {
                properties.put("security-domain", "CrashRecoveryDomain0");
                properties.put("recovery-security-domain", "CrashRecoveryDomain");
            } else {    // cr1 is default
                properties.put("user-name", "crashrec");
                properties.put("password", "crashrec");
            }

            // other database specific settings
            switch (database) {
                case MYSQL:
                    break;
                case POSTGRESQL:
                    break;
                case ORACLE:
                    properties.put("same-rm-override", "false");
                    properties.put("no-tx-separate-pool", "true");
                    break;
                case DB2:
                    properties.put("same-rm-override", "false");
                    properties.put("no-tx-separate-pool", "true");
                    break;
                case SYBASE:
                    break;
                case MSSQL:
                    properties.put("same-rm-override", "false");
                    break;
            }
//        properties .put("valid-connection-checker-class-name","someClass2");
//        properties .put("wrap-xa-resource","true");

            if (DS_JDBC_XADATASOURCE == null)
                throw new RuntimeException("System property ds.jdbc.xadatasource cannot be null!");
            properties.put("xa-datasource-class", DS_JDBC_XADATASOURCE);

            Enumeration e = properties.propertyNames();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                operation.get(name).set(properties.getProperty(name));
            }
            log.info("operation: " + operation);
            executeOperation(operation);

            //-------- XADataSource properties
            String databaseName = "crashrec";
            // other XA database specific settings
            switch (database) {
                case MYSQL:
                    break;
                case POSTGRESQL:
                    break;
                case ORACLE:
                    break;
                case DB2:
                    databaseName = "jbossqa";
                    addXADataSourceProperty(address, "DriverType", "4");
                    break;
                case SYBASE:
                    addXADataSourceProperty(address, "NetworkProtocol", "Tds");
                    break;
                case MSSQL:
                    addXADataSourceProperty(address, "SelectMethod", "cursor");
                    break;
            }
            if (database == Databases.ORACLE) {
                addXADataSourceProperty(address, "URL", getJdbcUrl());
            } else {
                String pattern = (database == Databases.SYBASE) ? "jdbc:sybase:Tds:(.*?):(\\d+)" : "://(.*?):(\\d+)";
                Matcher matcher = Pattern.compile(pattern).matcher(getJdbcUrl());
                matcher.find();
                String host = matcher.group(1);
                String port = matcher.group(2);

                addXADataSourceProperty(address, "ServerName", host);
                addXADataSourceProperty(address, "PortNumber", port);
            }
            addXADataSourceProperty(address, "DatabaseName", databaseName);


            //-------- enable the created datasource
            final ModelNode enable = new ModelNode();
            enable.get(OP).set("enable");
            enable.get(OP_ADDR).set(address);
            log.info("operation: " + enable);
            executeOperation(enable);

            addAdditionalSettings(address);
        }

        protected String getDatasourceName() throws Exception {
            return RECOVERY_DATASOURCE_NAME;
        }

        protected String getDatasourceJndiName() throws Exception {
            return RECOVERY_DATASOURCE_JNDI;
        }

        protected String getJdbcUrl() throws Exception {
            if (DS_JDBC_URL == null)
                throw new RuntimeException("System property ds.jdbc.url cannot be null!");
            return DS_JDBC_URL;
        }

        protected void addAdditionalSettings(final ModelNode address) throws Exception {
            // nothing to do here
        }

        private void addXADataSourceProperty(final ModelNode address, final String name, final String value) throws IOException, MgmtOperationException {
            final ModelNode propertyAddress = address.clone();
            propertyAddress.add("xa-datasource-properties", name);
            propertyAddress.protect();

            final ModelNode operation = new ModelNode();
            operation.get(OP).set("add");
            operation.get(OP_ADDR).set(propertyAddress);
            operation.get("value").set(value);

            log.info("operation: " + operation);
            executeOperation(operation);
        }

        private void removeXADataSource() throws Exception {
            final ModelNode address = new ModelNode();
            address.add("subsystem", "datasources");
            address.add("xa-data-source", getDatasourceName());
            address.protect();

            final ModelNode operation = new ModelNode();
            operation.get(OP).set("remove");
            operation.get(OP_ADDR).set(address);
            log.info("operation: " + operation);
            executeOperation(operation);
        }
    }

    static class SecurityDomainSetup extends AbstractLoginModuleSecurityDomainTestCaseSetup {

        @Override
        protected String getSecurityDomainName() {
            return "CrashRecoveryDomain";
        }

        @Override
        protected String getLoginModuleName() {
            return "ConfiguredIdentity";
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        protected Map<String, String> getModuleOptions() {
            Map<String, String> moduleOptions = new HashMap<String, String>();
            moduleOptions.put("userName", "crashrec");
            moduleOptions.put("password", "crashrec");
            moduleOptions.put("principal", "crashrec");
            return moduleOptions;
        }
    }

    static class SecurityDomain0Setup extends AbstractLoginModuleSecurityDomainTestCaseSetup {

        @Override
        protected String getSecurityDomainName() {
            return "CrashRecoveryDomain0";
        }

        @Override
        protected String getLoginModuleName() {
            return "ConfiguredIdentity";
        }

        @Override
        protected boolean isRequired() {
            return true;
        }

        @Override
        protected Map<String, String> getModuleOptions() {
            Map<String, String> moduleOptions = new HashMap<String, String>();
            moduleOptions.put("userName", "crash0");
            moduleOptions.put("password", "crash0");
            moduleOptions.put("principal", "crash0");
            return moduleOptions;
        }
    }

}
