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
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.jbossts.common.TestXAResource;
import org.jboss.as.test.jbossts.crashrec.test.TxEnvironmentSetup;
import org.jboss.as.test.jbossts.extension.ManualServerSetup;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Transaction Tooling tests applicable only for JTS tx mode.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ManualServerSetup({TxEnvironmentSetup.class, ToolingTestBase.JmsQueueSetup.class})
public class ToolingTestCaseJTSOnly extends ToolingTestBase {
    protected static final Logger log = Logger.getLogger(ToolingTestCaseJTSOnly.class);

    /**
     * Crashes the server before the Transaction Manager creates a chance to create a record of the TestXAResource's
     * XAResourceRecord. This means that the TestXAResource will be "orphaned" (i.e. the TestXAResource prepared
     * but crashed before the TM recorded the fact).
     * To be precise, it first calls prepare on JMS XA resource and then on TestXAResource.
     * JTA orphan recovery does not work for JTS, thus the orphan is not getting cleaned up automatically.
     *
     * IGNORED since the transaction is recovered in this scenario and only the orphan of TestXAResource stays
     * in the object store. Unfortunetaly there is no way to delete such orphan by transaction tooling as it can
     * only touch the transactions and their participants.
     */
    @Test
    @Ignore
    public void jtsOrphans(@ArquillianResource ManagementClient managementClient) throws Throwable {
        this.managementClient = managementClient;

        cleanLog();

        instrumentor.injectOnCall(ToolingCrashBean.class, "after", "$0.enlistXAResource(1)");
        instrumentor.crashAtMethodExit(TestXAResource.class, "prepare");

        execute(true);

//        debugInstrumentedClass(instrumentedJMSXAResource, "prepare", "rollback", "commit", "start", "end");
        instrumentedJMSXAResource.assertKnownInstances(1);
        instrumentedJMSXAResource.assertMethodCalled("prepare");
        instrumentedJMSXAResource.assertMethodNotCalled("rollback");
        instrumentedJMSXAResource.assertMethodNotCalled("commit");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodNotCalled("commit");

        rebootServer(controller);

        probeLog();
        List<ModelNode> indoubtTxs = getTxsFromLog();
        assertEquals("Expects one indoubt tx in txlog", 1, indoubtTxs.size());

        String transactionId = indoubtTxs.get(0).asString();
        log.info("got a transaction " + transactionId);
        log.info("tx type " + getTypeOfTx(transactionId));

        List<ModelNode> participants = getParticipantsOfTx(transactionId);
        assertTrue("Expects some participants of the tx", participants.size() > 0);
        for (ModelNode participant : participants) {
            String participantId = participant.asString();
            log.info("got a participant: " + participantId);
            recoverParticipant(transactionId, participantId);
        }

        instrumentedJMSXAResource.assertKnownInstances(1);
        instrumentedJMSXAResource.assertMethodCalled("rollback");
        instrumentedJMSXAResource.assertMethodNotCalled("commit");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodNotCalled("commit");

        probeLog();
        indoubtTxs = getTxsFromLog();
        assertEquals("Still expects one indoubt tx in txlog", 1, indoubtTxs.size());

        transactionId = indoubtTxs.get(0).asString();
        log.info("got a transaction " + transactionId);
        log.info("tx type " + getTypeOfTx(transactionId));

        participants = getParticipantsOfTx(transactionId);
        assertEquals("Expects one participant of the tx", 1, participants.size());

        String participantId = participants.iterator().next().asString();
        log.info("got a participant: " + participantId);

        for (Property property : getParticipantResources(transactionId, participantId)) {
            log.info("participant's property " + property.getName() + " = " + property.getValue());
            if (property.getName().equals("status")) {
                assertEquals("Wrong participant status", "PREPARED", property.getValue().asString());
            }
        }

        deleteTxFromLog(transactionId);

        probeLog();
        indoubtTxs = getTxsFromLog();
        assertTrue("Some unexpected txs in txlog", indoubtTxs.isEmpty());
    }

}
