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

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.jbossts.extension.ManualServerSetup;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.runner.RunWith;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;


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
        JPAClusterCrashRecoveryTestCase.XANoRecoveryDatasourceSetup.class,
        JPAClusterCrashRecoveryTestCase.XARecoveryDatasourceSetup.class})
public class JPAClusterCrashRecoveryTestCase extends JPACrashRecoveryTestCase {
    private static final Logger log = Logger.getLogger(JPAClusterCrashRecoveryTestCase.class);

    private static final String SECOND_RECOVERY_DATASOURCE_NAME = System.getProperty("jbossts.recovery.second.datasource", "RecoveryDS");
    private static final String SECOND_RECOVERY_DATASOURCE_JNDI = "java:jboss/xa-datasources/" + SECOND_RECOVERY_DATASOURCE_NAME;

    private static final String DS_SECOND_JDBC_URL = System.getProperty("ds.second.jdbc.url");

    /**
     * Make the work datasource non-recoverable.
     */
    static class XANoRecoveryDatasourceSetup extends JPACrashRecoveryTestCase.XADatasourceSetup {

        @Override
        protected void addAdditionalSettings(final ModelNode address) throws Exception {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get("name").set("no-recovery");
            operation.get("value").set("true");
            log.info("operation: " + operation);
            executeOperation(operation);

        }
    }

    /**
     * Create another datasource bound to a different cluster node that is able to recover transactions.
     */
    static class XARecoveryDatasourceSetup extends JPACrashRecoveryTestCase.XADatasourceSetup {

        @Override
        protected String getDatasourceName() throws Exception {
            return SECOND_RECOVERY_DATASOURCE_NAME;
        }

        @Override
        protected String getDatasourceJndiName() throws Exception {
            return SECOND_RECOVERY_DATASOURCE_JNDI;
        }

        @Override
        protected String getJdbcUrl() throws Exception {
            if (DS_SECOND_JDBC_URL == null)
                throw new RuntimeException("System property ds.second.jdbc.url cannot be null!");
            return DS_SECOND_JDBC_URL;
        }
    }

}
