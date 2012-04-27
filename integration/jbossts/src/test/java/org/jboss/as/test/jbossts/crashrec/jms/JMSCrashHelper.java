/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.test.jbossts.crashrec.jms;

import org.jboss.as.test.jbossts.crashrec.common.RecoveredXid;
import org.jboss.as.test.jbossts.crashrec.common.CrashHelperCommon;

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.naming.InitialContext;
import javax.transaction.xa.XAResource;
import java.util.Set;


/**
 * Helper class for checking in-doubt txs on JMS.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@Stateless
@Remote(JMSCrashHelperRemote.class)
public class JMSCrashHelper extends CrashHelperCommon implements JMSCrashHelperRemote {
    private static final String DEFAULT_CONNFACTORY_JNDINAME = "java:/JmsXA";

    private String connectionFactoryJNDIName = DEFAULT_CONNFACTORY_JNDINAME;
    private XAConnection xaConnection = null;
    private XASession xaSession = null;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean wipeOutTxsInDoubt(String connectionFactoryJNDIName, Set<RecoveredXid> xidsToRecover) {
        setConnectionFactoryJNDIName(connectionFactoryJNDIName);
        return wipeOutTxsInDoubt(xidsToRecover);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Set<RecoveredXid> checkXidsInDoubt(String connectionFactoryJNDIName) {
        setConnectionFactoryJNDIName(connectionFactoryJNDIName);
        return checkXidsInDoubt();
    }

    public void setConnectionFactoryJNDIName(String connectionFactoryJNDIName) {
        this.connectionFactoryJNDIName = connectionFactoryJNDIName;
    }

    @Override
    protected XAResource getNewXAResource() throws Exception {
        try {
            if (xaConnection == null) {
                InitialContext ic = new InitialContext();
                XAConnectionFactory xacf = (XAConnectionFactory) ic.lookup(connectionFactoryJNDIName);

                xaConnection = xacf.createXAConnection();
                xaSession = xaConnection.createXASession();
            }


            return xaSession.getXAResource();
        } catch (Exception e) {
            log.warn("Cannot create new XA resource", e);
            throw e;
        }
    }

    // Should not throw out any exception.
    @Override
    protected void closeXAResource() {
        if (xaSession != null) {
            try {
                xaSession.close();
            } catch (Exception ignored) {}
            xaSession = null;
        }
        if (xaConnection != null) {
            try {
                xaConnection.close();
            } catch (Exception ignored) {}
            xaConnection = null;
        }
    }

}
