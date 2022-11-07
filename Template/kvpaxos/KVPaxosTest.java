package kvpaxos;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This is a subset of entire test cases
 * For your reference only.
 */
public class KVPaxosTest {


    public void check(Client ck, String key, Integer value){
        Integer v = ck.Get(key);
        assertTrue("Get(" + key + ")->" + v + ", expected " + value, v.equals(value));
    }

    // @Test
    public void TestBasic(){
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for(int i = 0 ; i < npaxos; i++){
            ports[i] = 1100+i;
            peers[i] = host;
        }
        for(int i = 0; i < npaxos; i++){
            kva[i] = new Server(peers, ports, i);
        }

        Client ck = new Client(peers, ports);
        System.out.println("Test: Basic put/get ...");
        ck.Put("app", 6);
        check(ck, "app", 6);
        ck.Put("a", 70);
        check(ck, "a", 70);

        System.out.println("... Passed");

    }

    @Test
    public void TestBasic2(){
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for(int i = 0 ; i < npaxos; i++){
            ports[i] = 1100+i;
            peers[i] = host;
        }

        for(int i = 0; i < npaxos; i++){
            kva[i] = new Server(peers, ports, i);
        }

        ports[0] = 1;

        Client ck = new Client(peers, ports);
        System.out.println("Test: Basic put/get ...");
        ck.Put("app", 6);
        ck.Put("app", 7);
        ck.Put("app",8);
        ck.Put("app", 9);
        check(ck, "app", 9);

        ck.Put("a", 70);
        check(ck, "a", 70);


        ports[0] = 1100;

        System.out.println("ONE");
        for (int k : kva[1].px.getD().keySet()) {
            System.out.println(Op.class.cast(kva[1].px.getD().get(k).v));
        }
        
        System.out.println("Zero");
        for (int k : kva[0].px.getD().keySet()) {
            System.out.println(Op.class.cast(kva[0].px.getD().get(k).v));
        }

        Client ck2 = new Client(peers, ports);

        ck2.Put("temp", 10);
        
        System.out.println("ONE");
        for (int k : kva[1].px.getD().keySet()) {
            System.out.println(Op.class.cast(kva[1].px.getD().get(k).v));
        }
        System.out.println("ZERO");
        for (int k : kva[0].px.getD().keySet()) {
            System.out.println(Op.class.cast(kva[0].px.getD().get(k).v));
        }

        System.out.println("... Passed");

    }

}
