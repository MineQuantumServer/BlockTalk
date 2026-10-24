package dev.blocktalk.protocol;

import java.net.IDN;
import java.net.InetSocketAddress;

public record ServerAddress(String host,int port,boolean explicitPort) {
    public static ServerAddress parse(String input) {
        if(input==null || input.isBlank() || input.length()>300) throw new IllegalArgumentException("address");
        String s=input.trim(), host; int port=9987; boolean explicit=false;
        if(s.startsWith("[")) {
            int end=s.indexOf(']');
            if(end<2) throw new IllegalArgumentException("ipv6");
            host=s.substring(1,end);
            if(!host.contains(":") || !host.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("ipv6");
            if(end+1<s.length()) {
                if(s.charAt(end+1)!=':') throw new IllegalArgumentException("address");
                port=parsePort(s.substring(end+2)); explicit=true;
            }
        } else {
            int colon=s.indexOf(':');
            if(colon>=0) {
                if(colon!=s.lastIndexOf(':')) throw new IllegalArgumentException("ipv6_brackets");
                port=parsePort(s.substring(colon+1)); s=s.substring(0,colon); explicit=true;
            }
            host=IDN.toASCII(s);
            if(host.isBlank() || !host.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("host");
        }
        return new ServerAddress(host,port,explicit);
    }
    private static int parsePort(String s) {
        if(!s.matches("[0-9]{1,5}")) throw new IllegalArgumentException("port");
        int p=Integer.parseInt(s); if(p<1||p>65535) throw new IllegalArgumentException("port"); return p;
    }
    public InetSocketAddress resolve() { return new InetSocketAddress(host,port); }
    @Override public String toString() { return (host.contains(":")?"["+host+"]":host)+":"+port; }
}
