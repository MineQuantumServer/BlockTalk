package dev.blocktalk.input;

/** The only authority for transmitting; token changes invalidate captured and encoded frames. */
public final class PttGate {
    private long epoch;
    private boolean held,armed,connected,input,codec,permission,focused,allowed,muted,deafened;
    private String reason="disconnected";
    public synchronized void context(boolean connected,boolean input,boolean codec,boolean permission,boolean focused,boolean allowed) {
        boolean was=eligible();
        this.connected=connected; this.input=input; this.codec=codec; this.permission=permission; this.focused=focused; this.allowed=allowed;
        if(was && !eligible()) reset();
        updateReason();
    }
    public synchronized void key(boolean down) {
        if(!down) { if(held || armed) epoch++; held=false; armed=false; }
        else if(!held) { held=true; armed=eligible(); epoch++; }
        updateReason();
    }
    public synchronized void reset() { epoch++; armed=false; updateReason(); }
    public synchronized void mute(boolean value) { if(muted!=value) { muted=value; reset(); } }
    public synchronized void deafen(boolean value) { if(deafened!=value) { deafened=value; reset(); } }
    private boolean eligible() { return connected&&input&&codec&&permission&&focused&&allowed&&!muted&&!deafened; }
    public synchronized boolean open() { return armed&&held&&eligible(); }
    public synchronized long token() { return open()?epoch:-1; }
    public synchronized boolean valid(long token) { return token>=0 && token==epoch && open(); }
    public synchronized boolean muted() { return muted; }
    public synchronized boolean deafened() { return deafened; }
    public synchronized String reason() { updateReason(); return reason; }
    private void updateReason() {
        reason=!connected?"disconnected":!input?"audio":!codec?"codec":!permission?"permission":deafened?"deafened":muted?"muted":!focused?"focus":!allowed?"editing":!armed?"press_again":!held?"ptt":"ready";
    }
}
