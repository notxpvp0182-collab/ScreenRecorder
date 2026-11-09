package com.ahmed.screenrecorder.capture;

import com.ahmed.screenrecorder.config.RecordingConfig;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages off-heap direct ByteBuffer pooling and queue backpressure to prevent
 * Garbage Collection pauses and memory spikes during high-FPS recording.
 */
public class PerformanceManager {
    private ArrayBlockingQueue<ByteBuffer> freeBuffers;
    private int bufferCapacity;
    private final AtomicInteger droppedFrames = new AtomicInteger(0);
    private final AtomicInteger encodedFrames = new AtomicInteger(0);

    public void initializePool(int frameByteSize, int poolSize) {
        this.bufferCapacity = frameByteSize;
        this.freeBuffers = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            freeBuffers.offer(ByteBuffer.allocateDirect(frameByteSize).order(ByteOrder.nativeOrder()));
        }
        droppedFrames.set(0);
        encodedFrames.set(0);
    }

    public ByteBuffer acquireBuffer() {
        if (freeBuffers == null) return null;
        return freeBuffers.poll();
    }

    public void releaseBuffer(ByteBuffer buffer) {
        if (buffer != null && freeBuffers != null && buffer.capacity() == bufferCapacity) {
            buffer.clear();
            freeBuffers.offer(buffer);
        }
    }

    public void recordFrameDropped() {
        droppedFrames.incrementAndGet();
    }

    public void recordFrameEncoded() {
        encodedFrames.incrementAndGet();
    }

    public int getDroppedFrames() {
        return droppedFrames.get();
    }

    public int getEncodedFrames() {
        return encodedFrames.get();
    }

    public static void applyThreadPriority(Thread thread) {
        RecordingConfig.EncoderPriority priority = RecordingConfig.get().encoderPriority;
        switch (priority) {
            case LOW -> thread.setPriority(Thread.MIN_PRIORITY + 1);
            case NORMAL -> thread.setPriority(Thread.NORM_PRIORITY);
            case HIGH -> thread.setPriority(Thread.NORM_PRIORITY + 2);
        }
    }

    public void cleanup() {
        if (freeBuffers != null) {
            freeBuffers.clear();
        }
    }
}
