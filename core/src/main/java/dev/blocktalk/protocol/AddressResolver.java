package dev.blocktalk.protocol;

import java.net.*;
import java.util.*;
import org.xbill.DNS.*;

public final class AddressResolver {
    private AddressResolver() {}
    public static InetSocketAddress resolve(ServerAddress a) throws Exception {
        if(!a.explicitPort()&&!a.host().contains(":")&&!a.host().matches("[0-9.]+")) {
            org.xbill.DNS.Record[] records=new Lookup("_ts3._udp."+a.host(),Type.SRV).run();
            if(records!=null) {
                List<SRVRecord> srv=Arrays.stream(records).filter(SRVRecord.class::isInstance).map(SRVRecord.class::cast).filter(r->!r.getTarget().equals(Name.root)).sorted(Comparator.comparingInt(SRVRecord::getPriority)).toList();
                if(!srv.isEmpty()) {
                    int priority=srv.getFirst().getPriority();var group=srv.stream().filter(r->r.getPriority()==priority).toList();
                    int sum=group.stream().mapToInt(SRVRecord::getWeight).sum();
                    int choice=sum==0?java.util.concurrent.ThreadLocalRandom.current().nextInt(group.size()):java.util.concurrent.ThreadLocalRandom.current().nextInt(sum);
                    SRVRecord chosen=group.getFirst();
                    for(int i=0;i<group.size();i++) {SRVRecord r=group.get(i);if(sum==0?i==choice:(choice-=r.getWeight())<0){chosen=r;break;}}
                    return new InetSocketAddress(chosen.getTarget().toString(),chosen.getPort());
                }
            }
        }
        return a.resolve();
    }
}
