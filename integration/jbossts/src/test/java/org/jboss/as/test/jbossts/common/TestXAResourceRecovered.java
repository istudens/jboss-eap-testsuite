/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and/or its affiliates,
 * and individual contributors as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2010,
 * @author JBoss, by Red Hat.
 */
package org.jboss.as.test.jbossts.common;

import org.jboss.logging.Logger;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * Implementation of XAResource for use in txbridge recovery tests.
 *
 * Note: TestXAResourceRecovered cannot directly extend TestXAResource, otherwise
 * TestXAResource will be every time instrumented together with TestXAResourceRecovered
 * which will deny the distinction between them and will increase the number of
 * known instrumented instances of TestXAResource, for example in InboundCrashRecoveryTests#testCrashOneLog
 * if the recovery process is run on the server side before the following assert:
 *   ...
 *   execute(baseURL + TestClient.URL_PATTERN, false);
 *   // if recovery process is run before the next assert and if TestXAResourceRecovered
 *   // extends TestXAResource then the assert will fail because of 2 known instances
 *   instrumentedTestXAResource.assertKnownInstances(1);
 *   ...
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com) 2010-01
 * @author Ivo Studensky (istudens@redhat.com)
 */
public class TestXAResourceRecovered extends TestXAResourceCommon implements XAResource {
    private static Logger log = Logger.getLogger(TestXAResourceRecovered.class);

    @Override
    public void rollback(Xid xid) throws XAException {
        log.info("TestXAResourceRecovered.rollback(Xid=" + xid + ")");

        TestXAResourceRecoveryHelper.getInstance().removeLog(xid);
    }

    @Override
    public void commit(Xid xid, boolean b) throws XAException {
        log.info("TestXAResourceRecovered.commit(Xid=" + xid + ", b=" + b + ")");

        TestXAResourceRecoveryHelper.getInstance().removeLog(xid);
    }

    @Override
    public Xid[] recover(int i) throws XAException {
        log.info("TestXAResourceRecovered.recover(i=" + i + ")");

        return TestXAResourceRecoveryHelper.getInstance().recover();
    }

    @Override
    public void forget(Xid xid) throws XAException {
        log.info("TestXAResource.forget(Xid=" + xid + ")");

        TestXAResourceRecoveryHelper.getInstance().removeLog(xid);
    }

    @Override
    public void end(Xid xid, int i) throws XAException {
        super.end(xid, i);
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        return super.getTransactionTimeout();
    }

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException {
        return super.isSameRM(xaResource);
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        return super.prepare(xid);
    }

    @Override
    public boolean setTransactionTimeout(int i) throws XAException {
        return super.setTransactionTimeout(i);
    }

    @Override
    public void start(Xid xid, int i) throws XAException {
        super.start(xid, i);
    }

    @Override
    public String toString() {
        return new String("TestXAResourceRecovered(" + super.toString() + ")");
    }

}
