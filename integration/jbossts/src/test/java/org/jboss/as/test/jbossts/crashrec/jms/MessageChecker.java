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

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.jboss.logging.Logger;


/**
 * Helper class for checking the recovery result on JMS side.
 * It checks whether the message has been sent or not, i.e. commit has really been done or not.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@Stateless
@Remote(MessageCheckerRemote.class)
public class MessageChecker implements MessageCheckerRemote {
    private static Logger log = Logger.getLogger(MessageChecker.class);

    public static final int RECEIVE_TIMEOUT = 5;    // in seconds


    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String checkMessageResult(String connectionFactoryJNDIName) {
        String receivedMessage = null;

        InitialContext ic = null;
        Connection connection = null;
        try {
            ic = new InitialContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) ic.lookup(connectionFactoryJNDIName);

            Queue crashRecoveryQueue = (Queue) ic.lookup(JMSCrashBean.TEST_QUEUE_JNDI_NAME);

            connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(crashRecoveryQueue);

            connection.start();

            log.info("waiting to receive a message from " + JMSCrashBean.TEST_QUEUE_JNDI_NAME + "...");
            TextMessage message = (TextMessage) consumer.receive(RECEIVE_TIMEOUT * 1000);

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
