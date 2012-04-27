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

import org.jboss.logging.Logger;

import javax.ejb.*;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;


/**
 * Helper SLSB for playing (initiate, update, ...) with the test entity.
 *
 * @author <a href="istudens@redhat.com">Ivo Studensky</a>
 */
@Stateless
@Remote(TestEntityHelperRemote.class)
@Local(TestEntityHelperLocal.class)
public class TestEntityHelper implements TestEntityHelperRemote, TestEntityHelperLocal {
    private static Logger log = Logger.getLogger(TestEntityHelper.class);

    @PersistenceContext
    EntityManager em;

    /**
     * Initiates the test entity with <code>entityPK</code> key with the value <code>initValue</code>.
     *
     * @param entityPK primary key of the test entity
     * @return initiated test entity instance
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public TestEntity initTestEntity(String entityPK, int initValue) {
        TestEntity entity = getTestEntity(entityPK);

        if (entity == null) {
            entity = new TestEntity(entityPK, initValue);
            em.persist(entity);
        } else {
            entity.setA(initValue);
        }

        return entity;
    }

    /**
     * Finds the test entity with <code>entityPK</code> key.
     *
     * @param entityPK primary key of the test entity
     * @return test entity instance
     */
    public TestEntity getTestEntity(String entityPK) {
        return em.find(TestEntity.class, entityPK);
    }

    /**
     * Updates the test entity, i.e. increments its value by 1.
     *
     * @param entityPK primary key of the test entity
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void updateTestEntity(String entityPK) {
        TestEntity entity = getTestEntity(entityPK);
        entity.setA(entity.getA() + 1);
    }

}
