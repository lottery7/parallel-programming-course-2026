package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() {
    }

    /**
     * Всегда первый поток из enabled.
     */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /**
     * Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1).
     */
    public static final class Replay implements Strategy {
        private final String trace;
        private int curPos;

        public Replay(String trace) {
            this.trace = trace;
        }

        /**
         * Сбросить позицию в трассе перед прогоном.
         */
        public void beginRun() {
            curPos = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (curPos >= trace.length()) {
                throw new IllegalStateException("trace " + trace + " ended at decision " + (curPos + 1));
            }
            char thread = trace.charAt(curPos++);
            int id = thread - 'A';
            if (!enabled.contains(id)) {
                throw new IllegalStateException("decision " + curPos + ": thread " + thread + " is not enabled, enabled=" + enabled);
            }
            return id;
        }
    }

    /**
     * Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed).
     */
    public static final class RandomPick implements Strategy {
        private final Random random;

        public RandomPick(long seed) {
            random = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            int i = random.nextInt(enabled.size());
            return enabled.get(i);
        }
    }

    /**
     * Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     * bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE.
     */
    public static final class Dfs implements Strategy {
        private final List<List<Integer>> options = new ArrayList<>();
        private final List<Integer> chosen = new ArrayList<>();
        private int depth;
        private final int bound;
        private int preemptions;

        public Dfs(int bound) {
            this.bound = bound;
        }

        /**
         * Подготовиться к новому прогону (позиция в пути, счётчик вытеснений).
         */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            int choice;
            if (depth < chosen.size()) {
                choice = options.get(depth).get(chosen.get(depth));
            } else {
                List<Integer> filteredEnabled = new ArrayList<>();
                if (preemptions == bound && enabled.contains(last)) {
                    filteredEnabled.add(last);
                } else {
                    filteredEnabled.addAll(enabled);
                }
                choice = filteredEnabled.getFirst();
                chosen.add(0);
                options.add(filteredEnabled);
            }
            depth++;
            if (choice != last && enabled.contains(last)) {
                preemptions++;
            }
            return choice;
        }

        /**
         * Выбрать следующий путь; false — дерево обойдено.
         */
        public boolean endRun() {
            while (!options.isEmpty() && options.getLast().size() - 1 == chosen.getLast()) {
                options.removeLast();
                chosen.removeLast();
                depth--;
            }
            if (options.isEmpty()) {
                return false;
            }
            chosen.set(chosen.size() - 1, chosen.getLast() + 1);
            return true;
        }
    }
}
