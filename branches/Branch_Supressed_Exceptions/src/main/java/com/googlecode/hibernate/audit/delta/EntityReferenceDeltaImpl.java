package com.googlecode.hibernate.audit.delta;

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
class EntityReferenceDeltaImpl extends MemberVariableDeltaSupport implements EntityReferenceDelta
{
    // Constants -----------------------------------------------------------------------------------

    // Static --------------------------------------------------------------------------------------

    // Attributes ----------------------------------------------------------------------------------

    private Serializable id;
    private String entityName;
    private Class entityClass;

    // Constructors --------------------------------------------------------------------------------

    EntityReferenceDeltaImpl(String name, Serializable id, String entityName, Class entityClass)
    {
        setName(name);
        this.id = id;
        this.entityName = entityName;
        this.entityClass = entityClass;
    }

    // ScalarDelta implementation ------------------------------------------------------------------

    public boolean isEntityReference()
    {
        return true;
    }

    public boolean isPrimitive()
    {
        return false;
    }

    public Serializable getId()
    {
        return id;
    }

    public String getEntityName()
    {
        return entityName;
    }

    public Class getEntityClass()
    {
        return entityClass;
    }

    @Override
    public String toString()
    {
        return getName() + "[" + entityName + "(" + id + ")]";
    }

    // Public --------------------------------------------------------------------------------------

    // MemberVariableDelta implementation ----------------------------------------------------------

    // Package protected ---------------------------------------------------------------------------

    // Protected -----------------------------------------------------------------------------------

    // Private -------------------------------------------------------------------------------------

    // Inner classes -------------------------------------------------------------------------------

}