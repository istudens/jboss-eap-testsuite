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

import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.management.base.AbstractMgmtServerSetupTask;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.io.IOException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

/**
 * Settings of transaction subsystem shared by all crash rec tests.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class TxEnvironmentSetup extends AbstractMgmtServerSetupTask {
    protected static final Logger log = Logger.getLogger(TxEnvironmentSetup.class);

    protected static final boolean JBOSS_JTS = Boolean.getBoolean("jbossts.jts");

    protected static final String JACORB_TRANSACTIONS_JTA = "spec";
    protected static final String JACORB_TRANSACTIONS_JTS = "on";

    @Override
    protected void doSetup(final ManagementClient managementClient) throws Exception {
        log.info("TxEnvironmentSetup.doSetup");
        boolean restartServer = false;
        // first check and enable recovery listener
        if (! checkRecoveryListener()) {
            enableRecoveryListener();
//            restartServer = true;     // in this case restart is not needed as we call recovery listener always after server crash
        }
        // check JTA/JTS setting
        if (JBOSS_JTS) {
            if (!checkJTSOnTransactions() || JACORB_TRANSACTIONS_JTA.equalsIgnoreCase(checkTransactionsOnJacorb())) {
                setJTS(true);
                restartServer = true;
            }
        } else {    // JTA
            if (checkJTSOnTransactions() || JACORB_TRANSACTIONS_JTS.equalsIgnoreCase(checkTransactionsOnJacorb())) {
                setJTS(false);
                restartServer = true;
            }
        }
        if (restartServer) {
            reload();
            // wait for reload
            Thread.sleep(TimeoutUtil.adjust(3 * 1000));
        }
    }

    @Override
    public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
        // nothing to do here
    }

    private void setJTS(boolean enabled) throws IOException, MgmtOperationException {
        /*     /subsystem=transactions:write-attribute(name=jts,value=false|true)   */
        ModelNode address = new ModelNode();
        address.add("subsystem", "transactions");
        ModelNode operation = new ModelNode();
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("jts");
        operation.get("value").set(enabled);
        log.info("operation=" + operation);
        executeOperation(operation);

        String transactionsOnJacorb = (enabled) ? JACORB_TRANSACTIONS_JTS : JACORB_TRANSACTIONS_JTA;
        address = new ModelNode();
        address.add("subsystem", "jacorb");
        operation = new ModelNode();
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("transactions");
        operation.get("value").set(transactionsOnJacorb);
        log.info("operation=" + operation);
        executeOperation(operation);
    }

    private boolean checkJTSOnTransactions() throws IOException, MgmtOperationException {
        /*     /subsystem=transactions:read-attribute(name=jts)   */
        final ModelNode address = new ModelNode();
        address.add("subsystem", "transactions");
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("jts");

        return executeOperation(operation).asBoolean();
    }

    private String checkTransactionsOnJacorb() throws IOException, MgmtOperationException {
        /*     /subsystem=jacorb:read-attribute(name=transactions)   */
        final ModelNode address = new ModelNode();
        address.add("subsystem", "jacorb");
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("transactions");

        return executeOperation(operation).asString();
    }

    private void enableRecoveryListener() throws IOException, MgmtOperationException {
        /*     /subsystem=transactions:write-attribute(name=recovery-listener,value=true)   */
        final ModelNode address = new ModelNode();
        address.add("subsystem", "transactions");
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("recovery-listener");
        operation.get("value").set("true");
        log.info("operation=" + operation);
        executeOperation(operation);
    }

    private boolean checkRecoveryListener() throws IOException {
        /*     /subsystem=transactions:read-attribute(name=recovery-listener)   */
        final ModelNode address = new ModelNode();
        address.add("subsystem", "transactions");
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get("name").set("recovery-listener");

        try {
            return executeOperation(operation).asBoolean();
        } catch (MgmtOperationException ignored) {
        }
        return false;
    }

    private void reload() throws IOException, MgmtOperationException {
        /*      :reload()     */
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("reload");
        operation.get(OP_ADDR).set(new ModelNode());
        log.info("operation=" + operation);
        executeOperation(operation);
    }

}
