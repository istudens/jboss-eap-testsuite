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
package org.jboss.as.test.jbossts.common;

import com.arjuna.ats.arjuna.tools.osb.api.mbeans.RecoveryStoreBean;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Instantiates RecoveryStoreBean on JMX.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
@Singleton
@Startup
public class TransactionStoreBrowserService {
    protected static final Logger log = Logger.getLogger(TransactionStoreBrowserService.class);

    private RecoveryStoreBean rsb;

    @PostConstruct
    public void start() {
        log.info("TransactionStoreBrowserService.start");
        rsb = new RecoveryStoreBean();
        rsb.start();
    }

    @PreDestroy
    public void stop() {
        log.info("TransactionStoreBrowserService.stop");
        if (rsb != null)
            rsb.stop();
    }

}
