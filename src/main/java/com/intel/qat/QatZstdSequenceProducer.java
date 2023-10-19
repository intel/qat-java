package com.intel.qat;

import com.github.luben.zstd.AbstractSequenceProducer;
import java.lang.ref.Cleaner;

/**
 * An implementation of an abstract sequence producer plugin. To be used in conjunction with
 * zstd-jni, passed as a
 */
public class QatZstdSequenceProducer extends AbstractSequenceProducer implements AutoCloseable {
  /** Pointer to the `sequenceProducerState` */
  private long sequenceProducerState;

  /** Cleaner instance associated with this object. */
  private static Cleaner cleaner;

  /** Cleaner.Cleanable instance representing zstd cleanup action. */
  private final Cleaner.Cleanable cleanable;

  static {
    SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      cleaner = Cleaner.create();
    } else {
      java.security.PrivilegedAction<Void> pa =
          () -> {
            cleaner = Cleaner.create();
            return null;
          };
      java.security.AccessController.doPrivileged(pa);
    }
  }

  public static final class Status {
    /** Success. */
    public static final int OK = 0;

    /** The device was already started. */
    public static final int STARTED = 1;

    /** An unspecified error occurred. */
    public static final int FAIL = -1;
  }

  public static int startDevice() {
    return InternalJNI.zstdStartDevice();
  }

  public static void stopDevice() {
    InternalJNI.zstdStopDevice();
  }

  public QatZstdSequenceProducer() {
    sequenceProducerState = InternalJNI.zstdCreateSeqProdState();
    assert sequenceProducerState != 0;
    cleanable = cleaner.register(this, new ZstdCleaner(sequenceProducerState));
  }

  @Override
  public long getProducerFunction() {
    return InternalJNI.zstdGetSeqProdFunction();
  }

  @Override
  public long getStatePointer() {
    return sequenceProducerState;
  }

  @Override
  public void close() {
    cleanable.clean();
  }

  /** A class that represents a cleaner action for a zstd session. */
  private static class ZstdCleaner implements Runnable {
    private long sequenceProducerState;

    /** Creates a new cleaner object that cleans up the specified session. */
    public ZstdCleaner(long sequenceProducerState) {
      this.sequenceProducerState = sequenceProducerState;
    }

    @Override
    public void run() {
      if (sequenceProducerState == 0)
        throw new RuntimeException("Attempted to double-free the sequence producer state");

      InternalJNI.zstdFreeSeqProdState(sequenceProducerState);
      sequenceProducerState = 0;
    }
  }
}
