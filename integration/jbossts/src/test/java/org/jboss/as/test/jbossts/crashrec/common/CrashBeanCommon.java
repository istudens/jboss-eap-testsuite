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
package org.jboss.as.test.jbossts.crashrec.common;

import org.jboss.as.test.jbossts.common.TestSynchronization;
import org.jboss.as.test.jbossts.common.TestXAResource;
import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.transaction.TransactionManager;

public abstract class CrashBeanCommon {
    private static Logger log = Logger.getLogger(CrashBeanCommon.class);

    @Resource(mappedName = "java:jboss/TransactionManager")
    protected TransactionManager transactionManager;

    protected void beforeEntityUpdate() {
        log.info("CrashBeanCommon.beforeEntityUpdate");
    }

    protected void afterEntityUpdate() {
        log.info("CrashBeanCommon.afterEntityUpdate");
    }

    protected void enlistSynchronization(int count) throws Throwable {
        log.info("CrashBeanCommon.enlistSynchronization");
        try {
            for (int i = 0; i < count; i++) {
                TestSynchronization testSynchronization = new TestSynchronization();
                transactionManager.getTransaction().registerSynchronization(testSynchronization);
            }
        } catch (Exception e) {
            log.error("Could not enlist TestSynchronization", e);
            throw new Throwable("Could not enlist TestSynchronization", e);
        }
    }

    protected void enlistXAResource(int count) throws Throwable {
        log.info("CrashBeanCommon.enlistXAResource");
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


}
