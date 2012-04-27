/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags.
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
 * (C) 2008,
 * @author JBoss Inc.
 */
package org.jboss.as.test.jbossts.crashrec.jms;

import org.jboss.as.test.jbossts.crashrec.common.CrashBeanCommon;
import org.jboss.logging.Logger;

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import java.lang.IllegalStateException;

@Stateless
@Remote(JMSCrashBeanRemote.class)
public class JMSCrashBean extends CrashBeanCommon implements JMSCrashBeanRemote {
    private static Logger log = Logger.getLogger(JMSCrashBean.class);

    public static final String TEST_QUEUE_NAME      = "queue/crashRecoveryQueue";
    public static final String TEST_QUEUE_JNDI_NAME = "java:jboss/" + TEST_QUEUE_NAME;


    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void testXA(String connectionFactoryJNDIName, String message) throws Throwable {
        log.info("testXA called with message=" + message + " and connectionFactoryJNDIName=" + connectionFactoryJNDIName);

        try {
            Transaction tx = transactionManager.getTransaction();
            if (tx == null || tx.getStatus() != Status.STATUS_ACTIVE) {
                log.error("Test method called without an active transaction!");
                throw new IllegalStateException("Test method called without an active transaction!");
            }
        } catch (SystemException e) {
            log.error("Cannot get the current transaction!", e);
            throw new Throwable("Cannot get the current transaction!", e);
        }

        beforeEntityUpdate();
        sendMessage(connectionFactoryJNDIName, message);
        afterEntityUpdate();
    }


    private void sendMessage(String connectionFactoryJNDIName, String message) throws NamingException, JMSException {
        InitialContext ic = null;
        Connection conn = null;
        try {
            ic = new InitialContext();

            ConnectionFactory connectionFactory = (ConnectionFactory) ic.lookup(connectionFactoryJNDIName);
            Queue testQueue = (Queue) ic.lookup(TEST_QUEUE_JNDI_NAME);

            conn = connectionFactory.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(testQueue);

            producer.send(session.createTextMessage(message));
            log.info("message sent");

        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) {
                }
            }
            if (ic != null) {
                try {
                    ic.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

}
