package io.miniredis.core;

import java.util.ArrayList;
import java.util.List;

public interface Propagator {

    void propagate(List<byte[]> argv);

    final class Recording implements Propagator {
        private final List<List<byte[]>> commands = new ArrayList<>();

        @Override
        public void propagate(List<byte[]> argv) {
            List<byte[]> copy = new ArrayList<>(argv.size());
            for (byte[] a : argv) copy.add(a.clone());
            commands.add(copy);
        }

        public List<List<byte[]>> commands() { return commands; }
        public void clear() { commands.clear(); }
    }

    final class NoOp implements Propagator {
        public static final NoOp INSTANCE = new NoOp();
        private NoOp() {}
        @Override
        public void propagate(List<byte[]> argv) { /* intentionally empty until Week 4 */ }
    }
}