package net.unit8.ezjndi;

import javax.naming.*;
import javax.naming.spi.NamingManager;
import java.util.*;

public class MemoryContext implements Cloneable, Context {
    private Properties envAsProperties;

    private Map<Name, Object> namesToObjects;
    private Map<Name, Context> subContexts = Collections.synchronizedMap(new HashMap<>());
    private Hashtable env = new Hashtable();
    private NameParser nameParser;
    /* The full name of this context. */
    private Name nameInNamespace = null;
    private boolean nameLock = false;


    public MemoryContext(Hashtable env) {
        this(env, null, null);
    }

    public MemoryContext(Hashtable env, Name nameInNamespace) {
        this(env, nameInNamespace, null);
    }

    protected MemoryContext(Hashtable env, Name nameInNamespace, NameParser parser) {
        if(env != null) {
            this.env = (Hashtable)env.clone();
            Properties props = new Properties();
            props.putAll(env);
            envAsProperties = props;
        }

        if(parser == null) {
            try {
                nameParser = new EzNameParser(this);
            } catch (NamingException e) {
                /*
                 * XXX: This should never really occur.  If it does, there is
                 * a severe problem.  I also don't want to throw the exception
                 * right now because that would break compatability, even
                 * though it is probably the right thing to do.  This might
                 * get upgraded to a fixme.
                 */
                e.printStackTrace();
            }
        }
        try {
            if (nameInNamespace == null) {
                this.nameInNamespace = nameParser.parse("");
            } else {
                this.nameInNamespace = nameInNamespace;
            }
        } catch (NamingException e) {
            /* This shouldn't be an issue at this point */
            e.printStackTrace();
        }
        final String name = this.nameInNamespace.toString();
        namesToObjects = new HashMap<>();
        if (envAsProperties != null) {
            String enc = (String) envAsProperties.get("net.unit8.ezjndi.enc");
            if (enc != null && name.startsWith(enc)) {
                namesToObjects = new ContextClassLoaderSeparatedMap<>(null);
            }
        }
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        if (name.size() == 0) {
            return newInstance();
        }
        else {
            Name objName = name.getPrefix(1);
            objName = JndiUtils.toCompoundName(objName, envAsProperties);
            if (name.size() > 1) { // A subcontext is lookuped.
                if (subContexts.containsKey(objName)) {
                    return subContexts.get(objName).lookup(name.getSuffix(1));
                }
                String msg = "MemoryContext#lookup(\"{}\"): Invalid subcontext '{}' in context '{}': {}";
                throw new NamingException(msg);
            }
            else { // Can be a subcontext or an object.
                name = JndiUtils.toCompoundName(name, envAsProperties);
                if (namesToObjects.containsKey(name)) {
                    Object o = namesToObjects.get(objName);
                    if (o instanceof Reference) {
                        Object instance;
                        try {
                            instance = NamingManager.getObjectInstance(o, null, null, getEnvironment());
                        }
                        catch (Exception e) {
                            NamingException namingException = new NamingException();
                            namingException.setRootCause(e);
                            throw namingException;
                        }
                        o = instance == o ? null : instance;
                        namesToObjects.put(objName, o);
                    }
                    return o;
                }
                if (subContexts.containsKey(name)) {
                    return subContexts.get(name);
                }
                throw new NameNotFoundException(name.toString());
            }
        }
    }

    private Object newInstance() throws OperationNotSupportedException {
        Context clone;
        try {
            clone = (Context) this.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new OperationNotSupportedException();
        }
        return clone;
    }

    @Override
    public Object lookup(String name) throws NamingException {
        return lookup(nameParser.parse(name));
    }

    @Override
    public void bind(Name name, Object object) throws NamingException {
        if(name.size() == 0) {
            throw new InvalidNameException("Cannot bind to an empty name.");
        }
        else if(name.size() > 1) {
            Name prefix = name.getPrefix(1);
            if(subContexts.containsKey(prefix)) {
                subContexts.get(prefix).bind(name.getSuffix(1), object);
            }
            else {
                throw new NameNotFoundException(prefix + "");
            }
        }
        else {
            /* Determine if the name is already bound */
            if(subContexts.containsKey(name)) {
                throw new NameAlreadyBoundException("Name " + name
                    + " already bound.  Use rebind() to override");
            }
            if (object instanceof Context) {
                subContexts.put(name, (Context) object);
            }
            else {
                namesToObjects.put(name, object);
            }
        }
    }

    @Override
    public void bind(String name, Object object) throws NamingException {
        bind(nameParser.parse(name), object);
    }

    @Override
    public void rebind(Name name, Object object) throws NamingException {
        if(name.isEmpty()) {
            throw new InvalidNameException("Cannot bind to empty name");
        }
        /* Look up the target context first. */
        Object targetContext = lookup(name.getPrefix(name.size() - 1));
        if(!(targetContext instanceof Context)) {
            throw new NamingException("Cannot bind object.  Target context does not exist.");
        }
        unbind(name);
        bind(name, object);
    }

    @Override
    public void rebind(String name, Object object) throws NamingException {
        rebind(nameParser.parse(name), object);
    }

    @Override
    public void unbind(Name name) throws NamingException {
        if(name.isEmpty()) {
            throw new InvalidNameException("Cannot unbind to empty name");
        }
        else if(name.size() == 1) {
            namesToObjects.remove(name);
            subContexts.remove(name);
        }
        else {
            Object targetContext = lookup(name.getPrefix(name.size() - 1));
            if(!(targetContext instanceof Context)) {
                NamingException e = new NamingException("Cannot unbind object.");
                throw e;
            }
            ((Context)targetContext).unbind(name.getSuffix(name.size() - 1));
        }
    }

    @Override
    public void unbind(String name) throws NamingException {
        unbind(nameParser.parse(name));
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        Object old = lookup(oldName);
        if(newName.isEmpty()) {
            throw new InvalidNameException("Cannot bind to empty name");
        }

        if(old == null) {
            throw new NamingException("Name '" + oldName + "' not found.");
        }

        /* If the new name is bound throw a NameAlreadyBoundException */
        if(lookup(newName) != null) {
            throw new NameAlreadyBoundException("Name '" + newName + "' already bound");
        }

        unbind(oldName);
        unbind(newName);
        bind(newName, old);
        /* If the object is a Thread, or a ThreadContext, give it the new name. */
        if(old instanceof Thread) {
            ((Thread)old).setName(newName.toString());
        }
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        rename(nameParser.parse(oldName), nameParser.parse(newName));
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        if(name == null || name.isEmpty()) {
            /* Because there are two mappings that need to be used here,
             * create a new mapping and add the two maps to it.  This also
             * adds the safety of cloning the two maps so the original is
             * unharmed. */
            Map enumStore = new HashMap();
            enumStore.putAll(namesToObjects);
            enumStore.putAll(subContexts);
            NamingEnumeration enumerator = new ContextNames(enumStore);
            return enumerator;
        }
        /* Look for a subcontext */
        Name subName = name.getPrefix(1);
        if(namesToObjects.containsKey(subName)) {
            /* Nope, actual object */
            throw new NotContextException(name + " cannot be listed");
        }
        if(subContexts.containsKey(subName)) {
            return subContexts.get(subName).list(name.getSuffix(1));
        }
        /* Couldn't find the subcontext and it wasn't pointing at us, throw
         * an exception. */
        /* IMPROVE: Give this a better message */
        throw new NamingException();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        return list(nameParser.parse(name));
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        if(name == null || name.isEmpty()) {
            /* Because there are two mappings that need to be used here,
             * create a new mapping and add the two maps to it.  This also
             * adds the safety of cloning the two maps so the original is
             * unharmed. */
            Map enumStore = new HashMap();
            enumStore.putAll(namesToObjects);
            enumStore.putAll(subContexts);
            return new ContextBindings(enumStore);
        }
        /* Look for a subcontext */
        Name subName = name.getPrefix(1);
        if(subContexts.containsKey(subName)) {
            return subContexts.get(subName).listBindings(name.getSuffix(1));
        }
        else {
        /* Couldn't find the subcontext and it wasn't pointing at us, throw an exception. */
            throw new NamingException("MemoryContext#listBindings(\"" + name + "\"): subcontext not found.");
        }
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        return listBindings(nameParser.parse(name));
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        if(name.size() > 1) {
            if(subContexts.containsKey(name.getPrefix(1))) {
                Context subContext = subContexts.get(name.getPrefix(1));
                destroySubcontexts(subContext);
                return;
            }
            /* IMPROVE: Better message might be necessary */
            throw new NameNotFoundException();
        }
        /* Look at the contextStore to see if the name is bound there */
        if(namesToObjects.containsKey(name)) {
            throw new NotContextException();
        }
        /* Look for the subcontext */
        if(!subContexts.containsKey(name)) {
            throw new NameNotFoundException();
        }
        Context subContext = subContexts.get(name);
        destroySubcontexts(subContext);
        subContext.close();
        subContexts.remove(name);
    }

    private void destroySubcontexts(Context context) throws NamingException {
        NamingEnumeration<Binding> bindings = context.listBindings("");
        while (bindings.hasMore()) {
            final Binding binding = bindings.next();
            // Context.listBindings() may only be called with subcontexts.
            String name = binding.getName();
            if (binding.getObject() instanceof Context) {
                Context subContext = (Context) binding.getObject();
                destroySubcontexts(subContext);
                context.destroySubcontext(name);
            }
            else {
                // Here name is always a single component name. To force handling as such:
                Properties syntax = new Properties();
                syntax.setProperty("jndi.syntax.direction", "flat");
                context.unbind(new CompoundName(name, syntax));
            }
        }
    }


    @Override
    public void destroySubcontext(String name) throws NamingException {
        destroySubcontext(nameParser.parse(name));
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        Context newContext;

        if(name.size() > 1) {
            if(subContexts.containsKey(name.getPrefix(1))) {
                Context subContext = subContexts.get(name.getPrefix(1));
                newContext = subContext.createSubcontext(name.getSuffix(1));
                return newContext;
            }
            else {
                throw new NameNotFoundException("The subcontext " + name.getPrefix(1) + " was not found (" + name + ").");
            }
        }
        try {
            lookup(name);
        }
        catch (NameNotFoundException ignore) { }

        Name contextName = getNameParser((Name)null).parse(getNameInNamespace());
        contextName.addAll(name);
        newContext = new MemoryContext(this.env, contextName);
        ((MemoryContext)newContext).setNameInNamespace(contextName);
        bind(name, newContext);
        return newContext;
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return createSubcontext(nameParser.parse(name));
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return lookup(nameParser.parse(name));
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        if(name == null ||
           name.isEmpty() ||
           (name.size() == 1 && name.toString().equals(getNameInNamespace()))) {
            return nameParser;
        }
        Name subName = name.getPrefix(1);
        if(subContexts.containsKey(subName)) {
            return subContexts.get(subName).getNameParser(name.getSuffix(1));
        }
        throw new NotContextException();
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return getNameParser(nameParser.parse(name));
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        if(name == null || prefix == null) {
            throw new NamingException("Arguments must not be null");
        }
        Name retName = (Name)prefix.clone();
        retName.addAll(name);
        return retName;
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        Name retName = composeName(nameParser.parse(name), nameParser.parse(prefix));
        /* toString pretty much is guaranteed to exist */
        return retName.toString();
    }

    @Override
    public Object addToEnvironment(String name, Object object) throws NamingException {
        if(this.env == null) {
            return null;
        }
        return this.env.put(name, object);
    }

    @Override
    public Object removeFromEnvironment(String name) throws NamingException {
        if(this.env == null) {
            return null;
        }
        return this.env.remove(name);
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
      if(this.env == null) {
            return new Hashtable();
        }
        return (Hashtable)this.env.clone();
    }

    @Override
    public void close() throws NamingException {
        forceClose();
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return nameInNamespace.toString();
    }

    public boolean isEmpty() {
        return (namesToObjects.size() > 0 || subContexts.size() > 0);
    }

    private void setNameInNamespace(Name name) throws NamingException {
        if(nameLock) {
            if(nameInNamespace != null || !nameInNamespace.isEmpty()) {
                throw new NamingException("Name already set.");
            }
        }
        nameInNamespace = name;
        nameLock = true;
    }

    public void forceClose() throws NamingException {
        destroySubcontexts(this);
        env = null;
        namesToObjects = null;
        subContexts = null;
    }
}
