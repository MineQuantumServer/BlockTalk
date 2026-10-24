package dev.blocktalk.config;

import dev.blocktalk.model.Models.Server;
import java.util.*;

public final class Settings {
    public int schemaVersion=2;
    public String nickname="BlockTalk", inputDevice="default", outputDevice="default";
    public float inputGain=1, outputGain=0.8f, hudScale=1, hudOpacity=0.65f;
    public int jitterFrames=3, hudX=8, hudY=8, hudUsers=5;
    public boolean guiPtt=false, keepConnection=false, reconnect=true, hud=true;
    public List<Server> favorites=new ArrayList<>();
    public Map<String,Float> userVolumes=new HashMap<>();
    public void validate() {
        if(schemaVersion<1 || schemaVersion>2) throw new IllegalArgumentException("schema");
        schemaVersion=2;
        nickname=dev.blocktalk.model.Models.clean(nickname,30); if(nickname.isBlank()) nickname="BlockTalk";
        if(inputDevice==null) inputDevice="default"; if(outputDevice==null) outputDevice="default";
        inputGain=clamp(inputGain,0,3,1); outputGain=clamp(outputGain,0,2,0.8f);
        hudScale=clamp(hudScale,0.5f,2,1); hudOpacity=clamp(hudOpacity,0,1,0.65f);
        jitterFrames=Math.clamp(jitterFrames,1,8); hudUsers=Math.clamp(hudUsers,1,12);
        hudX=Math.clamp(hudX,0,10000); hudY=Math.clamp(hudY,0,10000);
        if(favorites==null) favorites=new ArrayList<>();
        favorites=new ArrayList<>(favorites.stream().filter(Objects::nonNull).limit(100).toList());
        if(userVolumes==null) userVolumes=new HashMap<>();
        userVolumes.entrySet().removeIf(e->e.getKey()==null||e.getKey().length()>200||e.getValue()==null||!Float.isFinite(e.getValue()));
        userVolumes.replaceAll((k,v)->clamp(v,0,2,1));
    }
    private static float clamp(float v,float min,float max,float def) { return Float.isFinite(v)?Math.clamp(v,min,max):def; }
}
