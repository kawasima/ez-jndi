package net.unit8.ezjndi;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.Iterator;
import java.util.Map;

public class ContextBindings implements NamingEnumeration<Binding> {
    private Map bindings = null;
    private Iterator iterator = null;

    public ContextBindings(Map table) {
        bindings = table;
        iterator = bindings.keySet().iterator();
    }
    @Override
    public Binding next() throws NamingException {
        if(bindings == null) {
            throw new NamingException();
        }
        return nextElement();
    }

    @Override
    public boolean hasMore() throws NamingException {
        if(bindings == null) {
            throw new NamingException();
        }
        return hasMoreElements();
    }

    @Override
    public boolean hasMoreElements() {
        return iterator.hasNext();
    }

    @Override
    public Binding nextElement() {
        if(bindings == null) {
            return null;
        }
        Object name = iterator.next();
        /* What comes out of the iterator should be a CompoundName */
        return new Binding(name.toString(), bindings.get(name));
    }

    @Override
    public void close() throws NamingException {
        bindings = null;
        iterator = null;
    }

}
