package paxos;
import java.io.ObjectInputFilter.Status;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.midi.Sequence;
import javax.sql.rowset.serial.SerialArray;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is the main class you need to implement paxos instances.
 */

class SequenceData {
    Object value;
    int initiator;
    int prop;
    boolean isDecided;

    SequenceData(Object value, int initiator, int prop, boolean isDecided) {
        this.value = value;
        this.initiator = initiator;
        this.prop = prop;
        this.isDecided = isDecided;
    }

    public void update(Object value, int initiator, int prop, boolean isDecided) {
        if (this.isDecided) return;

        this.value = value;
        this.initiator = initiator;
        this.prop = prop;
        this.isDecided = isDecided;
    }

    public String toString() {
        return "*SEQUENCEDATA* Val: " + this.value + " initiator: " + this.initiator + " Prop: " + this.prop;
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

    HashMap<Integer, SequenceData> SequencePrepMap;
    HashMap<Integer, SequenceData> SequenceAcceptMap;
    HashMap<Integer, retStatus> DecidedValMap;

    int seq;
    Object value;

    State d_status;
    Object d_val;


    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */
    public Paxos(int me, String[] peers, int[] ports){

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here

        SequencePrepMap = new HashMap<Integer, SequenceData>(); 
        SequenceAcceptMap = new HashMap<Integer, SequenceData>(); 
        DecidedValMap = new HashMap<Integer, retStatus>();



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

    @Override
    public void run(){
        //Your code here
        mutex.lock();
        int seq_cp = this.seq;
        Object val_cp = this.value;
        mutex.unlock();

        retStatus decidedStatus = DecidedValMap.getOrDefault(seq_cp, null);
        
        while (decidedStatus == null || decidedStatus.state != State.Decided) {
            mutex.lock();
            SequenceData s = SequencePrepMap.getOrDefault(seq_cp, null);
            if (s == null) {
                s = new SequenceData(null, this.me, -1, false);
                SequencePrepMap.put(seq_cp, s);
            }
            // System.out.println(s);
            int prop_number = s.prop + 1;
            mutex.unlock();
            SequenceData temp_max = new SequenceData(val_cp, this.me, prop_number, false);

            boolean someonDecided = false;

            int count = 0;
            for (int i = 0; i < this.peers.length; i++) {
                Response res = Call("Prepare", new Request(seq_cp, prop_number, this.me, val_cp), i);

                if (res == null) {
                    continue;
                }

                someonDecided = res.isDecided || someonDecided;
                
                if (res.accept == false) {
                    SequencePrepMap.put(seq_cp, new SequenceData(res.max_val, res.initiator, res.max_prop, res.isDecided));
                    temp_max.update(res.max_val, res.initiator, res.max_prop, res.isDecided);
                    continue;
                } else {
                    // if (this.me == 0) {

                    //     System.out.println(res.max_prop > temp_max.prop);
                    //     System.out.println(res.max_prop == temp_max.prop);
                    //     System.out.println(res.max_prop + " " + temp_max.prop);
                    //     System.out.println(res.initiator > temp_max.initiator);
                    // }
                    if (res.max_prop > temp_max.prop || (res.max_prop == temp_max.prop && res.initiator > temp_max.initiator)) {
                        temp_max.update(res.max_val, res.initiator, res.max_prop, res.isDecided);
                    }
                    if (this.me == 0){
                        // System.out.println(res);
                        // System.out.println(temp_max);
                    }
                }
                count++;
            }

            if (!someonDecided) {
                if (count <= (this.peers.length/2)) {
                    continue;
                }
    
                // Here we have some value in temp max > could me mine or someone else's
                count = 0;
    
                // send accept to all for this value with the same proposal number
    
                for (int i = 0; i < this.peers.length; i++) {
                    Response res = Call("Accept", new Request(seq_cp, prop_number, this.me, temp_max.value), i);
                    if (res == null || !res.accept) {
                        continue;
                    }
                    // System.out.println("Initiator: " + this.me + " PROP no: " + prop_number + " Acceptance from: " + i + " for SEQ: " + seq_cp + " for val: " + temp_max.value);
                    count++;
                }
    
                if (count <= (this.peers.length/2)) {
                    continue;
                }
            }

            count = 0;
            // System.out.println("SEQ: " + seq_cp + " VAL: " + val_cp + " ME: " + this.me + " temp_max: " + temp_max.value);
            // System.out.println(this.me + " DECIDED on val: " + temp_max.value + " seq: " + seq_cp + " LEN: " + this.peers.length);
            for (int i = 0; i < this.peers.length; i++) {
                if (i == this.me) { 
                    Decide(new Request(seq_cp, prop_number, this.me, temp_max.value));
                }
                Response res = Call("Decide", new Request(seq_cp, prop_number, this.me, temp_max.value), i);

                if (res == null) {
                    continue;
                }
                count++;
            }

            mutex.lock();
            decidedStatus = DecidedValMap.getOrDefault(seq_cp, null);
            mutex.unlock();
        }

    }

    // RMI handler
    public Response Prepare(Request req){
        // your code here
        mutex.lock();
        SequenceData prep_max = SequencePrepMap.getOrDefault(req.seq, null);
        boolean isDecided = DecidedValMap.getOrDefault(req.seq, new retStatus(State.Pending, null)).state == State.Decided;

        if (prep_max == null || (req.prop > prep_max.prop) || (req.prop == prep_max.prop && req.initiator > prep_max.initiator)) {
            SequencePrepMap.put(req.seq, new SequenceData(req.value, req.initiator, req.prop, false));

            SequenceData accept_max = SequenceAcceptMap.getOrDefault(req.seq, null);
            mutex.unlock();
            if (accept_max == null) {
                return new Response(req.prop, -1, -1, null, true, isDecided);
            }
            return new Response(req.prop, accept_max.prop, accept_max.initiator, accept_max.value, true, isDecided);
        }
        mutex.unlock();

        // prepare reject
        return new Response(req.prop, prep_max.prop, prep_max.initiator, prep_max.value, false, isDecided);
        
    }
    
    public Response Accept(Request req) {
        mutex.lock();
        SequenceData prep_max = SequencePrepMap.getOrDefault(req.seq, null);
        boolean isDecided = DecidedValMap.getOrDefault(req.seq, new retStatus(State.Pending, null)).state == State.Decided;

        if (prep_max == null || (req.prop > prep_max.prop) || (req.prop == prep_max.prop && req.initiator >= prep_max.initiator)) {
            SequencePrepMap.put(req.seq, new SequenceData(req.value, req.initiator, req.prop, false));
            SequenceAcceptMap.put(req.seq, new SequenceData(req.value, req.initiator, req.prop, false));
            mutex.unlock();
            return new Response(req.prop, true, isDecided);
        }
        // accept_reject
        mutex.unlock();
        return new Response(req.prop, false, isDecided);
    }

    public Response Decide(Request req){
        // your code here
        
        mutex.lock();
        if (!DecidedValMap.containsKey(req.seq)) {
            // System.out.println("Deciding: " + req.value + " on SEQ: " + req.seq + " Me: " + this.me);
            DecidedValMap.put(req.seq, new retStatus(State.Decided, req.value));
        }
        mutex.unlock();

        // add to dict
        return new Response(req.prop, true, true);
    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
        return 10;
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
        return 1;
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
