package net.unit8.ezjndi;

import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

public class EzJndiContextFactory implements InitialContextFactory {
    private static final ConcurrentHashMap<String, Context> contextsByRoot =
            new ConcurrentHashMap<String, Context>();

    @Override
    public Context getInitialContext(Hashtable environment) throws NamingException {
        environment.put("jndi.syntax.direction", "left_to_right");
        environment.put("jndi.syntax.separator", "/");
        environment.put("net.unit8.ezjndi.enc", "java:comp/env");
        String root = "";
        Context ctx = contextsByRoot.get(root);
        if (ctx != null) {
            return ctx;
        } else {
            final String finalRoot = root;
            MemoryContext context = new MemoryContext(environment) {
                private boolean isClosed;
                @Override
                public void close() throws NamingException {
                    // When already closed getEnvironment() throws an Exception.
                    if (!isClosed) {
                        contextsByRoot.remove(finalRoot);
                        super.forceClose();
                        isClosed = true;
                    }
                }
            };

            // ENC
            String encName = (String) environment.get("net.unit8.ezjndi.enc");
            if (encName != null) {
                CompositeName compName = new CompositeName(encName);
                Enumeration<String> token = compName.getAll();
                Context c = context;
                while(token.hasMoreElements()) {
                    c = c.createSubcontext(token.nextElement());
                }
            }
            contextsByRoot.put(root, context);
            return context;
        }
    }
}
