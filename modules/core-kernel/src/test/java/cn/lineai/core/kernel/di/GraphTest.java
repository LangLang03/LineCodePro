package cn.lineai.core.kernel.di;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class GraphTest {

    interface Engine {
    }

    static final class EngineImpl implements Engine {
    }

    @Test
    public void resolvesSingletonOnce() {
        Graph graph = new Graph.Builder()
                .singleton(Engine.class, g -> new EngineImpl())
                .build();
        assertSame(graph.get(Engine.class), graph.get(Engine.class));
    }

    @Test
    public void factoryCreatesFreshInstances() {
        Graph graph = new Graph.Builder()
                .factory(Engine.class, g -> new EngineImpl())
                .build();
        Engine first = graph.get(Engine.class);
        Engine second = graph.get(Engine.class);
        assertNotNull(first);
        org.junit.Assert.assertNotSame(first, second);
    }

    @Test
    public void childResolvesParentBinding() {
        Graph root = new Graph.Builder()
                .instance(Engine.class, new EngineImpl())
                .build();
        Graph child = root.newChild(null);
        assertSame(root.get(Engine.class), child.get(Engine.class));
    }

    @Test
    public void missingBindingCarriesPath() {
        Graph graph = new Graph.Builder().build();
        try {
            graph.get(Engine.class);
            fail("expected MissingBindingException");
        } catch (Graph.MissingBindingException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("Engine"));
        }
    }

    interface A {
    }

    interface B {
    }

    @Test
    public void detectsCircularDependency() {
        Graph graph = new Graph.Builder()
                .singleton(A.class, g -> {
                    g.get(B.class);
                    return new A() {
                    };
                })
                .singleton(B.class, g -> {
                    g.get(A.class);
                    return new B() {
                    };
                })
                .build();
        try {
            graph.get(A.class);
            fail("expected CircularDependencyException");
        } catch (Graph.CircularDependencyException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("Circular dependency"));
        }
    }

    static final class EngineCloseable implements Engine, AutoCloseable {
        final List<String> closed;

        EngineCloseable(List<String> closed) {
            this.closed = closed;
        }

        @Override
        public void close() {
            closed.add("engine");
        }
    }

    static final class ACloseable implements A, AutoCloseable {
        final List<String> closed;

        ACloseable(List<String> closed) {
            this.closed = closed;
        }

        @Override
        public void close() {
            closed.add("a");
        }
    }

    @Test
    public void closesOwnedInstancesInReverseOrder() {
        List<String> closed = new ArrayList<>();
        Graph graph = new Graph.Builder()
                .singleton(Engine.class, g -> new EngineCloseable(closed))
                .singleton(A.class, g -> new ACloseable(closed))
                .build();
        graph.get(Engine.class);
        graph.get(A.class);
        graph.close();
        assertEquals(2, closed.size());
        assertEquals("a", closed.get(0));
        assertEquals("engine", closed.get(1));
    }
}
