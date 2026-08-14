package com.patchsimple.messagebuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @TempDir
    Path tempDir;

    private InMemoryMessageBuffer newBuffer(int maxSize) {
        return new InMemoryMessageBuffer(maxSize, tempDir.resolve("test-messages.dat"));
    }

    @Test
    void someClassMethodName() {
        assertTrue(true);
    }

    @Test
    void pushSuccess_WhenQueueHasRoom() throws QueueFullException, QueueEmptyException {
        InMemoryMessageBuffer buffer = newBuffer(10);
        Message msg = new Message("hello");
        buffer.push(msg);
        assertEquals(msg, buffer.pop());
    }

    @Test
    void pushThrowsQueueFullException_whenQueueIsFull() throws QueueFullException {
        InMemoryMessageBuffer buffer = newBuffer(1);
        buffer.push(new Message("first message"));
        assertThrows(QueueFullException.class, () -> buffer.push(new Message("second message")));
    }

    @Test
    void popShouldRemoveItem_andDisplayMessage_whenQueueHasItem() throws QueueEmptyException, QueueFullException {
        InMemoryMessageBuffer buffer = newBuffer(10);
        buffer.push(new Message("hello world"));
        Message result = buffer.pop();
        assertEquals("hello world", result.body());
    }

    @Test
    void popThrowsEmptyQueueException_whenQueueIsEmpty() throws QueueEmptyException {
        InMemoryMessageBuffer buffer = newBuffer(10);
        assertThrows(QueueEmptyException.class, buffer::pop);
    }
}