/**
 * Copyright (C) 2009 Krasimir Chobantonov <kchobantonov@yahoo.com>
 * This file is part of Hibernate Audit.

 * Hibernate Audit is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at your
 * option) any later version.
 * 
 * Hibernate Audit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with Hibernate Audit.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.googlecode.hibernate.audit.test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.teneo.hibernate.HbDataStore;
import org.eclipse.emf.teneo.hibernate.HbDataStoreFactory;
import org.eclipse.emf.teneo.hibernate.HbHelper;
import org.eclipse.emf.teneo.hibernate.HbSessionDataStore;
import org.hibernate.event.PostCollectionRecreateEventListener;
import org.hibernate.event.PostDeleteEventListener;
import org.hibernate.event.PostInsertEventListener;
import org.hibernate.event.PostUpdateEventListener;
import org.hibernate.event.PreCollectionRecreateEventListener;
import org.hibernate.event.PreCollectionRemoveEventListener;
import org.hibernate.event.PreCollectionUpdateEventListener;

import com.googlecode.hibernate.audit.listener.AuditListener;
import com.googlecode.hibernate.audit.test.model1.Model1Package;

public abstract class AbstractHibernateAuditTest {
    protected final Logger LOG = Logger.getLogger(getClass());

    private static HbDataStoreFactory emfDataStoreFactory = new HbDataStoreFactory() {
        public HbDataStore createHbDataStore() {
            return new SessionFactory();
        }
    };

    protected final static HbDataStore dataStore = init();

    // init method
    private static HbDataStore init() {
        try {
            // Create the DataStore.
            final String dataStoreName = "AuditDataStore";
            HbHelper.setHbDataStoreFactory(emfDataStoreFactory);
            HbDataStore dataStore = HbHelper.INSTANCE.createRegisterDataStore(dataStoreName);

            // Configure the EPackages used by this DataStore.
            dataStore.setEPackages(new EPackage[] { Model1Package.eINSTANCE });

            // Initialize the DataStore. This sets up the Hibernate mapping and
            // creates the corresponding tables in the database.
            Properties prop = new Properties();
            prop.load(AbstractHibernateAuditTest.class.getResourceAsStream("/hibernate.properties"));
            dataStore.setHibernateProperties(prop);

            dataStore.initialize();

            return dataStore;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SessionFactory extends HbSessionDataStore {
        @Override
        protected void buildSessionFactory() {
            // programatically add the audit listener
            AuditListener auditListener = new AuditListener();

            // explicitly cast the auditListener so that the generic function
            // will have the correct type.
            getConfiguration().getEventListeners().setPostInsertEventListeners(
                    addListener(getConfiguration().getEventListeners().getPostInsertEventListeners(), (PostInsertEventListener) auditListener));
            getConfiguration().getEventListeners().setPostUpdateEventListeners(
                    addListener(getConfiguration().getEventListeners().getPostUpdateEventListeners(), (PostUpdateEventListener) auditListener));
            getConfiguration().getEventListeners().setPostDeleteEventListeners(
                    addListener(getConfiguration().getEventListeners().getPostDeleteEventListeners(), (PostDeleteEventListener) auditListener));

            getConfiguration().getEventListeners().setPreCollectionUpdateEventListeners(
                    addListener(getConfiguration().getEventListeners().getPreCollectionUpdateEventListeners(), (PreCollectionUpdateEventListener) auditListener));
            getConfiguration().getEventListeners().setPreCollectionRemoveEventListeners(
                    addListener(getConfiguration().getEventListeners().getPreCollectionRemoveEventListeners(), (PreCollectionRemoveEventListener) auditListener));
            getConfiguration().getEventListeners().setPostCollectionRecreateEventListeners(
                    addListener(getConfiguration().getEventListeners().getPostCollectionRecreateEventListeners(), (PostCollectionRecreateEventListener) auditListener));

            setSessionFactory(getConfiguration().buildSessionFactory());
        }

        private <T> T[] addListener(T[] listeners, T listener) {
            int length = listeners != null ? listeners.length + 1 : 1;
            T[] newListeners = (T[]) Array.newInstance(listener.getClass(), length);
            for (int i = 0; i < length; i++) {
                if (listeners != null && listeners.length > i) {
                    newListeners[i] = listeners[i];
                }
            }
            newListeners[length - 1] = listener;

            return newListeners;
        }
    }
}