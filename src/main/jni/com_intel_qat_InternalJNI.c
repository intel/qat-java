/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_InternalJNI.h"

#include <qatzip.h>
#include <stdint.h>
#include <stdlib.h>
#include <threads.h>
#include <zstd.h>

#include "qatseqprod.h"
#include "util.h"

#define unlikely(x) __builtin_expect((x), 0)

#ifndef CPA_DC_API_VERSION_AT_LEAST
#define CPA_DC_API_VERSION_AT_LEAST(major, minor) \
  (CPA_DC_API_VERSION_NUM_MAJOR > major ||        \
   (CPA_DC_API_VERSION_NUM_MAJOR == major &&      \
    CPA_DC_API_VERSION_NUM_MINOR >= minor))
#endif

#if CPA_DC_API_VERSION_AT_LEAST(3, 1)
#define COMP_LVL_MAXIMUM QZ_LZS_COMP_LVL_MAXIMUM
#else
#define COMP_LVL_MAXIMUM QZ_DEFLATE_COMP_LVL_MAXIMUM
#endif

#define DEFLATE_ALGORITHM 0
#define LZ4_ALGORITHM 1
#define ZSTD_ALGORITHM 2

/**
 * ZLIB data_format ordinal QatZipper.DataFormat.
 */
#define ZLIB_DATA_FORMAT 4

/**
 * Stores the JNI field ID for the 'position' field of java.nio.ByteBuffer.
 * Used to access or modify the current position of a ByteBuffer instance.
 * Initialized during JNI library setup and remains valid for the JVM's
 * lifetime.
 */
static jfieldID g_nio_bytebuffer_position_id;

/**
 * Stores the JNI field ID for the 'bytesRead' field of com.intel.qat.QatZipper.
 * Enables this to read or update the number of bytes processed by a QatZipper
 * instance. Initialized during JNI library setup and remains valid for the
 * JVM's lifetime.
 */
static jfieldID g_qzip_bytes_read_id;

/**
 * Thread-local flag indicating whether the Zstandard (ZSTD) compression
 * algorithm is active. Non-zero when ZSTD is selected for the current thread’s
 * QAT session; zero otherwise. Used to manage ZSTD-specific resources and
 * behavior.
 */
static _Thread_local int g_algorithm_is_zstd;

/**
 * Thread-local pointer to the Zstandard (ZSTD) sequence producer state.
 * Holds the state for ZSTD compression operations in the current thread’s QAT
 * session. Allocated during ZSTD initialization and freed during cleanup (e.g.,
 * JNI_OnUnload). NULL when ZSTD is not active or state is uninitialized.
 */
static _Thread_local void *g_zstd_seqprod_state;

/**
 * QZSTD_startQatDevice() must be called only once!
 */
static once_flag g_init_qzstd_flag = ONCE_FLAG_INIT;
static int g_zstd_is_device_available;
static void initialize_qzstd_once(void) {
  g_zstd_is_device_available = QZSTD_startQatDevice();
}

/**
 * Represents a unique QAT session for specific compression parameters.
 * This structure is used to manage a QAT session with associated metadata.
 * All members should be accessed with care in a multi-threaded environment.
 */
typedef struct Session_T {
  int32_t key;             /**< Unique identifier for session parameters */
  int32_t reference_count; /**< Number of active references to this session */
  QzSession_T *qz_session; /**< Pointer to the QAT session object */
} Session_T;

/**
 * Defines the maximum number of unique QAT sessions allowed per thread.
 * This limit prevents excessive memory usage and ensures efficient session
 * caching for thread-local session storage. It is highly imporobable that an
 * application would require more than one distinct session configuration.
 */
#define MAX_SESSIONS_PER_THREAD 32

/**
 * Thread-local cache of QAT session objects, indexed by unique session keys.
 * Stores up to MAX_SESSIONS_PER_THREAD active sessions per thread to optimize
 * session reuse for distinct compression configurations. Each session holds a
 * Session_T struct with a QAT session handle and metadata.
 */
static _Thread_local Session_T g_session_cache[MAX_SESSIONS_PER_THREAD];

/**
 * Thread-local counter tracking the number of active QAT sessions in
 * g_session_cache. Incremented when a new session is created and decremented
 * when a session is torn down. Must not exceed MAX_SESSIONS_PER_THREAD to
 * prevent cache overflow.
 */
static _Thread_local int g_session_counter;

/**
 * Retrieves a cached QAT session matching the specified key from the
 * thread-local session cache. Searches the g_session_cache array up to
 * g_session_cache_counter for a session with a matching key. Returns a pointer
 * to the found Session_T or NULL if no match is found.
 *
 * @param key The unique session key generated from compression parameters
 * (e.g., algorithm, level).
 * @return A pointer to the matching Session_T in g_session_cache, or NULL if no
 * session matches.
 */
static Session_T *get_cached_session(int key) {
  for (int i = 0; i < g_session_counter; ++i) {
    if (g_session_cache[i].qz_session && g_session_cache[i].key == key) {
      return &g_session_cache[i];
    }
  }
  return NULL;
}

/**
 * Constructs a unique key for a session from compression parameters.
 * Combines parameters into a 32-bit key using bitwise operations to ensure
 * uniqueness for distinct configurations.
 *
 * @param algorithm    Compression algorithm (e.g., DEFLATE, LZ4, ZSTD).
 * @param level        Compression level (1 to COMP_LVL_MAXIMUM).
 * @param sw_backup    Software fallback flag (0 or 1).
 * @param polling_mode Polling mode for QAT (e.g., synchronous, asynchronous).
 * @param data_format  Data format (e.g., ZLIB, GZIP).
 * @param hw_buff_sz   Hardware buffer size in KB (converted to bits
 * internally).
 * @return A 32-bit key uniquely representing the session parameters.
 */
static int32_t generate_key_for_session(int32_t algorithm,
                                        int32_t level,
                                        int32_t sw_backup,
                                        int32_t polling_mode,
                                        int32_t data_format,
                                        int32_t hw_buff_sz) {
  int32_t key = 0;

  // Bit-field allocation: 4 bits each for algorithm, level, sw_backup,
  // polling_mode, data_format; 12 bits for hw_buff_sz (supports up to 4MB)
  key |= (algorithm & 0xF);
  key |= (level & 0xF) << 4;
  key |= (sw_backup & 0x1) << 8;
  key |= (polling_mode & 0xF) << 9;
  key |= (data_format & 0xF) << 13;
  key |= ((hw_buff_sz >> 10) & 0xFFF) << 17;

  return key;
}

/**
 * Sets up a deflate compression session with specified parameters.
 *
 * @param qz_session    Pointer to the QzSession_T structure to configure
 * @param level         Compression level (1-9)
 * @param sw_backup     Software backup flag (0 or 1)
 * @param polling_mode  Polling mode (0 for busy, non-zero for periodical)
 * @param data_format   Data format for compression
 * @param hw_buff_sz    Hardware buffer size
 * @return              QZ_OK on success, error code on failure
 */
static int setup_deflate_session(QzSession_T *qz_session,
                                 int level,
                                 unsigned char sw_backup,
                                 int polling_mode,
                                 int data_format,
                                 int hw_buff_sz) {
  QzSessionParamsDeflate_T params = {0};

  int rc = qzGetDefaultsDeflate(&params);
  if (rc != QZ_OK) {
    return rc;
  }

  // Validate and set parameters
  params.data_fmt = data_format;
  params.common_params.hw_buff_sz = hw_buff_sz;
  params.common_params.comp_lvl = (level < 1 || level > 9) ? 1 : level;
  params.common_params.sw_backup = sw_backup ? 1 : 0;
  params.common_params.polling_mode =
      (polling_mode == 0) ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

  return qzSetupSessionDeflate(qz_session, &params);
}

/**
 * Sets up a zlib deflate compression session with specified parameters.
 *
 * @param qz_session    Pointer to the QzSession_T structure to configure
 * @param level         Compression level (1-9)
 * @param sw_backup     Software backup flag (0 or 1)
 * @param polling_mode  Polling mode (0 for busy, non-zero for periodical)
 * @return              QZ_OK on success, error code on failure
 */
static int setup_deflate_zlib_session(QzSession_T *qz_session,
                                      int level,
                                      unsigned char sw_backup,
                                      int polling_mode) {
  QzSessionParamsDeflateExt_T params = {0};

  int rc = qzGetDefaultsDeflateExt(&params);
  if (rc != QZ_OK) {
    return rc;
  }

  // Configure parameters with validation
  params.deflate_params.common_params.comp_lvl =
      (level < 1 || level > 9) ? 1 : level;
  params.deflate_params.common_params.sw_backup = sw_backup ? 1 : 0;
  params.deflate_params.common_params.polling_mode =
      (polling_mode == 0) ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;
  params.zlib_format = 1;
  params.stop_decompression_stream_end = 1;

  return qzSetupSessionDeflateExt(qz_session, &params);
}

/**
 * Sets up an LZ4 compression session with specified parameters.
 *
 * @param qz_session    Pointer to the QzSession_T structure to configure
 * @param level         Compression level (valid range depends on LZ4
 * implementation)
 * @param sw_backup     Software backup flag (0 or 1)
 * @param polling_mode  Polling mode (0 for busy, non-zero for periodical)
 * @return              QZ_OK on success, error code on failure
 */
static int setup_lz4_session(QzSession_T *qz_session,
                             int level,
                             unsigned char sw_backup,
                             int polling_mode) {
  QzSessionParamsLZ4_T params = {0};

  int rc = qzGetDefaultsLZ4(&params);
  if (rc != QZ_OK) {
    return rc;
  }

  // Configure parameters with validation
  params.common_params.comp_lvl = level;
  params.common_params.sw_backup = sw_backup ? 1 : 0;
  params.common_params.polling_mode =
      (polling_mode == 0) ? QZ_BUSY_POLLING : QZ_PERIODICAL_POLLING;

  return qzSetupSessionLZ4(qz_session, &params);
}

/**
 * Compresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env a pointer to the JNI environment.
 * @param sess a pointer to the QzSession_T object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param bytes_read an out parameter that stores the bytes read from the
 * source buffer.
 * @param bytes_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of compression retries before we give up.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static int compress(JNIEnv *env,
                    QzSession_T *sess,
                    unsigned char *src_ptr,
                    unsigned int src_len,
                    unsigned char *dst_ptr,
                    unsigned int dst_len,
                    int *bytes_read,
                    int *bytes_written,
                    int retry_count) {
  unsigned int src_len_remain = src_len;
  unsigned int dst_len_remain = dst_len;
  int rc =
      qzCompress(sess, src_ptr, &src_len_remain, dst_ptr, &dst_len_remain, 1);

  // Retry on specific error if retries remain
  while (unlikely(rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0)) {
    src_len_remain = src_len;
    dst_len_remain = dst_len;
    rc =
        qzCompress(sess, src_ptr, &src_len_remain, dst_ptr, &dst_len_remain, 1);
    retry_count--;
  }

  if (rc != QZ_OK) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     get_err_str(rc));
    return rc;
  }

  *bytes_read = src_len_remain;
  *bytes_written = dst_len_remain;

  return QZ_OK;
}

/**
 * Compresses data using a QzSession_T session.
 *
 * @param env           JNI environment pointer
 * @param sess          Pointer to the QzSession_T structure
 * @param src_ptr       Pointer to source data buffer
 * @param src_len       Length of source data
 * @param dst_ptr       Pointer to destination buffer
 * @param dst_len       Length of destination buffer
 * @param bytes_read    Pointer to store number of bytes read from source
 * @param bytes_written Pointer to store number of bytes written to destination
 * @param retry_count   Number of retry attempts for specific errors
 * @return              QZ_OK on success, error code on failure
 */
static int decompress(JNIEnv *env,
                      QzSession_T *sess,
                      unsigned char *src_ptr,
                      unsigned int src_len,
                      unsigned char *dst_ptr,
                      unsigned int dst_len,
                      int *bytes_read,
                      int *bytes_written,
                      int retry_count) {
  unsigned int src_len_remain = src_len;
  unsigned int dst_len_remain = dst_len;
  int rc =
      qzDecompress(sess, src_ptr, &src_len_remain, dst_ptr, &dst_len_remain);

  // Retry on specific error if retries remain
  while (unlikely(rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0)) {
    src_len_remain = src_len;
    dst_len_remain = dst_len;
    rc = qzDecompress(sess, src_ptr, &src_len_remain, dst_ptr, &dst_len_remain);
    retry_count--;
  }

  *bytes_read = src_len_remain;
  *bytes_written = dst_len_remain;

  if (rc == QZ_OK || rc == QZ_BUF_ERROR || rc == QZ_DATA_ERROR) {
    // TODO: implement a better solution!
    // The streaming API requires that we allow BUF_ERROR and DATA_ERROR to proceed.
    // Caller needs to check bytes_read and bytes_written.
    return QZ_OK;
  } else {
    (*env)->ThrowNew(
        env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
        get_err_str(rc));
    return rc;
  }
}

/**
 * Initializes JNI field IDs for accessing Java class fields.
 *
 * @param env  JNI environment pointer
 * @param clz  Java class object (unused)
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_initFieldIDs(JNIEnv *env,
                                                                   jclass clz) {
  (void)clz;
  if (!env) {
    return;
  }

  jclass byte_buffer_class = (*env)->FindClass(env, "java/nio/ByteBuffer");
  g_nio_bytebuffer_position_id =
      (*env)->GetFieldID(env, byte_buffer_class, "position", "I");

  jclass qat_zipper_class = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  g_qzip_bytes_read_id =
      (*env)->GetFieldID(env, qat_zipper_class, "bytesRead", "I");
}

/**
 * Initializes JNI field IDs for accessing Java class fields.
 * Stores field IDs globally for efficient access to ByteBuffer.position and
 * QatZipper.bytesRead fields.
 *
 * @param env JNI environment pointer. Must not be NULL.
 * @param clz Java class object (unused).
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env,
                                                            jclass clz,
                                                            jobject qat_zipper,
                                                            jint comp_algorithm,
                                                            jint level,
                                                            jint sw_backup,
                                                            jint polling_mode,
                                                            jint data_format,
                                                            jint hw_buff_sz) {
  (void)clz;

  // Validate inputs
  if (level < 1 || level > COMP_LVL_MAXIMUM || sw_backup < 0 || sw_backup > 1 ||
      hw_buff_sz < 0) {
    (*env)->ThrowNew(
        env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
        "Invalid compression level");
    return QZ_FAIL;
  }

  // Handle ZSTD algorithm
  if (comp_algorithm == ZSTD_ALGORITHM) {
    call_once(&g_init_qzstd_flag, initialize_qzstd_once);
    if (g_zstd_is_device_available == -1) {
      if (sw_backup == 0) {
        (*env)->ThrowNew(
            env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
            "Initializing QAT failed");
        return QZ_FAIL;
      }
      return QZ_NO_HW;
    }
    g_algorithm_is_zstd = 1;
    return QZ_OK;
  }

  // Initialize session
  int rc = QZ_OK;
  int key = generate_key_for_session(comp_algorithm, level, sw_backup,
                                     polling_mode, data_format, hw_buff_sz);
  Session_T *session_ptr = get_cached_session(key);

  if (!session_ptr || !session_ptr->qz_session) {
    if (g_session_counter == MAX_SESSIONS_PER_THREAD) {
      (*env)->ThrowNew(
          env, (*env)->FindClass(env, "java/nio/BufferOverflowException"),
          "Number of sessions exceeded the limit");
      return QZ_FAIL;
    }

    session_ptr = &g_session_cache[g_session_counter++];
    session_ptr->key = key;
    session_ptr->qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

    rc = qzInit(session_ptr->qz_session, (unsigned char)sw_backup);
    if (rc != QZ_OK && rc != QZ_DUPLICATE) {
      free(session_ptr->qz_session);
      session_ptr->qz_session = NULL;
      (*env)->ThrowNew(
          env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
          "Initializing QAT failed");
      return rc;
    }

    if (comp_algorithm == DEFLATE_ALGORITHM) {
      rc = (data_format != ZLIB_DATA_FORMAT)
               ? setup_deflate_session(session_ptr->qz_session, level,
                                       (unsigned char)sw_backup, polling_mode,
                                       data_format, hw_buff_sz)
               : setup_deflate_zlib_session(session_ptr->qz_session, level,
                                            (unsigned char)sw_backup,
                                            polling_mode);
    } else {
      rc = setup_lz4_session(session_ptr->qz_session, level,
                             (unsigned char)sw_backup, polling_mode);
    }

    if (rc != QZ_OK) {
      qzClose(session_ptr->qz_session);
      free(session_ptr->qz_session);
      session_ptr->qz_session = NULL;
      session_ptr->key = 0;
      (*env)->ThrowNew(
          env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
          "QAT session setup failed");
      return rc;
    }
  }
  session_ptr->reference_count++;

  jclass qz_clz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clz, "session", "J");
  (*env)->SetLongField(env, qat_zipper, qz_session_field, (jlong)session_ptr);

  return QZ_OK;
}

/**
 * Compresses a byte array using a QAT session and updates the result in a
 * destination array.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qat_zipper  QatZipper Java object for updating bytesRead
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_arr     Source byte array
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to compress
 * @param dst_arr     Destination byte array
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for compression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressByteArray(JNIEnv *env,
                                                 jclass clz,
                                                 jobject qat_zipper,
                                                 jlong sess,
                                                 jbyteArray src_arr,
                                                 jint src_pos,
                                                 jint src_len,
                                                 jbyteArray dst_arr,
                                                 jint dst_pos,
                                                 jint dst_len,
                                                 jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }
  // Perform compression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
               dst_len, &bytes_read, &bytes_written, retry_count);

  // Release arrays
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for compression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update QatZipper.bytesRead
  if (unlikely(!g_qzip_bytes_read_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "QatZipper bytesRead field ID not initialized");
    return rc;
  }
  (*env)->SetIntField(env, qat_zipper, g_qzip_bytes_read_id, (jint)bytes_read);

  return (jint)bytes_written;
}

/**
 * Decompresses a byte array using a QAT session and stores the result in a
 * destination array.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qat_zipper  QatZipper Java object for updating bytesRead
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_arr     Source byte array (compressed data)
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to decompress
 * @param dst_arr     Destination byte array (decompressed data)
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for decompression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressByteArray(JNIEnv *env,
                                                   jclass clz,
                                                   jobject qat_zipper,
                                                   jlong sess,
                                                   jbyteArray src_arr,
                                                   jint src_pos,
                                                   jint src_len,
                                                   jbyteArray dst_arr,
                                                   jint dst_pos,
                                                   jint dst_len,
                                                   jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }

  // Perform decompression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
                 dst_len, &bytes_read, &bytes_written, retry_count);

  // Release arrays
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for decompression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update QatZipper.bytesRead
  if (unlikely(!g_qzip_bytes_read_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "QatZipper bytesRead field ID not initialized");
    return rc;
  }
  (*env)->SetIntField(env, qat_zipper, g_qzip_bytes_read_id, (jint)bytes_read);

  return (jint)bytes_written;
}

/**
 * Compresses data from a byte array and updates a ByteBuffer's position.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object for updating position
 * @param src_arr     Source byte array (input data)
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to compress
 * @param dst_arr     Destination byte array (compressed data)
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for compression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressByteBuffer(JNIEnv *env,
                                                  jclass clz,
                                                  jlong sess,
                                                  jobject src_buf,
                                                  jbyteArray src_arr,
                                                  jint src_pos,
                                                  jint src_len,
                                                  jbyteArray dst_arr,
                                                  jint dst_pos,
                                                  jint dst_len,
                                                  jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }

  // Perform compression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
               dst_len, &bytes_read, &bytes_written, retry_count);

  // Release arrays
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for compression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update ByteBuffer.position
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return (jint)bytes_written;
}

/**
 * Decompresses data from a byte array and updates a ByteBuffer's position.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object for updating position
 * @param src_arr     Source byte array (compressed data)
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to decompress
 * @param dst_arr     Destination byte array (decompressed data)
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for decompression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressByteBuffer(JNIEnv *env,
                                                    jclass clz,
                                                    jlong sess,
                                                    jobject src_buf,
                                                    jbyteArray src_arr,
                                                    jint src_pos,
                                                    jint src_len,
                                                    jbyteArray dst_arr,
                                                    jint dst_pos,
                                                    jint dst_len,
                                                    jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }

  // Perform decompression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
                 dst_len, &bytes_read, &bytes_written, retry_count);

  // Release arrays
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for decompression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update ByteBuffer.position
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return (jint)bytes_written;
}

/**
 * Compresses data from a direct ByteBuffer and updates source and destination
 * ByteBuffer positions.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object (direct buffer)
 * @param src_pos     Starting position in source buffer
 * @param src_len     Length of data to compress
 * @param dst_buf     Destination ByteBuffer object (direct buffer)
 * @param dst_pos     Starting position in destination buffer
 * @param dst_len     Available length in destination buffer
 * @param retry_count Number of retry attempts for compression
 * @return            Number of bytes written to destination, or -1 on error
 */
jint JNICALL
Java_com_intel_qat_InternalJNI_compressDirectByteBuffer(JNIEnv *env,
                                                        jclass clz,
                                                        jlong sess,
                                                        jobject src_buf,
                                                        jint src_pos,
                                                        jint src_len,
                                                        jobject dst_buf,
                                                        jint dst_pos,
                                                        jint dst_len,
                                                        jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access direct buffers
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);
  if (unlikely(!dst_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination buffer address");
    return -1;
  }

  // Perform compression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
               dst_len, &bytes_read, &bytes_written, retry_count);

  // Check for compression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update ByteBuffer positions
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, g_nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return (jint)bytes_written;
}

/**
 * Decompresses data from a direct ByteBuffer and updates source and destination
 * ByteBuffer positions.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object (direct buffer, compressed data)
 * @param src_pos     Starting position in source buffer
 * @param src_len     Length of data to decompress
 * @param dst_buf     Destination ByteBuffer object (direct buffer, decompressed
 * data)
 * @param dst_pos     Starting position in destination buffer
 * @param dst_len     Available length in destination buffer
 * @param retry_count Number of retry attempts for decompression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBuffer(JNIEnv *env,
                                                          jclass clz,
                                                          jlong sess,
                                                          jobject src_buf,
                                                          jint src_pos,
                                                          jint src_len,
                                                          jobject dst_buf,
                                                          jint dst_pos,
                                                          jint dst_len,
                                                          jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access direct buffers
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);
  if (unlikely(!dst_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination buffer address");
    return -1;
  }

  // Perform decompression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
                 dst_len, &bytes_read, &bytes_written, retry_count);

  // Check for decompression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update ByteBuffer positions
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, g_nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return (jint)bytes_written;
}

/**
 * Compresses data from a direct ByteBuffer to a byte array and updates the
 * source ByteBuffer position.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object (direct buffer)
 * @param src_pos     Starting position in source buffer
 * @param src_len     Length of data to compress
 * @param dst_arr     Destination byte array (compressed data)
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for compression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressDirectByteBufferSrc(JNIEnv *env,
                                                           jclass clz,
                                                           jlong sess,
                                                           jobject src_buf,
                                                           jint src_pos,
                                                           jint src_len,
                                                           jbyteArray dst_arr,
                                                           jint dst_pos,
                                                           jint dst_len,
                                                           jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source buffer and destination array
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }

  // Perform compression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
               dst_len, &bytes_read, &bytes_written, retry_count);

  // Release destination array
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);

  // Check for compression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update source ByteBuffer position
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return (jint)bytes_written;
}

/**
 * Decompresses data from a direct ByteBuffer to a byte array and updates the
 * source ByteBuffer position.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object (direct buffer, compressed data)
 * @param src_pos     Starting position in source buffer
 * @param src_len     Length of data to decompress
 * @param dst_arr     Destination byte array (decompressed data)
 * @param dst_pos     Starting position in destination array
 * @param dst_len     Available length in destination array
 * @param retry_count Number of retry attempts for decompression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBufferSrc(JNIEnv *env,
                                                             jclass clz,
                                                             jlong sess,
                                                             jobject src_buf,
                                                             jint src_pos,
                                                             jint src_len,
                                                             jbyteArray dst_arr,
                                                             jint dst_pos,
                                                             jint dst_len,
                                                             jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source buffer and destination array
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
  if (unlikely(!dst_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination array");
    return -1;
  }

  // Perform decompression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
                 dst_len, &bytes_read, &bytes_written, retry_count);

  // Release destination array
  (*env)->ReleasePrimitiveArrayCritical(env, dst_arr, (jbyte *)dst_ptr, 0);

  // Check for decompression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update source ByteBuffer position
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return (jint)bytes_written;
}

/**
 * Compresses data from a byte array to a direct ByteBuffer and updates source
 * and destination ByteBuffer positions.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object for updating position
 * @param src_arr     Source byte array (input data)
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to compress
 * @param dst_buf     Destination ByteBuffer object (direct buffer, compressed
 * data)
 * @param dst_pos     Starting position in destination buffer
 * @param dst_len     Available length in destination buffer
 * @param retry_count Number of retry attempts for compression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_compressDirectByteBufferDst(JNIEnv *env,
                                                           jclass clz,
                                                           jlong sess,
                                                           jobject src_buf,
                                                           jbyteArray src_arr,
                                                           jint src_pos,
                                                           jint src_len,
                                                           jobject dst_buf,
                                                           jint dst_pos,
                                                           jint dst_len,
                                                           jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source array and destination buffer
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination buffer address");
    return -1;
  }

  // Perform compression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
               dst_len, &bytes_read, &bytes_written, retry_count);

  // Release source array
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for compression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update source and destination ByteBuffer positions
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, g_nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return (jint)bytes_written;
}

/**
 * Decompresses data from a byte array to a direct ByteBuffer and updates source
 * and destination ByteBuffer positions.
 *
 * @param env          JNI environment pointer
 * @param clz         Java class object (unused)
 * @param sess        Pointer to Session_T containing QzSession_T
 * @param src_buf     Source ByteBuffer object for updating position
 * @param src_arr     Source byte array (compressed data)
 * @param src_pos     Starting position in source array
 * @param src_len     Length of data to decompress
 * @param dst_buf     Destination ByteBuffer object (direct buffer, decompressed
 * data)
 * @param dst_pos     Starting position in destination buffer
 * @param dst_len     Available length in destination buffer
 * @param retry_count Number of retry attempts for decompression
 * @return            Number of bytes written to destination, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_decompressDirectByteBufferDst(JNIEnv *env,
                                                             jclass clz,
                                                             jlong sess,
                                                             jobject src_buf,
                                                             jbyteArray src_arr,
                                                             jint src_pos,
                                                             jint src_len,
                                                             jobject dst_buf,
                                                             jint dst_pos,
                                                             jint dst_len,
                                                             jint retry_count) {
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source array and destination buffer
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);
  if (unlikely(!dst_ptr)) {
    (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr,
                                          JNI_ABORT);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access destination buffer address");
    return -1;
  }

  // Perform decompression
  int bytes_read = 0;
  int bytes_written = 0;
  int rc =
      decompress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
                 dst_len, &bytes_read, &bytes_written, retry_count);

  // Release source array
  (*env)->ReleasePrimitiveArrayCritical(env, src_arr, (jbyte *)src_ptr, 0);

  // Check for decompression error
  if (rc != QZ_OK) {
    return rc;
  }

  // Update source and destination ByteBuffer positions
  if (unlikely(!g_nio_bytebuffer_position_id)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "ByteBuffer position field ID not initialized");
    return -1;
  }
  (*env)->SetIntField(env, src_buf, g_nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, g_nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return (jint)bytes_written;
}

/**
 * Calculates the maximum possible compressed size for a given source size.
 *
 * @param env      JNI environment pointer (unused)
 * @param clz     Java class object (unused)
 * @param sess    Pointer to Session_T containing QzSession_T
 * @param src_size Size of the source data
 * @return        Maximum compressed size, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_maxCompressedSize(JNIEnv *env,
                                                 jclass clz,
                                                 jlong sess,
                                                 jlong src_size) {
  (void)env;
  (void)clz;

  QzSession_T *qz_session = ((Session_T *)sess)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  return qzMaxCompressedLength(src_size, ((Session_T *)sess)->qz_session);
}

/**
 * Returns the address of the QAT sequence producer function for Zstandard
 * compression.
 *
 * @param env  JNI environment pointer (unused)
 * @param clz Java class object (unused)
 * @return    Address of qatSequenceProducer as a jlong, or 0 on error
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdGetSeqProdFunction(JNIEnv *env, jclass clz) {
  (void)env;
  (void)clz;

  return (jlong)qatSequenceProducer;
}

/**
 * Creates a Zstandard sequence producer state for QAT compression.
 *
 * @param env  JNI environment pointer (unused)
 * @param clz Java class object (unused)
 * @return    Pointer to the created QZSTD sequence producer state as a jlong,
 *            or 0 on error
 */
JNIEXPORT jlong JNICALL
Java_com_intel_qat_InternalJNI_zstdCreateSeqProdState(JNIEnv *env, jclass clz) {
  (void)env;
  (void)clz;

  if (unlikely(g_zstd_is_device_available == -1)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "QAT device not available");
    return 0;
  }

  // Create sequence producer state
  g_zstd_seqprod_state = QZSTD_createSeqProdState();
  if (unlikely(!g_zstd_seqprod_state)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Failed to create Zstandard sequence producer state");
    return 0;
  }

  return (jlong)g_zstd_seqprod_state;
}

/**
 * Frees a Zstandard sequence producer state for QAT compression.
 *
 * @param env           JNI environment pointer (unused)
 * @param clz          Java class object (unused)
 * @param seqprod_state Pointer to the QZSTD sequence producer state to free
 */
JNIEXPORT void JNICALL
Java_com_intel_qat_InternalJNI_zstdFreeSeqProdState(JNIEnv *env,
                                                    jclass clz,
                                                    jlong seqprod_state) {
  (void)env;
  (void)clz;
  if (unlikely(seqprod_state == 0)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid Zstandard sequence producer state");
    return;
  }

  // Check if device is available
  if (unlikely(g_zstd_is_device_available == -1)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "QAT device not available");
    return;
  }

  // Free the sequence producer state
  QZSTD_freeSeqProdState((void *)seqprod_state);

  // Reset global state if it matches the freed state
  if ((void *)seqprod_state == g_zstd_seqprod_state) {
    g_zstd_seqprod_state = NULL;
  }
}

/**
 * Tears down a QAT session associated with the given session pointer.
 * Decrements the reference count and only destroys the session if no references
 * remain. Updates the thread-local session cache counter if the session is
 * fully torn down.
 *
 * @param env      JNI environment pointer. Must not be NULL.
 * @param clz      Java class object (unused).
 * @param sess     Pointer to a Session_T struct, cast to jlong.
 * @return QZ_OK on success, or 0 if an error occurs (with a Java exception
 * thrown).
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                               jclass clz,
                                                               jlong sess) {
  (void)clz;

  // Validate session pointer
  Session_T *session_ptr = (Session_T *)sess;
  if (!session_ptr || !session_ptr->qz_session) {
    return QZ_OK;
  }

  // Decrement reference count
  if (session_ptr->reference_count > 1) {
    session_ptr->reference_count--;
    return QZ_OK;
  }

  // Last reference: tear down the QAT session
  int rc = qzTeardownSession(session_ptr->qz_session);
  if (rc != QZ_OK) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Failed to tear down QAT session");
    return 0;
  }

  // Clean up session
  free(session_ptr->qz_session);
  session_ptr->qz_session = NULL;
  session_ptr->reference_count = 0;
  session_ptr->key = 0;

  // Update session cache counter
  if (g_session_counter > 0) {
    g_session_counter--;
  }

  return QZ_OK;
}

/**
 * Cleans up QAT ZSTD resources when the JNI library is unloaded.
 * Frees the ZSTD sequence producer state and stops the QAT device if
 * initialized.
 *
 * @param vm       JavaVM pointer (unused).
 * @param reserved Reserved parameter (unused).
 */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
  (void)vm;
  (void)reserved;

  if (g_algorithm_is_zstd && g_zstd_is_device_available != -1) {
    if (g_zstd_seqprod_state) {
      QZSTD_freeSeqProdState(g_zstd_seqprod_state);
      g_zstd_seqprod_state = NULL;
    }
    QZSTD_stopQatDevice();
    g_zstd_is_device_available = -1;  // Mark device as stopped
    g_algorithm_is_zstd = 0;          // Reset ZSTD flag
  }
}
