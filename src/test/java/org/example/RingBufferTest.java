package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Sidorin Aleksei
 * @version 1.0
 * @since 06.11.2025
 */
class RingBufferTest {

    private RingBuffer<Integer> buffer;
    private final int CAPACITY = 3;

    @BeforeEach
    void setUp() {
        buffer = new RingBuffer<>(CAPACITY);
    }

    @Test
    @DisplayName("Создание буфера с положительной емкостью")
    void testCreationWithValidCapacity() {
        assertEquals(CAPACITY, buffer.getCapacity());
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    @DisplayName("Создание буфера с некорректной емкостью должно бросать исключение")
    void testCreationWithInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(-1));
    }

    @Test
    @DisplayName("Добавление и извлечение одного элемента")
    void testSinglePutAndTake() throws InterruptedException {
        buffer.put(42);
        int result = buffer.take();

        assertEquals(42, result);
        assertTrue(buffer.isEmpty());
    }

    @Test
    @DisplayName("Добавление null должно бросать NullPointerException")
    void testPutNullThrowsException() {
        assertThrows(NullPointerException.class, () -> buffer.put(null));
        assertThrows(NullPointerException.class, () -> buffer.offer(null));
    }

    @Test
    @DisplayName("Буфер должен работать по принципу FIFO")
    void testFIFOOrder() throws InterruptedException {
        buffer.put(1);
        buffer.put(2);
        buffer.put(3);

        assertEquals(1, buffer.take());
        assertEquals(2, buffer.take());
        assertEquals(3, buffer.take());
    }

    @Test
    @DisplayName("Кольцевое поведение буфера")
    void testCircularBehavior() throws InterruptedException {
        buffer.put(1);
        buffer.put(2);
        buffer.put(3);

        assertEquals(1, buffer.take());

        buffer.put(4);

        assertEquals(2, buffer.take());
        assertEquals(3, buffer.take());
        assertEquals(4, buffer.take());
    }

    @Test
    @DisplayName("Метод offer возвращает false при полном буфере")
    void testOfferWhenFull() {
        assertTrue(buffer.offer(1));
        assertTrue(buffer.offer(2));
        assertTrue(buffer.offer(3));

        assertFalse(buffer.offer(4));
        assertTrue(buffer.isFull());
    }

    @Test
    @DisplayName("Метод poll возвращает null при пустом буфере")
    void testPollWhenEmpty() {
        assertNull(buffer.poll());
        assertTrue(buffer.isEmpty());
    }

    @Test
    @DisplayName("Методы size, isEmpty, isFull работают корректно")
    void testSizeAndStateMethods() throws InterruptedException {
        assertEquals(0, buffer.size());
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        buffer.put(1);
        assertEquals(1, buffer.size());
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());

        buffer.put(2);
        buffer.put(3);
        assertEquals(3, buffer.size());
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());

        buffer.take();
        assertEquals(2, buffer.size());
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    @DisplayName("Очистка буфера")
    void testClear() throws InterruptedException {
        buffer.put(1);
        buffer.put(2);

        buffer.clear();

        assertEquals(0, buffer.size());
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        buffer.put(10);
        assertEquals(1, buffer.size());
        assertEquals(10, buffer.take());
    }

    @Test
    @DisplayName("Блокировка при попытке взять из пустого буфера")
    void testTakeBlocksWhenEmpty() throws InterruptedException {
        Thread consumer = new Thread(() -> {
            try {
                Integer result = buffer.take();
                assertEquals(100, result);
            } catch (InterruptedException e) {
                fail("Thread was interrupted unexpectedly");
            }
        });

        consumer.start();

        Thread.sleep(100);
        assertEquals(Thread.State.WAITING, consumer.getState());

        buffer.put(100);

        consumer.join(1000);
        assertFalse(consumer.isAlive());
    }

    @Test
    @DisplayName("Блокировка при попытке положить в полный буфер")
    void testPutBlocksWhenFull() throws InterruptedException {
        buffer.put(1);
        buffer.put(2);
        buffer.put(3);

        Thread producer = new Thread(() -> {
            try {
                buffer.put(4);
            } catch (InterruptedException e) {
                fail("Thread was interrupted unexpectedly");
            }
        });

        producer.start();

        Thread.sleep(100);
        assertEquals(Thread.State.WAITING, producer.getState());

        buffer.take();

        producer.join(1000);
        assertFalse(producer.isAlive());
    }

    @Test
    @DisplayName("Многопоточное использование - производитель и потребитель")
    void testProducerConsumer() throws InterruptedException {
        final int ITEMS_COUNT = 100;
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < ITEMS_COUNT; i++) {
                    buffer.put(i);
                    produced.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < ITEMS_COUNT; i++) {
                    Integer item = buffer.take();
                    assertNotNull(item);
                    consumed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        producer.join(2000);
        consumer.join(2000);

        assertEquals(ITEMS_COUNT, produced.get());
        assertEquals(ITEMS_COUNT, consumed.get());
        assertTrue(buffer.isEmpty());
    }

    @Test
    @DisplayName("Несколько производителей и потребителей")
    void testMultipleProducersConsumers() throws InterruptedException {
        final int PRODUCERS = 3;
        final int CONSUMERS = 2;
        final int ITEMS_PER_PRODUCER = 10;
        final int TOTAL_ITEMS = PRODUCERS * ITEMS_PER_PRODUCER;

        AtomicInteger consumedCount = new AtomicInteger();
        CountDownLatch producersLatch = new CountDownLatch(PRODUCERS);
        CountDownLatch consumersLatch = new CountDownLatch(CONSUMERS);

        for (int i = 0; i < PRODUCERS; i++) {
            final int producerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < ITEMS_PER_PRODUCER; j++) {
                        buffer.put(producerId * 100 + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersLatch.countDown();
                }
            }).start();
        }

        for (int i = 0; i < CONSUMERS; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < TOTAL_ITEMS / CONSUMERS; j++) {
                        Integer item = buffer.take();
                        assertNotNull(item);
                        consumedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    consumersLatch.countDown();
                }
            }).start();
        }

        assertTrue(producersLatch.await(5, TimeUnit.SECONDS));
        assertTrue(consumersLatch.await(5, TimeUnit.SECONDS));

        assertEquals(TOTAL_ITEMS, consumedCount.get());
        assertTrue(buffer.isEmpty());
    }

    @Test
    @DisplayName("Прерывание заблокированного потока")
    void testInterruptBlockedThread() throws InterruptedException {
        Thread blockedThread = new Thread(() -> {
            try {
                buffer.take();
                fail("Should have been interrupted");
            } catch (InterruptedException e) {
                assertTrue(Thread.currentThread().isInterrupted());
            }
        });

        blockedThread.start();

        Thread.sleep(100);

        blockedThread.interrupt();

        blockedThread.join(1000);
        assertFalse(blockedThread.isAlive());
    }

    @Test
    @DisplayName("Смешанное использование put/take и offer/poll")
    void testMixedOperations() throws InterruptedException {
        assertTrue(buffer.offer(1));
        assertTrue(buffer.offer(2));

        buffer.put(3);

        assertFalse(buffer.offer(4));

        assertEquals(1, buffer.poll());
        assertEquals(2, buffer.poll());

        assertEquals(3, buffer.take());

        assertNull(buffer.poll());
    }

    @Test
    @DisplayName("Буфер емкостью 1")
    void testSingleElementBuffer() throws InterruptedException {
        RingBuffer<Integer> singleBuffer = new RingBuffer<>(1);

        assertTrue(singleBuffer.isEmpty());
        assertFalse(singleBuffer.isFull());

        singleBuffer.put(42);
        assertTrue(singleBuffer.isFull());
        assertFalse(singleBuffer.isEmpty());

        assertEquals(42, singleBuffer.take());
        assertTrue(singleBuffer.isEmpty());
    }
}