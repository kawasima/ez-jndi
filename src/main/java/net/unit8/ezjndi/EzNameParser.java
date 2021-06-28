package net.unit8.ezjndi;

import javax.naming.*;
import java.util.Properties;

public class EzNameParser implements NameParser {
    private Context parent = null;
    private Properties props = new Properties();

    public EzNameParser(Context parent) throws NamingException {
        this.parent = parent;
        props.putAll(this.parent.getEnvironment());
    }


    @Override
    public Name parse(String name) throws NamingException {
        if(name == null) {
            name = "";
        }
        return new CompoundName(name, props);
    }
}
