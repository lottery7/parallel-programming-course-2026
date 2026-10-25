package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /**
     * Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.
     */
    boolean logging;
    /**
     * Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100".
     */
    final List<String> log = new ArrayList<>();
    /**
     * null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2).
     */
    String deadlock;

    public Scheduler() {
    }

    private final List<ThreadState> states = new ArrayList<>();
    private final Semaphore yielded = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    public static final ThreadLocal<ThreadState> CUR_STATE = new ThreadLocal<>();
    final public List<SchedLock> locks = new ArrayList<>();
    private boolean aborted;

    /**
     * Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.
     */
    public void spawn(Runnable body) {
        states.add(new ThreadState(this, states.size(), body));
    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        for (var state : states) {
            state.thread = new Thread(() -> {
                CUR_STATE.set(state);
                try {
                    state.body.run();
                } catch (AbortedException ignored) {
                } finally {
                    state.finished = true;
                    yielded.release();
                }
            });
            state.thread.start();
            yielded.acquireUninterruptibly();
        }

        int last = -1;
        try {
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean anyAlive = false;
                for (var state : states) {
                    if (!state.finished) {
                        anyAlive = true;
                        if (state.waitingFor == null || state.waitingFor.ownerState == null) {
                            enabled.add(state.id);
                        }
                    }
                }
                if (!anyAlive) break;
                if (enabled.isEmpty()) {
                    deadlock = heldString();
                    abort();
                    break;
                }

                int id = strategy.pick(enabled, last);
                last = id;
                var chosenState = states.get(id);
                trace.append(chosenState.getLetter());
                chosenState.go.release();
                yielded.acquireUninterruptibly();
            }
        } catch (RuntimeException e) {
            abort();
            throw e;
        }

        for (var state : states) {
            try {
                state.thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String heldString() {
        List<String> held = new ArrayList<>();
        for (var lock : locks) {
            if (lock.ownerState != null) held.add(lock.name + ":" + lock.ownerState.getLetter());
        }
        return "held={" + String.join(", ", held) + "}";
    }

    private void abort() {
        aborted = true;
        for (var state : states) {
            if (!state.finished) state.go.release();
        }
    }

    /**
     * Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB".
     */
    public String traceString() {
        return trace.toString();
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна.
     */
    public static void point() {
        logOperation("window");
        CUR_STATE.get().touched = true;
    }

    /**
     * Ждать, пока cond не станет true. До этого поток не попадает в enabled.
     */
    public static void await(BooleanSupplier cond) {
        throw new UnsupportedOperationException("TODO этап 1: Scheduler.await");
    }

    static void touchShared() {
        var state = CUR_STATE.get();
        if (state.scheduler.aborted) throw new AbortedException();
        if (state.touched) {
            state.scheduler.yielded.release();
            state.go.acquireUninterruptibly();
            if (state.scheduler.aborted) throw new AbortedException();
        }
        state.touched = true;
    }

    static void logOperation(String operation) {
        var state = CUR_STATE.get();
        if (state.scheduler.logging && !state.scheduler.aborted) {
            state.scheduler.log.add(state.getLetter() + "  " + operation);
        }
    }

    private static final class AbortedException extends RuntimeException {
        AbortedException() {
            super(null, null, false, false);
        }
    }

}
