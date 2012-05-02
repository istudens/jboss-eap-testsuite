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
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.integration.management.base.AbstractMgmtServerSetupTask;
import org.jboss.as.test.jbossts.crashrec.common.RecoveredXid;
import org.jboss.as.test.jbossts.crashrec.jms.*;
import org.jboss.as.test.jbossts.extension.ManualServerSetup;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.runner.RunWith;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


/**
 * Crash recovery tests involving a JMS XA resource.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ManualServerSetup({
        TxEnvironmentSetup.class,
        CrashRecoveryTestBase.TxEnvironmentCheck.class,
        JMSCrashRecoveryTestCase.JmsQueueSetup.class})
public class JMSCrashRecoveryTestCase extends CrashRecoveryTestBase {
    private static final Logger log = Logger.getLogger(JMSCrashRecoveryTestCase.class);

    private static final String ARCHIVE_NAME = "crashrecovery-jms";

    private static final String CONNECTION_FACTORY_JNDI_NAME = System.getProperty("jbossts.jms.connection.factory.jndi", "java:/JmsXA");

    @Deployment(name = ARCHIVE_NAME, managed = false, testable = false)
    @TargetsContainer(CRASHREC_CONTAINER)
    public static Archive<?> deploy() {
        return ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar")
                .addPackage("org.jboss.as.test.jbossts.common")
                .addPackage("org.jboss.as.test.jbossts.crashrec.common")
                .addPackage("org.jboss.as.test.jbossts.crashrec.jms")
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.jts\n"), "MANIFEST.MF");
    }


    @Override
    protected String getDeploymentName() {
        return ARCHIVE_NAME;
    }

    @Override
    protected void initBeforeTestExecution(String testName) throws Throwable {
        // clean the test queue
        String message = null;
        do {
            message = lookupMessageCheckerBean().checkMessageResult(CONNECTION_FACTORY_JNDI_NAME);
        } while (message != null);
    }

    @Override
    protected void checkAfterTestExecution(String testName, boolean expectRollback) throws Throwable {
        // checking the test-queue state after recovering
        String message = lookupMessageCheckerBean().checkMessageResult(CONNECTION_FACTORY_JNDI_NAME);
        if (expectRollback) {
            assertNull("got unexpected message", message);
        } else {
            assertEquals("got wrong message", message, testName);
        }
    }

    @Override
    protected void callCrashTest(String testName) throws Throwable {
        lookupCrashBean().testXA(CONNECTION_FACTORY_JNDI_NAME, testName);
    }

    @Override
    protected Set<RecoveredXid> checkXidsInDoubt() throws Throwable {
        return lookupCrashHelper().checkXidsInDoubt(CONNECTION_FACTORY_JNDI_NAME);
    }

    /**
     * Wipes out in-doubt txs from JMS resource (adapter) according to xidsToRecover list.
     */
    @Override
    protected void wipeOutTxsInDoubt(Set<RecoveredXid> xidsToRecover) {
        log.info("wiping out in-doubt txs");
        try {
            lookupCrashHelper().wipeOutTxsInDoubt(CONNECTION_FACTORY_JNDI_NAME, xidsToRecover);
        } catch (Exception e) {
            log.warn(e);
        }
    }


    private JMSCrashHelperRemote lookupCrashHelper() throws Exception {
        return lookup(JMSCrashHelperRemote.class, JMSCrashHelper.class, ARCHIVE_NAME);
    }

    private JMSCrashBeanRemote lookupCrashBean() throws Exception {
        return lookup(JMSCrashBeanRemote.class, JMSCrashBean.class, ARCHIVE_NAME);
    }

    private MessageCheckerRemote lookupMessageCheckerBean() throws Exception {
        return lookup(MessageCheckerRemote.class, MessageChecker.class, ARCHIVE_NAME);
    }


    static class JmsQueueSetup extends AbstractMgmtServerSetupTask {

        JMSOperations jmsOperations;

        @Override
        protected void doSetup(final ManagementClient managementClient) throws Exception {
            log.debug("JmsQueueSetup.doSetup");
            jmsOperations = JMSOperationsProvider.getInstance(managementClient);
            try {
                jmsOperations.removeJmsQueue(JMSCrashBean.TEST_QUEUE_NAME);
            } catch (Exception ignored) {
            }
            jmsOperations.createJmsQueue(JMSCrashBean.TEST_QUEUE_NAME, JMSCrashBean.TEST_QUEUE_JNDI_NAME);
            log.info("===test queue created===");
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            log.debug("JmsQueueSetup.tearDown");
            jmsOperations.removeJmsQueue(JMSCrashBean.TEST_QUEUE_NAME);
            log.info("===test queue removed===");
            jmsOperations.close();
        }

    }

}
