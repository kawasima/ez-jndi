package net.unit8.ezjndi;

import javax.naming.Binding;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.Iterator;
import java.util.Map;

public class ContextNames implements NamingEnumeration<NameClassPair> {
    private Map bindings = null;
    private Iterator iterator = null;

    public ContextNames(Map table) {
        bindings = table;
        iterator = bindings.keySet().iterator();
    }

    @Override
    public NameClassPair nextElement() {
        if(bindings == null) {
            return null;
        }
        Object name = iterator.next();
        /* What comes out of the iterator should be a CompoundName */
        return new NameClassPair(name.toString(), bindings.get(name).getClass().getName());
    }

    @Override
    public NameClassPair next() throws NamingException {
        if(bindings == null) {
            return null;
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
    public void close() throws NamingException {
        bindings = null;
        iterator = null;
    }
}
