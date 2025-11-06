package org.example;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Потокобезопасная реализация кольцевого буфера (circular buffer).
 * Поддерживает операции добавления и извлечения элементов с блокировками
 * для многопоточного использования.
 *
 * <p>Буфер имеет фиксированную емкость и работает по принципу FIFO (First-In-First-Out).
 * Когда буфер заполнен, операции put блокируют поток до освобождения места.
 * Когда буфер пуст, операции take блокируют поток до появления данных.
 *
 * <p>Реализация использует {@link ReentrantLock} и {@link Condition} для эффективной
 * синхронизации между производителями и потребителями.
 *
 * @param <T> тип элементов, хранящихся в буфере
 */
public class RingBuffer<T> {
    private final T[] buffer;
    private final int capacity;
    private int head;
    private int tail;
    private int count;

    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    /**
     * Создает новый потокобезопасный кольцевой буфер указанной емкости.
     *
     * @param capacity максимальное количество элементов, которые может содержать буфер
     * @throws IllegalArgumentException если capacity меньше или равно 0
     */
    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = (T[]) new Object[capacity];
        this.head = 0;
        this.tail = 0;
        this.count = 0;
    }

    /**
     * Добавляет элемент в буфер. Если буфер заполнен, поток блокируется
     * до тех пор, пока не освободится место.
     *
     * @param item элемент для добавления в буфер
     * @throws InterruptedException если поток прерван во время ожидания
     * @throws NullPointerException если item равен null
     */
    public void put(T item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("Item cannot be null");
        }

        lock.lock();
        try {
            while (count == capacity) {
                notFull.await();
            }

            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            count++;

            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлекает и удаляет элемент из буфера. Если буфер пуст, поток блокируется
     * до тех пор, пока не появится элемент.
     *
     * @return первый элемент из буфера
     * @throws InterruptedException если поток прерван во время ожидания
     */
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            T item = buffer[head];
            buffer[head] = null;
            head = (head + 1) % capacity;
            count--;

            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Пытается добавить элемент в буфер без блокировки.
     * Если буфер заполнен, элемент не добавляется.
     *
     * @param item элемент для добавления в буфер
     * @return true если элемент успешно добавлен, false если буфер заполнен
     * @throws NullPointerException если item равен null
     */
    public boolean offer(T item) {
        if (item == null) {
            throw new NullPointerException("Item cannot be null");
        }

        lock.lock();
        try {
            if (count == capacity) {
                return false;
            }

            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            count++;

            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Пытается извлечь элемент из буфера без блокировки.
     * Если буфер пуст, возвращает null.
     *
     * @return первый элемент из буфера или null если буфер пуст
     */
    public T poll() {
        lock.lock();
        try {
            if (count == 0) {
                return null;
            }

            T item = buffer[head];
            buffer[head] = null;
            head = (head + 1) % capacity;
            count--;

            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Возвращает текущее количество элементов в буфере.
     *
     * @return количество элементов в буфере
     */
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Проверяет, пуст ли буфер.
     *
     * @return true если буфер пуст, false в противном случае
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Проверяет, заполнен ли буфер.
     *
     * @return true если буфер заполнен, false в противном случае
     */
    public boolean isFull() {
        return size() == capacity;
    }

    /**
     * Возвращает максимальную емкость буфера.
     *
     * @return емкость буфера
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Очищает буфер, удаляя все элементы.
     * После вызова этого метода буфер становится пустым.
     */
    public void clear() {
        lock.lock();
        try {
            for (int i = 0; i < capacity; i++) {
                buffer[i] = null;
            }
            head = 0;
            tail = 0;
            count = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }
}