package kvpaxos;
import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;


public class Server implements KVPaxosRMI {

    ReentrantLock mutex;
    Registry registry;
    Paxos px;
    int me;

    String[] servers;
    int[] ports;
    KVPaxosRMI stub;

    // Your definitions here

    HashMap<String, Integer> client_map;
    int local_max_seq;
    HashMap<String, Integer> KeyValMap;
    
    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);
        // Your initialization code here
        
        client_map = new HashMap<String, Integer>();
        local_max_seq = -1;
        KeyValMap = new HashMap<String, Integer>();
        
        try{
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void updateDict() {
        mutex.lock();
        int px_max = this.px.Max();
        mutex.unlock();

        for (int i = local_max_seq + 1; i <= px_max; i++) {
            Paxos.retStatus ret = this.px.Status(i);
            addSingleValue(ret);
        }
        local_max_seq = px_max;
        this.px.Done(local_max_seq);
    }

    public void addSingleValue(Paxos.retStatus ret) {
        Op kv_obj = Op.class.cast(ret.v);
        mutex.lock();
        if (kv_obj.op.equals("Put")) {
            KeyValMap.put(kv_obj.key, kv_obj.value);
            int client_seq_old = client_map.getOrDefault(kv_obj.client_id, -1);
            client_map.put(kv_obj.client_id, Math.max(client_seq_old, kv_obj.ClientSeq));
        }
        mutex.unlock();
    }

    // RMI handlers
    public Response Get(Request req){
        updateDict();

        Op kv_obj = req.kv_obj;
        int client_seq_seen = client_map.getOrDefault(kv_obj.client_id, -1);

        if (kv_obj.ClientSeq <= client_seq_seen) {
            System.out.println("Already seen this request!");
            return null;
        }

        client_map.put(kv_obj.client_id, kv_obj.ClientSeq);

        int seq = local_max_seq + 1;

        while (true) {
            this.px.Start(seq, kv_obj);
    
            wait(seq); 
    
            Paxos.retStatus ret = this.px.Status(seq);
            
            addSingleValue(ret);

            if (kv_obj.equals(Op.class.cast(ret.v))) 
                break;  
            
            seq++;
        }

        local_max_seq = seq;
        this.px.Done(local_max_seq);
        
        return new Response(KeyValMap.getOrDefault(kv_obj.key, null));
    }

    public Response Put(Request req){
        updateDict();

        Op kv_obj = req.kv_obj;
        int client_seq_seen = client_map.getOrDefault(kv_obj.client_id, -1);

        if (kv_obj.ClientSeq <= client_seq_seen) {
            System.out.println("Already seen this request!");
            return null;
        }

        client_map.put(kv_obj.client_id, kv_obj.ClientSeq);

        int seq = local_max_seq + 1;

        while (true) {
            this.px.Start(seq, kv_obj);
    
            wait(seq); 
    
            Paxos.retStatus ret = this.px.Status(seq);

            addSingleValue(ret);

            if (kv_obj.equals(Op.class.cast(ret.v)))
                break;

            seq++;
        }

        local_max_seq = seq;
        this.px.Done(local_max_seq);

        return new Response();
    }

    public Op wait(int seq) {
        int to = 10;
        while(true) {
            Paxos.retStatus ret = this.px.Status(seq);

            if (ret.state == State.Decided) {
                return Op.class.cast(ret.v);
            }

            try {
                Thread.sleep(to);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (to < 1000) {
                to *= 2;
            }
        }
    }
}
