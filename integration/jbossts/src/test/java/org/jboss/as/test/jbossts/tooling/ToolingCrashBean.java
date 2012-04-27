/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
 * (C) 2012,
 * @author JBoss Inc.
 */
package org.jboss.as.test.jbossts.tooling;

import org.jboss.as.test.jbossts.common.TestXAResource;
import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.lang.IllegalStateException;


@Stateless
@Remote(ToolingCrashBeanRemote.class)
public class ToolingCrashBean implements ToolingCrashBeanRemote {
    private static Logger log = Logger.getLogger(ToolingCrashBean.class);

    public static final String TEST_QUEUE_NAME      = "queue/toolingTestQueue";
    public static final String TEST_QUEUE_JNDI_NAME = "java:jboss/" + TEST_QUEUE_NAME;

    private static final String CONNECTION_FACTORY_JNDI_NAME = "java:/JmsXA";

    @Resource(mappedName = "java:jboss/TransactionManager")
    protected TransactionManager transactionManager;


    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void testXA() throws Throwable {
        log.info("ToolingCrashBean.testXA");

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

        before();
        sendMessage();
        after();
    }

    protected void before() {
        log.info("ToolingCrashBean.before");
    }

    protected void after() {
        log.info("ToolingCrashBean.after");
    }

    protected void enlistXAResource(int count) throws Throwable {
        log.info("ToolingCrashBean.enlistXAResource");
        try {
            for (int i = 0; i < count; i++) {
                TestXAResource testXAResource = new TestXAResource();
                transactionManager.getTransaction().enlistResource(testXAResource);
            }
        } catch (Exception e) {
            log.error("Could not enlist TestXAResource", e);
            throw new Throwable("Could not enlist TestXAResource", e);
        }
    }

    private void sendMessage() throws NamingException, JMSException {
        InitialContext ic = null;
        Connection conn = null;
        try {
            ic = new InitialContext();

            ConnectionFactory connectionFactory = (ConnectionFactory) ic.lookup(CONNECTION_FACTORY_JNDI_NAME);
            Queue testQueue = (Queue) ic.lookup(TEST_QUEUE_JNDI_NAME);

            conn = connectionFactory.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(testQueue);

            producer.send(session.createTextMessage("committed"));
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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String checkMessageResult() {
        String receivedMessage = null;

        InitialContext ic = null;
        Connection connection = null;
        try {
            ic = new InitialContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) ic.lookup(CONNECTION_FACTORY_JNDI_NAME);

            Queue crashRecoveryQueue = (Queue) ic.lookup(TEST_QUEUE_JNDI_NAME);

            connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(crashRecoveryQueue);

            connection.start();

            log.info("waiting to receive a message from " + TEST_QUEUE_JNDI_NAME + "...");
            TextMessage message = (TextMessage) consumer.receive(5 * 1000);     // 5 secs

            if (message != null) {
                receivedMessage = message.getText();
                log.debug("received message: " + receivedMessage);
            }
        } catch (Exception e) {
            log.warn("Error in receiving a message:", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }
            if (ic != null) {
                try {
                    ic.close();
                } catch (Exception ignored) {}
            }
        }

        return receivedMessage;
    }


}
