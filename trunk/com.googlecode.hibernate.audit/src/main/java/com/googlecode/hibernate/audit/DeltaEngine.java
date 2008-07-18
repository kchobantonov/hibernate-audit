package com.googlecode.hibernate.audit;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.MappingException;
import org.hibernate.EntityMode;
import org.hibernate.Query;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.engine.SessionFactoryImplementor;
import org.apache.log4j.Logger;
import com.googlecode.hibernate.audit.model.AuditTransaction;
import com.googlecode.hibernate.audit.model.AuditType;
import com.googlecode.hibernate.audit.model.AuditEvent;
import com.googlecode.hibernate.audit.model.AuditEventType;
import com.googlecode.hibernate.audit.model.AuditEventPair;
import com.googlecode.hibernate.audit.util.Reflections;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.io.Serializable;

/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * Copyright 2008 Ovidiu Feodorov
 *
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class DeltaEngine
{
    // Constants -----------------------------------------------------------------------------------

    private static final Logger log = Logger.getLogger(DeltaEngine.class);

    // Static --------------------------------------------------------------------------------------

    /**
     * @param initialEntityState - an entity instance to which we want to apply the forward delta.
     *        It must contain a valid id.
     *
     * @throws MappingException - if the object passed as initial state is not a known entity.
     * @throws IllegalArgumentException - if such a transaction does not exist, doesn't have a valid
     *         id, etc.
     */
    public static void applyDelta(SessionFactoryImplementor sf,
                                  Object initialEntityState,
                                  Long transactionId)
        throws Exception
    {
        Session s = null;
        Transaction t = null;

        try
        {
            // determine the current events within the current transaction that directly
            // "touched" the object whose initial state is given

            // for that, first determine the entity's id
            Class c = initialEntityState.getClass();
            String className = c.getName();

            EntityPersister persister = sf.getEntityPersister(className);

            // TODO. For the time being we only suport pojos
            Object id = persister.getIdentifier(initialEntityState, EntityMode.POJO);

            if (id == null)
            {
                throw new IllegalArgumentException("initial state must have a non-null id");
            }

            s = sf.openSession();
            t = s.beginTransaction();

            AuditTransaction at = (AuditTransaction)s.get(AuditTransaction.class, transactionId);

            if (at == null)
            {
                throw new IllegalArgumentException("No audit transaction with id " +
                                                   transactionId + " exists");
            }

            // first query the type
            String qs = "from AuditType as a where a.className = :className";
            Query q = s.createQuery(qs);
            q.setString("className", className);

            AuditType atype = (AuditType)q.uniqueResult();

            if (atype == null)
            {
                throw new IllegalArgumentException(
                    "no audit trace found for an object of type " + className);
            }

            // get all events of that transaction
            qs = "from AuditEvent as a where a.transaction = :transaction order by a.id";
            q = s.createQuery(qs);
            q.setParameter("transaction", at);

            List events = q.list();

            if (events.isEmpty())
            {
                throw new IllegalArgumentException(
                    "no audit events found for " + initialEntityState +
                    " in transaction " + transactionId);
            }

            // "apply" events

            Set<EntityExpectation> entityLoadingRow = new HashSet<EntityExpectation>();

            for(Object o: events)
            {
                AuditEvent ae = (AuditEvent)o;

                if (!AuditEventType.INSERT.equals(ae.getType()))
                {
                    throw new RuntimeException("NOT YET IMPLEMENTED");
                }

                Long tId = ae.getTargetId();
                AuditType tt = HibernateAudit.enhance(sf, ae.getTargetType());

                if (!tt.isEntityType())
                {
                    throw new RuntimeException("NOT YET IMPLEMENTED");
                }

                // we're sure it's an entity, so add it to the loading row
                EntityExpectation e = new EntityExpectation(sf, tt.getClassInstance(), tId);
                Object detachedEntity = e.getDetachedInstance();
                entityLoadingRow.add(e);

                // insert all pairs of this event into this entity

                q = s.createQuery("from AuditEventPair as p where p.event = :event order by p.id");
                q.setParameter("event", ae);

                List pairs = q.list();

                for(Object o2: pairs)
                {
                    AuditEventPair p = (AuditEventPair)o2;
                    String name = p.getField().getName();
                    AuditType type = HibernateAudit.enhance(sf, p.getField().getType());

                    Object value = null;

                    if (type.isEntityType())
                    {
                        Serializable entityId = type.stringToValue(p.getStringValue());
                        Class entityClass = type.getClassInstance();

                        // the audit framework persisted persisted only the id of this entity,
                        // but we need the entire state, so we check if we find this entity on the
                        // list of those we need state for; if it's there, fine, use it, if not
                        // register it on the list, hopefully the state will come later in a
                        // different event
                        EntityExpectation ee = new EntityExpectation(entityClass, entityId);

                        boolean expectationExists = false;
                        for(EntityExpectation seen : entityLoadingRow)
                        {
                            if (seen.equals(ee))
                            {
                                expectationExists = true;
                                value = seen.getDetachedInstance();
                                break;
                            }
                        }

                        if (!expectationExists)
                        {
                            ee.initializeDetachedInstance(sf);
                            entityLoadingRow.add(ee);
                        }
                    }
                    else if (type.isCollectionType())
                    {
                        throw new RuntimeException("NOT YET IMPLEMENTED");
                    }
                    else
                    {
                        // primitive
                        value = type.stringToValue(p.getStringValue());
                    }

                    Reflections.mutate(detachedEntity, name, value);
                }
            }

            t.commit();

            Object transactionDelta = null;
            for(EntityExpectation e: entityLoadingRow)
            {
                if (e.getId().equals(id) && e.getClassInstance().equals(c))
                {
                    transactionDelta = e.getDetachedInstance();
                }
            }

            if (transactionDelta == null)
            {
                throw new IllegalArgumentException(
                    "no audit trace for " + c.getName() + "[" + id + "]" );
            }

            Reflections.applyDelta(initialEntityState, transactionDelta);
            entityLoadingRow.clear();
        }
        catch(Exception e)
        {
            if (t != null)
            {
                try
                {
                    t.rollback();
                }
                catch(Exception e2)
                {
                    log.error("failed to rollback Hibernate transaction", e2);
                }
            }

            if (s != null)
            {
                try
                {
                    s.close();
                }
                catch(Exception e2)
                {
                    log.error("failed to close Hibernate session", e2);
                }
            }

            throw e;
        }
    }

    // Attributes ----------------------------------------------------------------------------------

    // Constructors --------------------------------------------------------------------------------

    // Public --------------------------------------------------------------------------------------

    // Package protected ---------------------------------------------------------------------------

    // Protected -----------------------------------------------------------------------------------

    // Private -------------------------------------------------------------------------------------

    // Inner classes -------------------------------------------------------------------------------
}