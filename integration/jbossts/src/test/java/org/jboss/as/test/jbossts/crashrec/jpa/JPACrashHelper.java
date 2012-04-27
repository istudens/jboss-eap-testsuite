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
package org.jboss.as.test.jbossts.crashrec.jpa;

import org.jboss.as.test.jbossts.crashrec.common.CrashHelperCommon;
import org.jboss.as.test.jbossts.crashrec.common.RecoveredXid;
import org.jboss.jca.adapters.jdbc.WrappedConnection;
import org.jboss.jca.adapters.jdbc.WrapperDataSource;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.xa.XAResource;
import java.util.Set;


/**
 * Helper class for playing with txs in doubt.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@Stateless
public class JPACrashHelper extends CrashHelperCommon implements JPACrashHelperRemote {
    public static final String DEFAULT_DATASOURCE_JNDI_NAME = "java:jboss/datasources/ExampleDS";

    private String datasourceName = DEFAULT_DATASOURCE_JNDI_NAME;
    private WrappedConnection connection = null;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Set<RecoveredXid> checkXidsInDoubt(String datasourceName) {
        setDatasourceName(datasourceName);
        return checkXidsInDoubt();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean wipeOutTxsInDoubt(String datasourceName, Set<RecoveredXid> xidsToRecover) {
        setDatasourceName(datasourceName);
        return wipeOutTxsInDoubt(xidsToRecover);
    }

    private void setDatasourceName(String datasourceName) {
        if (datasourceName != null && datasourceName.length() > 0)
            this.datasourceName = datasourceName;
    }

    /**
     * Gets XAResource for a datasource with name ({@link #datasourceName}).
     */
    @Override
    protected XAResource getNewXAResource() throws Exception {
        try {
            if (connection == null) {
                DataSource ds = (DataSource) new InitialContext().lookup(datasourceName);
                WrapperDataSource wds = (WrapperDataSource) ds;
                connection = (WrappedConnection) wds.getConnection();

                if (!connection.isXA())
                    throw new RuntimeException("Datasource " + datasourceName + " does not seem to be an XADataSource!");
            }

            return connection.getXAResource();

        } catch (Exception e) {
            log.warn("Cannot get an XAResource of " + datasourceName + " datasource", e);
            throw e;
        }
    }

    // Should not throw out any exception.
    @Override
    protected void closeXAResource() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {}
            connection = null;
        }
    }

}
