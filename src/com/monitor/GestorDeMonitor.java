package com.monitor;

import com.errors.IllegalPetriStateException;
import com.errors.OutsideWindowException;
import com.petri.PInvariant;
import com.petri.PetriNet;
import com.util.Log;
import com.util.Mutex;

import java.sql.Timestamp;
import java.util.Arrays;

public class GestorDeMonitor {

    private static final long NOWAIT = 0;

    private Mutex mutex;
    private PetriNet petriNet;
    private Colas queues;
    private Politicas policy;
    private Log eventLog = new Log();
    private int transitionsLeft;
    private int transitionsTotal;
    public static boolean keeprunning = true;
    private PInvariant pInvariant;

    /**
     * Clase encargada de implementar la logica del monitor
     * 
     * @param net   red de petri a utilizar
     * @param limit cantidad de transiciones a disparar
     */
    public GestorDeMonitor(PetriNet net, int limit) {

        mutex = new Mutex();
        petriNet = net;
        policy = new FairPolicy();
        queues = new Colas(petriNet.getTransitionsCount());
        transitionsLeft = limit;
        transitionsTotal = limit;
        pInvariant = new PInvariant(petriNet.getInitialMarking());

    }

    public long fireTransition(int transition) {

        boolean k = true;
        boolean result;

        mutex.acquire();

        while (k) {

            try {

                if(checkTransitionsLeft()){
                    mutex.release();
                    return NOWAIT;
                }

                result = petriNet.trigger(transition);

                if (result) {

                    /* Controlo invariantes */
                    try {
                        pInvariant.checkInvariants(petriNet.getCurrentMarking());
                    } catch (IllegalPetriStateException e) {
                        keeprunning = false;
                        e.printStackTrace();
                        return NOWAIT;
                    }

                    /* Disparo la cantidad de transiciones especificadas */
                    transitionsLeft--;
                    if(checkTransitionsLeft()){
                        mutex.release();
                        return NOWAIT;
                    }


                    System.out.printf("%3d | Transition %d triggered\n", transitionsTotal - transitionsLeft,
                            transition + 1);
                    String currentMarking = Arrays.toString(petriNet.getMatrix(PetriNet.MRK)[PetriNet.CURRENT]);

                    if(petriNet.getTimedTransitions()[transition] != null){
                        long time = petriNet.getTimedTransitions()[transition].getElapsedTime();
                        eventLog.log(String.format("T%-2d%sMarking: %s%sTime: %4d[ms]", transition + 1, Log.SEPARATOR, currentMarking, Log.SEPARATOR, time));
                    }
                    else{
                        eventLog.log(String.format("T%-2d%sMarking: %s", transition + 1, Log.SEPARATOR, currentMarking));
                    }

                    boolean[] enabledVector = petriNet.areEnabled().clone();
                    //System.out.println("EV: " + Arrays.toString(enabledVector));
                    boolean[] queueVector = queues.getQueued().clone();
                    //System.out.println("QV: " + Arrays.toString(queueVector));
                    boolean[] andVector = new boolean[petriNet.getTransitionsCount()];
                    Arrays.fill(andVector, false);

                    boolean m = false;

                    /* Calculo del vector AND */
                    for (int i = 0; i < petriNet.getTransitionsCount(); i++) {
                        if (queueVector[i] && enabledVector[i]) {
                            andVector[i] = true;
                            m = true;
                        }
                    }

                    //System.out.println("AV: "+Arrays.toString(andVector));

                    if (m) {

                        queues.wakeThread(policy.getNext(andVector));
                        return NOWAIT;

                    } else {
                        k = false;
                    }

                }
                else {

                    mutex.release();
                    queues.sleepThread(transition);

                }


            } catch (OutsideWindowException windowException) {

                if(windowException.isBefore()){

                    mutex.release();
                    return windowException.timeToSleep();

                    /*//TODO: simplemente duerme?
                    try {
                        Thread.sleep(windowException.timeToSleep());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
                else {
                    k = false;
                }
            }

        }

        mutex.release();
        return NOWAIT;

    }

    private boolean checkTransitionsLeft(){

        if (transitionsLeft < 0) {

            keeprunning = false;

            int index = 0;
            for(boolean sleepingThread : queues.getQueued()){

                if(sleepingThread){
                    queues.wakeThread(index);
                    return true;
                }

                index++;
            }

            return true;
        }

        return false;
    }

}
