/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.test.jbossts.tooling;

import org.jboss.arquillian.container.test.api.*;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.integration.management.base.AbstractMgmtServerSetupTask;
import org.jboss.as.test.integration.management.base.AbstractMgmtTestBase;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.jbossts.common.TestXAResource;
import org.jboss.as.test.jbossts.common.TestXAResourceRecovered;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.byteman.agent.submit.Submit;
import org.jboss.byteman.contrib.dtest.InstrumentedClass;
import org.jboss.byteman.contrib.dtest.InstrumentedInstance;
import org.jboss.byteman.contrib.dtest.Instrumentor;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.*;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.junit.Assert.assertTrue;


/**
 * Base test class for all tooling tests.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
public abstract class ToolingTestBase extends AbstractMgmtTestBase {
    protected static final Logger log = Logger.getLogger(ToolingTestBase.class);

    protected static final String CRASHREC_CONTAINER = "jbossts";
    protected static final String ARCHIVE_NAME = "toolingtests";

    protected static final String SERVER_PATH = System.getProperty("jboss.dist");

    protected static Instrumentor instrumentor = null;
    protected InstrumentedClass instrumentedTestXAResource;
    protected InstrumentedClass instrumentedJMSXAResource;

    protected ManagementClient managementClient;

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    protected Deployer deployer;

    @Deployment(name = ARCHIVE_NAME, managed = false, testable = false)
    @TargetsContainer(CRASHREC_CONTAINER)
    public static Archive<?> deploy() {
        return ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar")
                .addPackage(TestXAResource.class.getPackage())
                .addClasses(ToolingCrashBean.class, ToolingCrashBeanRemote.class)
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.jts\n"), "MANIFEST.MF");
    }


    @BeforeClass
    public static void beforeClass() throws Exception {
        if (instrumentor == null) {
            instrumentor = new Instrumentor(new Submit(), 1199);
        }
    }

    @Before
    public void before() throws Throwable {

        // start up the appserver
        String serverJvmArguments = System.getProperty("server.jvm.args").trim();
        String bytemanJvmArguments = System.getProperty("byteman.server.jvm.args").trim();
        controller.start(CRASHREC_CONTAINER, new Config().add("javaVmArguments", serverJvmArguments + " " + bytemanJvmArguments + " -Djboss.inst=" + SERVER_PATH).map());
        log.info("===appserver started===");

        // deploy the tests
        deployer.deploy(ARCHIVE_NAME);
        log.info("===deployment deployed===");


        // instument test classes
        instrumentor.setRedirectedSubmissionsFile(null);
        instrumentedTestXAResource = instrumentor.instrumentClass(TestXAResource.class);
        instrumentedJMSXAResource = instrumentor.instrumentClass(org.hornetq.ra.HornetQRAXAResource.class);

        // first clean the test queue
//        String message = null;
//        do {
//            message = lookupCrashBean().checkMessageResult();
//        } while (message != null);
    }

    @After
    public void after() throws Throwable {
        try {
            instrumentor.removeAllInstrumentation();

            // undeploy the tests
            deployer.undeploy(ARCHIVE_NAME);
            log.info("===deployment undeployed===");

        } finally {
            // shut down the appserver
            controller.stop(CRASHREC_CONTAINER);
            log.info("===appserver stopped===");
        }
    }

    protected void rebootServer(ContainerController controller) throws Throwable {
        instrumentor.removeLocalState();
        File rulesFile = File.createTempFile("jbosststoolingtests", ".btm");
        rulesFile.deleteOnExit();
        instrumentor.setRedirectedSubmissionsFile(rulesFile);

        instrumentedTestXAResource = instrumentor.instrumentClass(TestXAResourceRecovered.class);
        instrumentedJMSXAResource = instrumentor.instrumentClass(org.hornetq.ra.HornetQRAXAResource.class);

        // just let Arquillian know that the server has been killed
        // note: in fact the server has been killed by Byteman before
        controller.kill(CRASHREC_CONTAINER);

        // start up the server
        String serverJvmArguments = System.getProperty("server.jvm.args").trim();
        String bytemanJvmArguments = System.getProperty("byteman.server.jvm.args").trim();
        bytemanJvmArguments = bytemanJvmArguments.replaceFirst("byteman-dtest.jar", "byteman-dtest.jar,script:" + rulesFile.getCanonicalPath());
        controller.start(CRASHREC_CONTAINER, new Config().add("javaVmArguments", serverJvmArguments + " " + bytemanJvmArguments + " -Djboss.inst=" + SERVER_PATH).map());
    }

    protected void cleanLog() throws IOException, MgmtOperationException {
        probeLog();
        List<ModelNode> indoubtTxs = getTxsFromLog();
        log.warn("Some unexpected txs in txlog");

        for (ModelNode tx : indoubtTxs) {
            deleteTxFromLog(tx.asString());
        }

        probeLog();
        indoubtTxs = getTxsFromLog();
        assertTrue("Still some unexpected txs in txlog", indoubtTxs.isEmpty());
    }

    protected void probeLog() throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store:probe   */
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("probe");
        operation.get(OP_ADDR).set(getLogStoreAddress());
        log.info("operation=" + operation);
        executeOperation(operation);
    }

    protected List<ModelNode> getTxsFromLog() throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store:read-children-names(child-type=transactions)   */
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).set(getLogStoreAddress());
        operation.get(CHILD_TYPE).set("transactions");
        log.info("operation=" + operation);
        final ModelNode txsFromLog = executeOperation(operation);
        return txsFromLog.asList();
    }

    protected String getTypeOfTx(String transactionId) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store/transactions=0\:ffff7f000001\:-1a4f3240\:4f90fcb3\:c:read-attribute(name=type)   */
        final ModelNode address = getLogStoreAddress();
        address.add("transactions", transactionId);
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("type");
        log.info("operation=" + operation);
        final ModelNode txType = executeOperation(operation);
        return txType.asString();
    }

    protected List<ModelNode> getParticipantsOfTx(String transactionId) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store/transactions=0\:ffff7f000001\:-1a4f3240\:4f90fcb3\:c:read-children-names(child-type=participants)   */
        final ModelNode address = getLogStoreAddress();
        address.add("transactions", transactionId);
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get(CHILD_TYPE).set("participants");
        log.info("operation=" + operation);
        final ModelNode participants = executeOperation(operation);
        return participants.asList();
    }

    protected List<Property> getParticipantResources(String transactionId, String participantId) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store/transactions=0\:ffff7f000001\:-1a4f3240\:4f90fcb3\:c/participants=java\:\/JmsXA:read-reasources   */
        final ModelNode address = getLogStoreAddress();
        address.add("transactions", transactionId);
        address.add("participants", participantId);
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);
        log.info("operation=" + operation);
        final ModelNode resources = executeOperation(operation);
        return resources.asPropertyList();
    }

    protected void recoverParticipant(String transactionId, String participantId) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store/transactions=0\:ffff7f000001\:-1a4f3240\:4f90fcb3\:c/participants=java\:\/JmsXA:recover   */
        final ModelNode address = getLogStoreAddress();
        address.add("transactions", transactionId);
        address.add("participants", participantId);
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("recover");
        operation.get(OP_ADDR).set(address);
        log.info("operation=" + operation);
        executeOperation(operation);
    }

    protected void deleteTxFromLog(String transactionId) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions/log-store=log-store/transactions=0\:ffff7f000001\:-1a4f3240\:4f90fcb3\:c:delete   */
        final ModelNode address = getLogStoreAddress();
        address.add("transactions", transactionId);
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("delete");
        operation.get(OP_ADDR).set(address);
        log.info("operation=" + operation);
        executeOperation(operation);
    }

    protected ModelNode getLogStoreAddress() {
        final ModelNode address = new ModelNode();
        address.add("subsystem", "transactions");
        address.add("log-store", "log-store");
        return address;
    }

    protected void execute(boolean expectFailure) throws Throwable {
        try {
            // run the crash test
            lookupCrashBean().testXA();
        } catch (Throwable e) {
            if (!expectFailure) {
                log.warn("Caught an unexpected exception", e);
                throw e;
            } else {
                log.info("Failure expected", e);
            }
        }
    }

    protected ToolingCrashBeanRemote lookupCrashBean() throws Exception {
        return lookup(ToolingCrashBeanRemote.class, ToolingCrashBean.class, ARCHIVE_NAME);
    }

    protected <T> T lookup(final Class<T> remoteClass, final Class<?> beanClass, final String archiveName) throws NamingException {
        return lookup(remoteClass, beanClass, "", archiveName);
    }

    protected <T> T lookup(final Class<T> remoteClass, final Class<?> beanClass, final String appName, final String archiveName) throws NamingException {
        String myContext = Util.createRemoteEjbJndiContext(
                appName,
                archiveName,
                "",
                beanClass.getSimpleName(),
                remoteClass.getName(),
                false);

        Context ctx = Util.createNamingContext();
        return remoteClass.cast(ctx.lookup(myContext));
    }

    protected int getInvocationCount(InstrumentedClass clazz, String method) {
        return clazz.getInstances().iterator().next().getInvocationCount(method);
    }

    protected void debugInstrumentedClass(InstrumentedClass clazz, String... methods) {
        log.info(clazz + ".getInstances().size()=" + clazz.getInstances().size());
        for (InstrumentedInstance instance : clazz.getInstances()) {
            log.info("instance.toString()=" + instance.toString());
            for (String method : methods) {
                log.info("instance.getInvocationCount(\"" + method + "\")=" + instance.getInvocationCount(method));
            }
        }
    }

    @Override
    protected ModelControllerClient getModelControllerClient() {
        return managementClient.getControllerClient();
    }


    static class JmsQueueSetup extends AbstractMgmtServerSetupTask {

        JMSOperations jmsOperations;

        @Override
        protected void doSetup(final ManagementClient managementClient) throws Exception {
            log.info("JmsQueueSetup.doSetup");
            jmsOperations = JMSOperationsProvider.getInstance(managementClient);
            try {
                jmsOperations.removeJmsQueue(ToolingCrashBean.TEST_QUEUE_NAME);
            } catch (Exception ignored) {
            }
            jmsOperations.createJmsQueue(ToolingCrashBean.TEST_QUEUE_NAME, ToolingCrashBean.TEST_QUEUE_JNDI_NAME);
            log.info("===test queue created===");
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            log.info("JmsQueueSetup.tearDown");
            jmsOperations.removeJmsQueue(ToolingCrashBean.TEST_QUEUE_NAME);
            log.info("===test queue removed===");
            jmsOperations.close();
        }

    }

}
