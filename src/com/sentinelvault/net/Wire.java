package com.sentinelvault.net;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/** The messages that travel between the SentinelVault client and server. */
public final class Wire {
    private Wire() {}

    public static final int DEFAULT_PORT = 5050;

    /** One question from the client. The token proves who is asking (set after login). */
    public static final class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String op;
        public final String token;
        public final Object[] args;
        public Request(String op, String token, Object... args) {
            this.op = op;
            this.token = token;
            this.args = args;
        }
    }

    /** The server's answer: a value, or an error kind and message. */
    public static final class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        public final boolean ok;
        public final Object value;
        public final String errorKind;      // SECURITY, ARGUMENT, STATE or ERROR
        public final String errorMessage;
        private Response(boolean ok, Object value, String errorKind, String errorMessage) {
            this.ok = ok;
            this.value = value;
            this.errorKind = errorKind;
            this.errorMessage = errorMessage;
        }
        public static Response ok(Object value) { return new Response(true, value, null, null); }
        public static Response error(String kind, String message) { return new Response(false, null, kind, message); }
    }

    /**
     * Only our own classes and a few plain Java types may be read from the network.
     * This blocks the classic Java deserialization attack.
     */
    public static ObjectInputFilter filter() {
        return ObjectInputFilter.Config.createFilter(
                "maxdepth=20;maxrefs=200000;maxarray=70000000;"
                        + "com.sentinelvault.**;java.lang.*;java.util.*;!*");
    }
}
