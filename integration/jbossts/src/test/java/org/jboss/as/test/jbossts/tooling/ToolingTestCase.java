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
 * Transaction Tooling tests.
 * The tests invoke un-recoverable events and then clean the tx log with tooling via Management API.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
@ManualServerSetup({TxEnvironmentSetup.class, ToolingTestBase.JmsQueueSetup.class})
public class ToolingTestCase extends ToolingTestBase {
    protected static final Logger log = Logger.getLogger(ToolingTestCase.class);

    /**
     * Crashes the server at commit phase on TestXAResource after the commit was invoked on JMS XA resource.
     * This scenario is not fully supported yet by JBossTS which allows us to use it for tooling testing.
     * This test case will most likely stop working once JBTM-860 is done.
     */
    @Test
    public void commitHalt(@ArquillianResource ManagementClient managementClient) throws Throwable {
        this.managementClient = managementClient;

        cleanLog();

        instrumentor.injectOnCall(ToolingCrashBean.class, "after", "$0.enlistXAResource(1)");
        instrumentor.crashAtMethodEntry(TestXAResource.class, "commit");

        execute(true);

//        debugInstrumentedClass(instrumentedJMSXAResource, "prepare", "rollback", "commit", "start", "end");
        instrumentedJMSXAResource.assertKnownInstances(1);
        instrumentedJMSXAResource.assertMethodCalled("prepare");
        instrumentedJMSXAResource.assertMethodNotCalled("rollback");
        instrumentedJMSXAResource.assertMethodCalled("commit");

        instrumentedTestXAResource.assertKnownInstances(1);
        instrumentedTestXAResource.assertMethodCalled("prepare");
        instrumentedTestXAResource.assertMethodNotCalled("rollback");
        instrumentedTestXAResource.assertMethodCalled("commit");

        rebootServer(controller);

        probeLog();
        List<ModelNode> indoubtTxs = getTxsFromLog();
        assertEquals("Expects one indoubt tx in txlog", 1, indoubtTxs.size());

        String transactionId = indoubtTxs.get(0).asString();
        log.info("got a transaction " + transactionId);
        log.info("tx type " + getTypeOfTx(transactionId));

        List<ModelNode> participants = getParticipantsOfTx(transactionId);
        assertEquals("Expects two participants of the tx", 2, participants.size());

        for (ModelNode participant : participants) {
            String participantId = participant.asString();
            log.info("got a participant: " + participantId);

            for (Property property : getParticipantResources(transactionId, participantId)) {
                log.info("participant's property " + property.getName() + " = " + property.getValue());
                if (property.getName().equals("status")) {
                    assertEquals("Wrong participant status", "PREPARED", property.getValue().asString());
                }

                // just try it if it can pass, but no action triggered actually :)
                recoverParticipant(transactionId, participantId);
            }
        }

        deleteTxFromLog(transactionId);

        probeLog();
        indoubtTxs = getTxsFromLog();
        assertTrue("Some unexpected txs in txlog", indoubtTxs.isEmpty());
    }

}
