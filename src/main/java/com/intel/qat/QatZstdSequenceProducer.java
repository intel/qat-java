package com.intel.qat;

import com.github.luben.zstd.SequenceProducer;

/**
 * An implementation of an abstract sequence producer plugin. Pass an instance of this class to
 * {@link com.github.luben.zstd.ZstdCompressCtx#registerSequenceProducer(SequenceProducer)} to
 * enable QAT-accelerated compression with zstd.
 */
public class QatZstdSequenceProducer implements SequenceProducer {

  /** Constructs a new ZSTD sequence producer. */
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
