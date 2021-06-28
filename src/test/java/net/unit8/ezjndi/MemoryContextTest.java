package net.unit8.ezjndi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.*;

import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MemoryContextTest {
    @BeforeEach
    void setup() {
        System.setProperty("java.naming.factory.initial", EzJndiContextFactory.class.getName());
    }

    @Test
    void test() throws NamingException {
        InitialContext ic = new InitialContext();
        ic.bind("java:comp/env/HOGE", "hoge");
        assertThat(ic.lookup("java:comp/env/HOGE")).isEqualTo("hoge");
    }

    @Test
    void subContext() throws NamingException{
        InitialContext main = new InitialContext();
        ((Context) main.lookup("java:comp/env")).bind("HOGE", "hoge");
        assertThat(main.lookup(new CompositeName("java:comp/env/HOGE")))
                .isEqualTo("hoge");
        assertThat(main.lookup("java:comp/env/HOGE"))
                .isEqualTo("hoge");
    }

    @Test
    void enc() throws NamingException {
        InitialContext main = new InitialContext();
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        ClassLoader cl1 = new URLClassLoader(new URL[]{}, orig);
        ClassLoader cl2 = new URLClassLoader(new URL[]{}, orig);
        ((Context) main.lookup("java:comp/env")).bind("HOGE", "hoge_orig");

        Thread.currentThread().setContextClassLoader(cl1);
        ((Context) main.lookup("java:comp/env")).bind("HOGE", "hoge1");
        Thread.currentThread().setContextClassLoader(cl2);
        ((Context) main.lookup("java:comp/env")).bind("HOGE", "hoge2");
        Thread.currentThread().setContextClassLoader(cl1);
        assertThat(main.lookup("java:comp/env/HOGE"))
                .isEqualTo("hoge1");
        Thread.currentThread().setContextClassLoader(cl2);
        assertThat(main.lookup("java:comp/env/HOGE"))
                .isEqualTo("hoge2");
        Thread.currentThread().setContextClassLoader(orig);
        assertThat(main.lookup("java:comp/env/HOGE"))
                .isEqualTo("hoge_orig");
    }
}