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
 * (C) 20012,
 * @author JBoss Inc.
 */
package org.jboss.as.test.jbossts.client;

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.exceptions.ObjectStoreException;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.tools.osb.api.proxy.RecoveryStoreProxy;
import com.arjuna.ats.arjuna.tools.osb.api.proxy.StoreManagerProxy;
import com.arjuna.ats.internal.arjuna.common.UidHelper;
import org.jboss.logging.Logger;

import javax.management.JMException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Utility class for interaction with transaction log.
 */
public class TransactionLog {
    private static Logger log = Logger.getLogger(TransactionLog.class);

    private RecoveryStoreProxy prs = null;

    private String serviceUrl;

    private TransactionLog() {}

    public TransactionLog(String serviceUrl) throws IOException, JMException {
        this.serviceUrl = serviceUrl;
        initProxies();
    }

    public void initProxies() throws IOException, JMException {
        if (prs != null)
            StoreManagerProxy.releaseProxy(serviceUrl);

        log.info("Looking up MBeans on url " + serviceUrl);

        prs = StoreManagerProxy.getRecoveryStore(serviceUrl, null);

        // this immediately checks whether we really got a store manager proxy or not
        String storeName = prs.getStoreName();
        log.debug("RecoveryStore name is " + storeName);
    }

    /**
     * Remove any committed objects from the storer
     *
     * @param objectType the type of objects that should be removed
     * @return the number of objects that were purged
     * @throws ObjectStoreException the store implementation was unable to remove a committed object
     */
    public int clearXids(String objectType) throws ObjectStoreException {
        Collection<Uid> uids = getIds(objectType);

        for (Uid uid : uids)
            prs.remove_committed(uid, objectType);

        return uids.size();
    }

    public Collection<Uid> getIds(String objectType) throws ObjectStoreException {
        return getIds(null, objectType);
    }

    /**
     * Get a list object ids for a given object type
     *
     * @param ids        holder for the returned uids
     * @param objectType The type of object to search in the store for
     * @return all objects of the given type
     * @throws ObjectStoreException the store implementation was unable retrieve all types of objects
     */
    public Collection<Uid> getIds(Collection<Uid> ids, String objectType) throws ObjectStoreException {
        if (ids == null)
            ids = new ArrayList<Uid>();


        InputObjectState types = new InputObjectState();

        if (prs.allTypes(types)) {
            String theName;

            try {
                boolean endOfList = false;

                while (!endOfList) {
                    theName = types.unpackString();

                    if (theName.compareTo("") == 0)
                        endOfList = true;
                    else {
                        if (objectType != null && !theName.matches(objectType))
                            continue;

                        InputObjectState uids = new InputObjectState();

                        if (prs.allObjUids(theName, uids)) {

                            Uid theUid;

                            try {
                                boolean endOfUids = false;

                                while (!endOfUids) {
                                    theUid = UidHelper.unpackFrom(uids);

                                    if (theUid.equals(Uid.nullUid()))
                                        endOfUids = true;
                                    else
                                        ids.add(theUid);
                                }
                            } catch (Exception e) {
                                // end of uids!
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn(e);

                // end of list!
            }
        }

        return ids;
    }

    public void close() throws JMException, IOException {
        if (prs != null) {
            StoreManagerProxy.releaseProxy(serviceUrl);
            prs = null;
        }
    }

}
