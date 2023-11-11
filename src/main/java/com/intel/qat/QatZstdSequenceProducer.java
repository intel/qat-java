package com.intel.qat;

import com.github.luben.zstd.SequenceProducer;

/**
 * An implementation of an abstract sequence producer plugin. Pass an instance of this class to
 * {@link com.github.luben.zstd.ZstdCompressCtx#registerSequenceProducer(SequenceProducer)} to
 * enable QAT-accelerated compression with zstd.
 */
public class QatZstdSequenceProducer implements SequenceProducer {
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

  public QatZstdSequenceProducer() {}

  @Override
  public long getFunctionPointer() {
    return InternalJNI.zstdGetSeqProdFunction();
  }

  @Override
  public long createState() {
    return InternalJNI.zstdCreateSeqProdState();
  }

  @Override
  public void freeState(long state) {
    InternalJNI.zstdFreeSeqProdState(state);
  }
}
