package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import java.util.HashMap;

/**
 * This class is the main class you need to implement paxos instances.
 */

class AcceptData {
    Object value;
    int initiator;
    int prop;

    AcceptData(Object value, int initiator, int prop) {
        this.value = value;
        this.initiator = initiator;
        this.prop = prop;
    }

    public String toString() {
        return "*AcceptData* Val: " + this.value + " initiator: " + this.initiator + " Prop: " + this.prop;
    }
}

class PrepareData {
    int initiator;
    int prop;

    PrepareData(int initiator, int prop) {
        this.initiator = initiator;
        this.prop = prop;
    }


    public String toString() {
        return "*PrepareData*" +  " initiator: " + this.initiator + " Prop: " + this.prop;
    }
}


public class Paxos implements PaxosRMI, Runnable {

    ReentrantLock mutex;
    String[] peers; // hostname
    int[] ports; // host port
    int me; // index into peers[]

    Registry registry;
    PaxosRMI stub;

    AtomicBoolean dead;// for testing
    AtomicBoolean unreliable;// for testing

    // Your data here

    HashMap<Integer, PrepareData> SequencePrepMap;
    HashMap<Integer, AcceptData> SequenceAcceptMap;
    HashMap<Integer, retStatus> DecidedValMap;

    int seq;
    Object value;

    int done_val;
    int[] peer_min;
    int min_forg;

    int max_seq_seen;

    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */

     public HashMap<Integer, retStatus> getD() {
        return DecidedValMap;
     }
    public Paxos(int me, String[] peers, int[] ports){

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here

        SequencePrepMap = new HashMap<Integer, PrepareData>(); 
        SequenceAcceptMap = new HashMap<Integer, AcceptData>(); 
        DecidedValMap = new HashMap<Integer, retStatus>();

        this.min_forg = -1;

        this.max_seq_seen = -1;

        this.peer_min = new int[peers.length];

        for (int i = 0; i < this.peer_min.length; i++) {
            this.peer_min[i] = -1;
        }

        // register peers, do not modify this part
        try{
            System.setProperty("java.rmi.server.hostname", this.peers[this.me]);
            registry = LocateRegistry.createRegistry(this.ports[this.me]);
            stub = (PaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("Paxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
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

        PaxosRMI stub;
        try{
            Registry registry=LocateRegistry.getRegistry(this.ports[id]);
            stub=(PaxosRMI) registry.lookup("Paxos");
            if(rmi.equals("Prepare"))
                callReply = stub.Prepare(req);
            else if(rmi.equals("Accept"))
                callReply = stub.Accept(req);
            else if(rmi.equals("Decide"))
                callReply = stub.Decide(req);
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }


    /**
     * The application wants Paxos to start agreement on instance seq,
     * with proposed value v. Start() should start a new thread to run
     * Paxos on instance seq. Multiple instances can be run concurrently.
     *
     * Hint: You may start a thread using the runnable interface of
     * Paxos object. One Paxos object may have multiple instances, each
     * instance corresponds to one proposed value/command. Java does not
     * support passing arguments to a thread, so you may reset seq and v
     * in Paxos object before starting a new thread. There is one issue
     * that variable may change before the new thread actually reads it.
     * Test won't fail in this case.
     *
     * Start() just starts a new thread to initialize the agreement.
     * The application will call Status() to find out if/when agreement
     * is reached.
     */
    public void Start(int seq, Object value){
        // Your code here
        this.seq = seq;
        this.value = value;
        Thread pt = new Thread(this);
        pt.start();
    }

    public int get_proposal_value(int seq_cp) {
        mutex.lock();
        PrepareData sp = SequencePrepMap.getOrDefault(seq_cp, null);

        if (sp == null) {
            sp = new PrepareData(this.me, -1);
            SequencePrepMap.put(seq_cp, sp);
        }

        int prop_number = sp.prop + 1;
        mutex.unlock();

        return prop_number;
    }


    public boolean is_decided(int seq_cp) {
        mutex.lock();
        retStatus decidedStatus = DecidedValMap.getOrDefault(seq_cp, null);
        mutex.unlock();
        return decidedStatus != null && decidedStatus.state == State.Decided;
    }


    @Override
    public void run(){
        //Your code here
        mutex.lock();
        int seq_cp = this.seq;
        Object val_cp = this.value;
        mutex.unlock();

        while (!is_decided(seq_cp)) {
            
            // PROPOSE
            int prop_number = get_proposal_value(seq_cp);
            int majority = 0;
            int highest_prop = -1;
            Object highest_value = val_cp;

            AcceptData highest_seen = new AcceptData(highest_value, this.me, highest_prop);

            for (int i = 0; i < this.peers.length; i++) {

                Response res = Call("Prepare", new Request(seq_cp, prop_number, this.me, val_cp, this.peer_min[this.me]), i);

                if (res == null) continue;

                updateForgettable(res.min_done, i);
                
                if (res.accept == false) {
                    SequencePrepMap.put(seq_cp, new PrepareData(res.initiator, res.prop));
                    continue;
                } else {
                    if (res.max_a_val == null) {
                    } else {
                        if (res.max_a_prop > highest_seen.prop) {
                            highest_seen.initiator = res.initiator;
                            highest_seen.value = res.max_a_val;
                            highest_seen.prop = res.max_a_prop;
                        }
                    }
                }
                majority++;
            }

            highest_seen.prop = highest_seen.prop == -1 ? prop_number : highest_seen.prop;

            if (highest_seen.prop > prop_number) {
                PrepareData s = SequencePrepMap.put(seq_cp, new PrepareData(highest_seen.initiator, highest_seen.prop));
            }


            if (majority <= (this.peers.length/2)) {
                continue;
            }


            // ACCEPT
            majority = 0;

            for (int i = 0; i < this.peers.length; i++) {
                Response res = Call("Accept", new Request(seq_cp, prop_number, this.me, highest_seen.value, this.peer_min[this.me]), i);
                
                if (res == null) continue;

                updateForgettable(res.min_done, i);

                if (!res.accept) {
                    SequencePrepMap.put(seq_cp, new PrepareData(res.initiator, res.prop));
                    continue;
                }

                // System.out.println("Initiator: " + this.me + " PROP no: " + prop_number + " Acceptance from: " + i + " for SEQ: " + seq_cp + " for val: " + temp_max.value);
                majority++;
            }

            if (majority <= (this.peers.length/2)) {
                continue;
            }

            majority = 0;
            // System.out.println("SEQ: " + seq_cp + " VAL: " + val_cp + " ME: " + this.me + " temp_max: " + temp_max.value);
            // System.out.println(this.me + " DECIDED on val: " + highest_seen.value + " seq: " + seq_cp + " LEN: " + this.peers.length);
            for (int i = 0; i < this.peers.length; i++) {
                if (i == this.me) { 
                    Decide(new Request(seq_cp, prop_number, this.me, highest_seen.value, this.peer_min[this.me]));
                }
                Response res = Call("Decide", new Request(seq_cp, prop_number, this.me, highest_seen.value, this.peer_min[this.me]), i);

                if (res == null) continue;

                updateForgettable(res.min_done, i);
                majority++;
            }
        }

        try {
            Thread.sleep((long)((Math.random() * 300) + 50));
        } catch (Exception e) {}

    }

    public void updateForgettable(int min_done, int peer) {
        mutex.lock();
        if (this.peer_min[peer] == Math.max(peer_min[peer], min_done)) {
            mutex.unlock();
            return;
        }

        this.peer_min[peer] = Math.max(this.peer_min[peer], min_done);

        this.peer_min[this.me] = Math.max(this.peer_min[peer], this.peer_min[this.me]);
        
        int cur_min = this.peer_min[0];
        for (int i = 0; i < this.peer_min.length; i++){
            // find -1
            if (this.peer_min[i] == -1) {
                mutex.unlock();
                return;
            }
            cur_min = Math.min(cur_min, this.peer_min[i]);
        }
        
        if (cur_min == this.Min()) {
            mutex.unlock();
            return;
        }

        this.min_forg = cur_min - 1;
        mutex.unlock();
        // TODO: Forget instances;
    }

    // RMI handler
    public Response Prepare(Request req){
        // your code here

        updateForgettable(req.min_done, req.initiator);
        mutex.lock();

        this.max_seq_seen = Math.max(this.max_seq_seen, req.seq);

        PrepareData prep_max = SequencePrepMap.getOrDefault(req.seq, null);

        if (prep_max == null || (req.prop > prep_max.prop) || (req.prop == prep_max.prop && req.initiator > prep_max.initiator)) {
            AcceptData accept_max = SequenceAcceptMap.getOrDefault(req.seq, null);

            SequencePrepMap.put(req.seq, new PrepareData(req.initiator, req.prop));

            if (accept_max == null) {
                mutex.unlock();
                return new Response(req.prop, -1, req.initiator, null, true, this.peer_min[this.me]);
            }
            
            mutex.unlock();
            return new Response(req.prop, accept_max.prop, accept_max.initiator, accept_max.value, true, this.peer_min[this.me]);
        }
        mutex.unlock();

        // prepare reject
        return new Response(prep_max.prop, -1, prep_max.initiator, -1, false, this.peer_min[this.me]);
        
    }
    
    public Response Accept(Request req) {
        updateForgettable(req.min_done, req.initiator);
        mutex.lock();
        PrepareData prep_max = SequencePrepMap.getOrDefault(req.seq, null);

        if (prep_max == null || (req.prop > prep_max.prop) || (req.prop == prep_max.prop && req.initiator >= prep_max.initiator)) {
            SequencePrepMap.put(req.seq, new PrepareData(req.initiator, req.prop));
            SequenceAcceptMap.put(req.seq, new AcceptData(req.value, req.initiator, req.prop));
            mutex.unlock();
            return new Response(req.prop, true, this.peer_min[this.me]);
        }
        // accept_reject
        mutex.unlock();
        return new Response(prep_max.prop, -1, prep_max.initiator, -1, false, this.peer_min[this.me]);
    }

    public Response Decide(Request req){
        // your code here
        updateForgettable(req.min_done, req.initiator);
        mutex.lock();
        if (!DecidedValMap.containsKey(req.seq)) {
            System.out.println("DECIDED -- ME: " + this.me + " SEQ: " + req.seq + " Value: " + req.value);
            DecidedValMap.put(req.seq, new retStatus(State.Decided, req.value));
        }
        mutex.unlock();

        // add to dict
        return new Response(req.prop, true, this.peer_min[this.me]);
    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
        this.peer_min[this.me] = Math.max(seq, this.peer_min[this.me]);
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
        return this.max_seq_seen;
    }

    /**
     * Min() should return one more than the minimum among z_i,
     * where z_i is the highest number ever passed
     * to Done() on peer i. A peers z_i is -1 if it has
     * never called Done().

     * Paxos is required to have forgotten all information
     * about any instances it knows that are < Min().
     * The point is to free up memory in long-running
     * Paxos-based servers.

     * Paxos peers need to exchange their highest Done()
     * arguments in order to implement Min(). These
     * exchanges can be piggybacked on ordinary Paxos
     * agreement protocol messages, so it is OK if one
     * peers Min does not reflect another Peers Done()
     * until after the next instance is agreed to.

     * The fact that Min() is defined as a minimum over
     * all Paxos peers means that Min() cannot increase until
     * all peers have been heard from. So if a peer is dead
     * or unreachable, other peers Min()s will not increase
     * even if all reachable peers call Done. The reason for
     * this is that when the unreachable peer comes back to
     * life, it will need to catch up on instances that it
     * missed -- the other peers therefore cannot forget these
     * instances.
     */
    public int Min(){
        // Your code here
        return min_forg + 1;
    }



    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
    public retStatus Status(int seq){
        // Your code 
        mutex.lock();
        retStatus r = DecidedValMap.getOrDefault(seq, new retStatus(State.Pending, null));
        mutex.unlock();
        return r;
    }

    /**
     * helper class for Status() return
     */
    public class retStatus{
        public State state;
        public Object v;

        public retStatus(State state, Object v){
            this.state = state;
            this.v = v;
        }
    }

    /**
     * Tell the peer to shut itself down.
     * For testing.
     * Please don't change these four functions.
     */
    public void Kill(){
        this.dead.getAndSet(true);
        if(this.registry != null){
            try {
                UnicastRemoteObject.unexportObject(this.registry, true);
            } catch(Exception e){
                System.out.println("None reference");
            }
        }
    }

    public boolean isDead(){
        return this.dead.get();
    }

    public void setUnreliable(){
        this.unreliable.getAndSet(true);
    }

    public boolean isunreliable(){
        return this.unreliable.get();
    }

}
