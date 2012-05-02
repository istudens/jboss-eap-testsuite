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

import org.jboss.arquillian.container.test.api.Config;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.base.AbstractMgmtServerSetupTask;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.jbossts.client.TransactionLog;
import org.jboss.as.test.jbossts.common.*;
import org.jboss.as.test.jbossts.crashrec.common.CrashBeanCommon;
import org.jboss.as.test.jbossts.crashrec.common.RecoveredXid;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.shared.integration.ejb.security.Util;
import org.jboss.byteman.agent.submit.Submit;
import org.jboss.byteman.contrib.dtest.InstrumentedClass;
import org.jboss.byteman.contrib.dtest.InstrumentedInstance;
import org.jboss.byteman.contrib.dtest.Instrumentor;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.*;

import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.transaction.*;
import java.io.*;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Base class for single node crash recovery tests.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
public abstract class CrashRecoveryTestBase {
    protected static final Logger log = Logger.getLogger(CrashRecoveryTestBase.class);

    protected static final String CRASHREC_CONTAINER = "jbossts";

    protected static final String STORE_TYPE = System.getProperty("jbossts.store.type");
    protected static final String SERVER_PATH = System.getProperty("jboss.dist");
    protected static final boolean WIPE_OUT_TXS = Boolean.getBoolean("jbossts.wipeout.txs");

    protected static Instrumentor instrumentor = null;
    protected InstrumentedClass instrumentedTestSynchronization;
    protected InstrumentedClass instrumentedTestXAResource;

    protected TransactionLog store = null;
    protected int uidsBeforeTest = 0;
    protected Set<RecoveredXid> indoubtXidsBeforeTest = null;

    private static int recoveryManagerPort;
    private static String recoveryManagerHost;
    private static JMXServiceURL remoteJmxUrl;

    @ArquillianResource
    protected ContainerController controller;

    @ArquillianResource
    protected Deployer deployer;


    protected abstract String getDeploymentName();

    protected abstract void initBeforeTestExecution(String testName) throws Throwable;

    protected abstract void checkAfterTestExecution(String testName, boolean expectRollback) throws Throwable;

    protected abstract void callCrashTest(String testName) throws Throwable;

    protected abstract Set<RecoveredXid> checkXidsInDoubt() throws Throwable;



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
        deployer.deploy(getDeploymentName());
        log.info("===deployment deployed===");

        // initiate transaction "environment" before the test
        initTxLog();

        uidsBeforeTest = getPendingUids(store);

        if (WIPE_OUT_TXS)
            wipeOutTxsInDoubt();

        indoubtXidsBeforeTest = checkXidsInDoubt();
        if (indoubtXidsBeforeTest.size() > 0)
            log.info(indoubtXidsBeforeTest.size() + " txs in doubt in database before test run");

        // instument test classes
        instrumentor.setRedirectedSubmissionsFile(null);
        instrumentedTestSynchronization = instrumentor.instrumentClass(TestSynchronization.class);
        instrumentedTestXAResource = instrumentor.instrumentClass(TestXAResource.class);
    }

    @After
    public void after() throws Throwable {
        try {
            instrumentor.removeAllInstrumentation();

            // clear indoubt txs in the database
            Set<RecoveredXid> xidsInDoubtAfterTest = checkXidsInDoubt();
            wipeOutTxsInDoubt(indoubtXidsBeforeTest, xidsInDoubtAfterTest);

            // undeploy the tests
            deployer.undeploy(getDeploymentName());
            log.info("===deployment undeployed===");

        } finally {
            try {
                closeTxLog();
            } finally {
                // shut down the appserver
                controller.stop(CRASHREC_CONTAINER);
                log.info("===appserver stopped===");
            }
        }
    }

    protected void rebootServer(ContainerController controller) throws Throwable {
        instrumentor.removeLocalState();
        File rulesFile = File.createTempFile("jbosstscrashrectests", ".btm");
        rulesFile.deleteOnExit();
        instrumentor.setRedirectedSubmissionsFile(rulesFile);

        instrumentedTestSynchronization = instrumentor.instrumentClass(TestSynchronization.class);
        instrumentedTestXAResource = instrumentor.instrumentClass(TestXAResourceRecovered.class);

        // just let Arquillian know that the server has been killed
        // note: in fact the server has been killed by Byteman before
        controller.kill(CRASHREC_CONTAINER);

        // start up the server
        String serverJvmArguments = System.getProperty("server.jvm.args").trim();
        String bytemanJvmArguments = System.getProperty("byteman.server.jvm.args").trim();
        bytemanJvmArguments = bytemanJvmArguments.replaceFirst("byteman-dtest.jar", "byteman-dtest.jar,script:" + rulesFile.getCanonicalPath());
        controller.start(CRASHREC_CONTAINER, new Config().add("javaVmArguments", serverJvmArguments + " " + bytemanJvmArguments + " -Djboss.inst=" + SERVER_PATH).map());

        // update JMX proxies after server restart
        for (int j=0; j < 20; j++) {
            // wait until the deployment is fully up
            try {
                store.initProxies();
                break;
            } catch (UndeclaredThrowableException e) {
                if (e.getCause() instanceof javax.management.InstanceNotFoundException) {
                    store.close();
                    Thread.sleep(TimeoutUtil.adjust(5 * 1000));
                } else {
                    throw e;
                }
            }
        }
    }


    @Test
    /**
     * This one is just for verification of the whole environment configuration.
     */
    public void none() throws Throwable {
        String testName = "none";

        instrumentor.injectOnCall(CrashBeanCommon.class, "afterEntityUpdate", "$0.enlistSynchronization(1), $0.enlistXAResource(1)");

        initBeforeTestExecution(testName);

        execute(testName, false);

        instrumentedTestSynchronization.assertKnownInstances(1);
        instrumentedTestSynchronization.assertMethodCalled("beforeCompletion");
        instrumentedTestSynchronization.assertMethodCalled("afterCompletion");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        checkAfterTestExecution(testName, false);
    }

    /**
     * Halts server at prepare phase on TestXAResource after the prepare was invoked on JPA/JMS XA resource.
     * @throws Throwable
     */
    @Test
    public void prepareHalt() throws Throwable {
        String testName = "prepare_halt";

        instrumentor.injectOnCall(CrashBeanCommon.class, "afterEntityUpdate", "$0.enlistSynchronization(1), $0.enlistXAResource(1)");
        instrumentor.crashAtMethodEntry(TestXAResource.class, "prepare");

        initBeforeTestExecution(testName);

        execute(testName, true);

        instrumentedTestSynchronization.assertKnownInstances(1);
        instrumentedTestSynchronization.assertMethodCalled("beforeCompletion");
        instrumentedTestSynchronization.assertMethodNotCalled("afterCompletion");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodNotCalled("commit");

        rebootServer(controller);

        doRecovery();
        doRecovery();

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("recover");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodNotCalled("commit");

        checkAfterTestExecution(testName, true);

        // check the tx log state
        int pendingUids = getPendingUids(store);
        assertTrue("object store error", pendingUids != -1);
        assertTrue("recovery failed, some uids still left in the tx log", pendingUids <= uidsBeforeTest);

        assertEquals("There are still some un-recovered txs in the database after crash recovery.", indoubtXidsBeforeTest.size(), checkXidsInDoubt().size());
    }

    /**
     * Halts server at commit phase on TestXAResource after commit was invoked on JPA/JMS XA resource.
     * This scenario is not fully supported yet.
     * It requires to set -DJTAEnvironmentBean.xaAssumeRecoveryComplete=true to pass, but it helps only on JTA, not JTS.
     * For more details see JBTM-860.
     * @throws Throwable
     */
    @Test
    @Ignore("JBTM-860")
    public void commitHalt() throws Throwable {
        String testName = "commit_halt";

        instrumentor.injectOnCall(CrashBeanCommon.class, "afterEntityUpdate", "$0.enlistSynchronization(1), $0.enlistXAResource(1)");
        instrumentor.crashAtMethodEntry(TestXAResource.class, "commit");

        initBeforeTestExecution(testName);

        execute(testName, true);

        instrumentedTestSynchronization.assertKnownInstances(1);
        instrumentedTestSynchronization.assertMethodCalled("beforeCompletion");
        instrumentedTestSynchronization.assertMethodNotCalled("afterCompletion");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        rebootServer(controller);

        doRecovery();
        doRecovery();

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("recover");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        checkAfterTestExecution(testName, false);

        // check the tx log state
        int pendingUids = getPendingUids(store);
        assertTrue("object store error", pendingUids != -1);
        assertTrue("recovery failed, some uids still left in the tx log", pendingUids <= uidsBeforeTest);

        // check the in-doubt txs on the database side
        assertEquals("some in-doubt txs still left in the database", indoubtXidsBeforeTest.size(), checkXidsInDoubt().size());
    }

    /**
     * Halts server at commit phase on TestXAResource before commit was done on JPA/JMS XA resource.
     * @throws Throwable
     */
    @Test
    public void commitHaltRev() throws Throwable {
        String testName = "commit_halt_rev";

        instrumentor.injectOnCall(CrashBeanCommon.class, "beforeEntityUpdate", "$0.enlistSynchronization(1), $0.enlistXAResource(1)");
        instrumentor.crashAtMethodEntry(TestXAResource.class, "commit");

        initBeforeTestExecution(testName);

        execute(testName, true);

        instrumentedTestSynchronization.assertKnownInstances(1);
        instrumentedTestSynchronization.assertMethodCalled("beforeCompletion");
        instrumentedTestSynchronization.assertMethodNotCalled("afterCompletion");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        rebootServer(controller);

        doRecovery();
        doRecovery();

//        debugInstrumentedClass(instrumentedTestXAResource, "prepare", "recover", "rollback", "commit");
        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("recover");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        checkAfterTestExecution(testName, false);

        // check the tx log state
        int pendingUids = getPendingUids(store);
        assertTrue("object store error", pendingUids != -1);
        assertTrue("recovery failed, some uids still left in the tx log", pendingUids <= uidsBeforeTest);

        assertEquals("There are still some un-recovered txs in the database after crash recovery.", indoubtXidsBeforeTest.size(), checkXidsInDoubt().size());
    }

    protected void execute(String testName, boolean expectFailure) throws Throwable {
        boolean clientTx = false;    //FIXME
        UserTransaction tx = null;
        try {
            if (clientTx)
                tx = startTx();

            // run the crash test
            callCrashTest(testName);

            if (clientTx)
                commitTx(tx);

        } catch (Throwable e) {
            if (clientTx)
                rollbackTx(tx);

            if (! expectFailure) {
                log.warn("Caught an unexpected exception", e);
                throw e;
            } else {
                log.info("Failure expected", e);
            }
        }
    }

    protected void initTxLog() throws Throwable {
        store = new TransactionLog(remoteJmxUrl.toString());

        // this test may halt the VM so make sure the transaction log is empty
        // before starting the test - then the pass/fail check is simply to
        // test whether or not the log is empty (see recoverUids() below).
        try {
            store.clearXids(STORE_TYPE);
        } catch (Exception ignore) {
        }
    }

    protected void closeTxLog() {
            if (store != null) {
                try {
                    store.close();
                    store = null;
                } catch (Exception e) {
                    log.warn("Cannot close the transaction log!", e);
                }
            }
    }

    /**
     * Checks pending transactions in the transaction log.
     *
     * @return number of pending transactions in the transaction log.
     */
    protected int getPendingUids(TransactionLog store) {
        try {
            return store.getIds(STORE_TYPE).size();
        } catch (Throwable e) {
            log.warn(e);
            return -1;
        }
    }

    protected UserTransaction startTx() throws NamingException, SystemException, NotSupportedException {
        // TODO - see org.jboss.as.test.integration.ejb.remote.client.api.tx.EJBClientUserTransactionTestCase for example of client transaction
        // EJBClient.getUserTransaction(nodeName);

//        UserTransaction tx = lookup("UserTransaction", UserTransaction.class);
//        tx.begin();
//        return tx;
        return null;    // check if it is supported to lookup UserTransaction on client side
    }

    protected void commitTx(UserTransaction tx)
            throws SystemException, RollbackException, HeuristicRollbackException, HeuristicMixedException {
        tx.commit();
    }

    protected void rollbackTx(UserTransaction tx) throws SystemException {
        tx.rollback();
    }

    /**
     * Wipes out all the txs in doubt from database.
     *
     * @return true in success, fail otherwise
     */
    protected void wipeOutTxsInDoubt() {
        wipeOutTxsInDoubt(null);
    }

    /**
     * Wipes out only new txs in doubt from database after test run.
     *
     * @param xidsInDoubtBeforeTest txs in doubt in database before test run
     * @param xidsInDoubtBeforeTest txs in doubt in database after test run
     * @return true in success, fail otherwise
     */
    protected void wipeOutTxsInDoubt(Set<RecoveredXid> xidsInDoubtBeforeTest, Set<RecoveredXid> xidsInDoubtAfterTest) {
        Set<RecoveredXid> xidsToRecover = new HashSet<RecoveredXid>(xidsInDoubtAfterTest);
        xidsToRecover.removeAll(xidsInDoubtBeforeTest);

        if (! xidsToRecover.isEmpty()) {
            wipeOutTxsInDoubt(xidsToRecover);
        }
    }

    /**
     * Wipes out in-doubt txs from database/jms resource according to a xidsToRecover list.
     *
     * @param xidsToRecover list of xids to recover
     * @return true in success, fail otherwise
     */
    protected abstract void wipeOutTxsInDoubt(Set<RecoveredXid> xidsToRecover);


    // stolen from JBossTS project from CrashRecoveryDelays
    // prod the recovery manager via its socket. This avoid any sleep delay.
    protected static void doRecovery() throws InterruptedException {
        log.info("doRecovery#host = " + recoveryManagerHost);
        log.info("doRecovery#port = " + recoveryManagerPort);

        BufferedReader in = null;
        PrintStream out = null;
        Socket sckt = null;

        try {
            sckt = new Socket(recoveryManagerHost, recoveryManagerPort);

            in = new BufferedReader(new InputStreamReader(sckt.getInputStream()));
            out = new PrintStream(sckt.getOutputStream());

            // Output ping message
            out.println("SCAN");
            out.flush();

            // Receive pong message
            String inMessage = in.readLine();

            log.trace("inMessage = " + inMessage);
            if (!inMessage.equals("DONE")) {
                log.error("Recovery failed with message: " + inMessage);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }

                if (out != null) {
                    out.close();
                }

                sckt.close();
            } catch (Exception e) {
            }
        }
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


    private void debugInstrumentedClass(InstrumentedClass clazz, String... methods) {
        log.info(clazz + ".getInstances().size()=" + clazz.getInstances().size());
        for (InstrumentedInstance instance: clazz.getInstances()) {
            log.info("instance.toString()="+instance.toString());
            for (String method: methods) {
                log.info("instance.getInvocationCount(\"" + method + "\")="+instance.getInvocationCount(method));
            }
        }
    }


    static class TxEnvironmentCheck extends AbstractMgmtServerSetupTask {

        @Override
        protected void doSetup(final ManagementClient managementClient) throws Exception {
            log.debug("TxEnvironmentCheck.doSetup");
            recoveryManagerPort = getRecoveryManagerPort();
            recoveryManagerHost = managementClient.getMgmtAddress();
            remoteJmxUrl = managementClient.getRemoteJMXURL();
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            log.debug("TxEnvironmentCheck.tearDown");
            // nothing to do here
        }

        private int getRecoveryManagerPort() throws IOException {
            /*    /socket-binding-group=standard-sockets/socket-binding=txn-recovery-environment:read-attribute(name=port)  */
            final ModelNode address = new ModelNode();
            address.add("socket-binding-group", "standard-sockets");
            address.add("socket-binding", "txn-recovery-environment");
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
            operation.get(OP_ADDR).set(address);
            operation.get("name").set("port");

            try {
                return executeOperation(operation).asInt();
            } catch (MgmtOperationException ignored) {
            }
            return -1;
        }
    }

}
