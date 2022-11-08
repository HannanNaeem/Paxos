package kvpaxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.UUID;


public class Client {
    String[] servers;
    int[] ports;

    // Your data here

    int client_seq;
    String me;

    public Client(String[] servers, int[] ports){
        this.servers = servers;
        this.ports = ports;
        // Your initialization code here
        
        this.me = UUID.randomUUID().toString();
        this.client_seq = 0;
    }

    /**
     * Call() sends an RMI to the RMI handler on server with
     * arguments rmi name, request message, and server id. It
     * waits for the reply and return a response message if
     * the server responded, and return null if Call() was not
     * be able to contact the server.
     *
     * You should assume that Call() will time out and return
     * null after a while if it doesn't get a reply from the server.
     *
     * Please use Call() to send all RMIs and please don't change
     * this function.
     */
    public Response Call(String rmi, Request req, int id){
        Response callReply = null;
        KVPaxosRMI stub;
        try{
            Registry registry= LocateRegistry.getRegistry(this.ports[id]);
            stub=(KVPaxosRMI) registry.lookup("KVPaxos");
            if(rmi.equals("Get"))
                callReply = stub.Get(req);
            else if(rmi.equals("Put")){
                callReply = stub.Put(req);}
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }

    // RMI handlers
    public Integer Get(String key){
        Op get_op = new Op("Get", client_seq++, key, -1, this.me);

        boolean done = false;
        for (int i = 0; i < this.servers.length && !done; i++) {
            Response res = Call("Get", new Request(get_op), i);

            if (res == null) continue;

            return res.value;
        }

        return null;
    }

    public boolean Put(String key, Integer value){
        // Your code here

        Op put_op = new Op("Put", client_seq++, key, value, this.me);

        boolean done = false;
        for (int i = 0; i < this.servers.length && !done; i++) {
            Response res = Call("Put", new Request(put_op), i);

            if (res == null) continue;

            done = true;
        }

        return done;
        
    }

}
