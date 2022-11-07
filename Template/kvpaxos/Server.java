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

    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);
        // Your initialization code here

        client_map = new HashMap<String, Integer>();


        try{
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }


    // RMI handlers
    public Response Get(Request req){
        Op kv_obj = req.kv_obj;
        int client_seq_seen = client_map.getOrDefault(kv_obj.client_id, -1);

        if (kv_obj.ClientSeq <= client_seq_seen) {
            System.out.println("Already seen this request!");
            return null;
        }

        client_map.put(kv_obj.client_id, kv_obj.ClientSeq);

        int seq = this.px.Max() + 1;
        while (true) {
            this.px.Start(seq, kv_obj);
    
            wait(seq); 
    
            Paxos.retStatus ret = this.px.Status(seq);
            
            // TEST
            if (kv_obj.equals(Op.class.cast(ret.v))) 
                break;
            
            seq = this.px.Max() + 1;
        }

        for (int i = seq-1; i >= 0; i--) {
            Paxos.retStatus ret = this.px.Status(i);
            Op res = Op.class.cast(ret.v);

            if (res.op.equals("Put") && res.key.equals(kv_obj.key)) {
                return new Response(res);
            }
        }

        return null;

    }

    public Response Put(Request req){
        // Your code here
        Op kv_obj = req.kv_obj;
        int client_seq_seen = client_map.getOrDefault(kv_obj.client_id, -1);

        if (kv_obj.ClientSeq <= client_seq_seen) {
            System.out.println("Already seen this request!");
            return null;
        }

        client_map.put(kv_obj.client_id, kv_obj.ClientSeq);

        while (true) {
            int seq = this.px.Max() + 1;
            this.px.Start(seq, kv_obj);
    
            wait(seq); 
    
            Paxos.retStatus ret = this.px.Status(seq);
            
            // TEST
            if (kv_obj.equals(Op.class.cast(ret.v))) 
                return new Response();
        }
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
