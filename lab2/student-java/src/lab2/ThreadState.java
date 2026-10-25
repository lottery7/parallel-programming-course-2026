package lab2;

import java.util.concurrent.Semaphore;

public class ThreadState {
    final Scheduler scheduler;
    final int id;
    final Runnable body;
    final Semaphore go = new Semaphore(0);
    boolean finished;
    boolean touched = true;
    Thread thread;
    SchedLock waitingFor = null;

    public ThreadState(Scheduler scheduler, int id, Runnable body) {
        this.scheduler = scheduler;
        this.id = id;
        this.body = body;
    }

    public char getLetter() {
        return (char) ('A' + id);
    }
}
