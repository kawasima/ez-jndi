package net.unit8.ezjndi;

import javax.naming.*;
import java.util.Enumeration;
import java.util.Properties;

public class JndiUtils {
    public static Properties toProperties(Reference ref) {
        Properties props = new Properties();
        Enumeration<RefAddr> allRefAddresses = ref.getAll();
        while (allRefAddresses.hasMoreElements()) {
            RefAddr refAddr = allRefAddresses.nextElement();
            props.setProperty(refAddr.getType(), (String) refAddr.getContent());
        }
        return props;
    }

    public static Reference toReference(Properties props, String className) {
        final String factory = props.getProperty("javaxNamingSpiObjectFactory", null);
        Reference ref = new Reference(className, factory, null);
        for (Object key : props.keySet()) {
            ref.add(new StringRefAddr((String) key, props.getProperty((String) key)));
        }
        return ref;
    }

    /**
     * {@link CompositeName} to {@link CompoundName} conversion. See issue #14.
     */
    static Name toCompoundName(Name objName, final Properties env) throws InvalidNameException
    {
        if (objName instanceof CompositeName) {
            return toCompoundName(objName.toString(), env);
        }
        return objName;
    }

    public static CompoundName toCompoundName(final String objName, final Properties env) throws InvalidNameException
    {
        Properties envCopy = new Properties(env);
        return new CompoundName(objName, envCopy);
    }
}
