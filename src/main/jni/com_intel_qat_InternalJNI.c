/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_InternalJNI.h"

#include <pthread.h>
#include <qatzip.h>
#include <stdint.h>
#include <stdlib.h>
#include <zstd.h>

#include "qatseqprod.h"
#include "util.h"

#define likely(x) __builtin_expect((x), 1)
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

/*  QATzip log API
 *  could change the log level on runtime
 */
extern void logMessage(QzLogLevel_T level,
                       const char *file,
                       int line,
                       const char *format,
                       ...);
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
static pthread_mutex_t g_init_qzstd_mtx = PTHREAD_MUTEX_INITIALIZER;
static int g_zstd_is_device_available = QZSTD_FAIL;
static void call_qzstd_once(void) {
  pthread_mutex_lock(&g_init_qzstd_mtx);
  if (g_zstd_is_device_available != QZSTD_OK) {
    g_zstd_is_device_available = QZSTD_startQatDevice();
  }
  pthread_mutex_unlock(&g_init_qzstd_mtx);
}

/**
 * Represents a unique QAT session for specific compression parameters.
 * This structure is used to manage a QAT session with associated metadata.
 */
typedef struct QzSessionHandle_T {
  int32_t qz_key;          /**< Unique identifier for session parameters */
  int32_t reference_count; /**< Number of active references to this session */
  QzSession_T *qz_session; /**< Pointer to the QAT session object */
} QzSessionHandle_T;

/**
 * Defines the maximum number of unique QAT sessions allowed per thread.
 * This limit prevents excessive memory usage and ensures efficient session
 * caching for thread-local session storage. It is highly unusual for an
 * application to instantiate more than one unique combination of compression
 * configuration parameters per thread -- e.g. having two instances that
 * differ only by their compression level (within a single-thread). 32 is more
 * than enough.
 */
#define MAX_SESSIONS_PER_THREAD 32

/**
 * Thread-local cache of QAT session objects, indexed by unique session keys.
 * Stores up to MAX_SESSIONS_PER_THREAD active sessions per thread to optimize
 * session reuse for distinct compression configurations. Each session holds a
 * QzSessionHandle_T struct with a QAT session handle and metadata.
 */
static _Thread_local QzSessionHandle_T g_session_cache[MAX_SESSIONS_PER_THREAD];

/**
 * Thread-local counter tracking the number of active QAT sessions in
 * g_session_cache. Incremented when a new session is created and decremented
 * when a session is torn down. Must not exceed MAX_SESSIONS_PER_THREAD to
 * prevent cache overflow.
 */
static _Thread_local int g_session_counter;

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
static int32_t gen_session_key(int32_t algorithm,
                               int32_t level,
                               int32_t sw_backup,
                               int32_t polling_mode,
                               int32_t data_format,
                               int32_t hw_buff_sz) {
  int32_t qz_key = 0;

  // Bit-field allocation: 4 bits each for algorithm, level, sw_backup,
  // polling_mode, data_format; 12 bits for hw_buff_sz (supports up to 4MB)
  qz_key |= (algorithm & 0xF);
  qz_key |= (level & 0xF) << 4;
  qz_key |= (sw_backup & 0x1) << 8;
  qz_key |= (polling_mode & 0xF) << 9;
  qz_key |= (data_format & 0xF) << 13;
  qz_key |= ((hw_buff_sz >> 10) & 0xFFF) << 17;

  return qz_key;
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
                                 uint8_t sw_backup,
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
                                      uint8_t sw_backup,
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
                             uint8_t sw_backup,
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
 * Creates and initializes a new QAT session based on the provided
 * configuration key. The session is stored in a global cache, and the function
 * handles initialization and setup for either DEFLATE or LZ4 compression
 * algorithms, depending on the configuration.
 *
 * Parameters:
 *   env           - JNI environment pointer for Java interaction.
 *   qz_key        - Configuration key encoding the following:
 *                   - Compression algorithm (bits 0-3)
 *                   - Compression level (bits 4-7)
 *                   - Software backup flag (bit 8)
 *                   - Polling mode (bits 9-12)
 *                   - Data format (bits 13-16)
 *                   - Hardware buffer size (bits 17-29, shifted left by 10)
 *
 * Returns:
 *   A pointer to the initialized QzSessionHandle_T session from the global
 * cache, or NULL if an error occurs (e.g., session limit reached,
 * initialization failure, or setup failure).
 *
 * Throws:
 *   - java.lang.RuntimeException: If the number of sessions exceeds the
 * thread's limit (MAX_SESSIONS_PER_THREAD).
 *   - java.lang.IllegalStateException: If QAT initialization or session setup
 * fails.
 */
static QzSessionHandle_T *create_session(JNIEnv *env, int32_t qz_key) {
  if (g_session_counter == MAX_SESSIONS_PER_THREAD) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"),
                     "Number of sessions exceeded the limit for this thread");
    return NULL;
  }

  int comp_algo = qz_key & 0xF;
  int level = (qz_key >> 4) & 0xF;
  uint8_t sw_backup = (qz_key >> 8) & 0x1;
  int polling_mode = (qz_key >> 9) & 0xF;
  int data_format = (qz_key >> 13) & 0xF;
  int hw_buff_sz = ((qz_key >> 17) & 0xFFF) << 10;

  QzSessionHandle_T *sess_ptr = &g_session_cache[g_session_counter++];
  sess_ptr->qz_key = qz_key;
  sess_ptr->qz_session = (QzSession_T *)calloc(1, sizeof(QzSession_T));

  if (sess_ptr->qz_session == NULL) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to allocate session memory");
    return NULL;
  }

  // Initialize a new QAT session.
  int rc = qzInit(sess_ptr->qz_session, (uint8_t)sw_backup);
  if (rc != QZ_OK && rc != QZ_DUPLICATE) {
    qzTeardownSession(sess_ptr->qz_session);
    free(sess_ptr->qz_session);
    sess_ptr->qz_session = NULL;
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Initializing QAT failed");
    return NULL;
  }

  if (comp_algo == DEFLATE_ALGORITHM) {
    rc = (data_format != ZLIB_DATA_FORMAT)
             ? setup_deflate_session(sess_ptr->qz_session, level,
                                     (uint8_t)sw_backup, polling_mode,
                                     data_format, hw_buff_sz)
             : setup_deflate_zlib_session(sess_ptr->qz_session, level,
                                          (uint8_t)sw_backup, polling_mode);
  } else {
    rc = setup_lz4_session(sess_ptr->qz_session, level, (uint8_t)sw_backup,
                           polling_mode);
  }

  if (rc != QZ_OK) {
    qzTeardownSession(sess_ptr->qz_session);
    free(sess_ptr->qz_session);
    sess_ptr->qz_session = NULL;
    sess_ptr->qz_key = 0;
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "QAT session setup failed");
    return NULL;
  }

  return sess_ptr;
}

/**
 * Retrieves a cached QAT session matching the specified key from the
 * thread-local session cache. Searches the g_session_cache array up to
 * g_session_cache_counter for a session with a matching key. Returns a pointer
 * to the found QzSessionHandle_T or NULL if no match is found.
 *
 * @param qz_key  The unique session key generated from compression parameters
 * (e.g., algorithm, level).
 * @return        A pointer to the matching QzSessionHandle_T in
 * g_session_cache, or NULL if no session matches.
 */
static QzSessionHandle_T *get_session(int32_t qz_key) {
  for (int i = 0; i < g_session_counter; ++i) {
    if (g_session_cache[i].qz_session && g_session_cache[i].qz_key == qz_key) {
      return &g_session_cache[i];
    }
  }
  return NULL;
}

/*
 * Retrieves or creates a session based on the provided key.
 * Iterates through the global thread-local session cache to find an existing
 * session matching the key. If found, returns the cached session. Otherwise,
 * creates a a new session.
 *
 * Returns a pointer to the session or NULL on failure.
 */
static QzSessionHandle_T *get_or_create_session(JNIEnv *env, int32_t qz_key) {
  QzSessionHandle_T *sess_ptr = get_session(qz_key);
  if (sess_ptr) {
    return sess_ptr;
  }

  // Create a new session and update the reference count
  sess_ptr = create_session(env, qz_key);

  if (sess_ptr) {
    sess_ptr->reference_count++;
  }

  return sess_ptr;
}

/**
 * Compresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env            A pointer to the JNI environment.
 * @param qz_key         Value representing unique compression params.
 * @param src_ptr        The source buffer.
 * @param src_len        The size of the source buffer.
 * @param dst_ptr        The destination buffer.
 * @param dst_len        The size of the destination buffer.
 * @param bytes_read     An out parameter that stores the bytes read from the
 * source buffer.
 * @param bytes_written  An out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count    The number of compression retries before we give up.
 * @return               QZ_OK (0) if successful, non-zero otherwise.
 */
static int compress_slowpath(JNIEnv *env,
                             QzSession_T *sess,
                             uint8_t *src_ptr,
                             unsigned int src_len,
                             uint8_t *dst_ptr,
                             unsigned int dst_len,
                             int *bytes_read,
                             int *bytes_written,
                             int retry_count) {
  int rc = QZ_OK;

  // Retry on specific error if retries remain
  while (rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    rc = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);
    retry_count--;
  }

  if (rc != QZ_OK) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     get_err_str(rc));
    return rc;
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;

  return QZ_OK;
}

/**
 * Compresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env            A pointer to the JNI environment.
 * @param qz_key         Value representing unique compression params.
 * @param src_ptr        The source buffer.
 * @param src_len        The size of the source buffer.
 * @param dst_ptr        The destination buffer.
 * @param dst_len        The size of the destination buffer.
 * @param bytes_read     An out parameter that stores the bytes read from the
 * source buffer.
 * @param bytes_written  An out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count    The number of compression retries before we give up.
 * @return               QZ_OK (0) if successful, non-zero otherwise.
 */
static inline __attribute__((always_inline)) int compress(JNIEnv *env,
                                                          QzSession_T *sess,
                                                          uint8_t *src_ptr,
                                                          unsigned int src_len,
                                                          uint8_t *dst_ptr,
                                                          unsigned int dst_len,
                                                          int *bytes_read,
                                                          int *bytes_written,
                                                          int retry_count) {
  int rc = qzCompress(sess, src_ptr, &src_len, dst_ptr, &dst_len, 1);

  if (likely(rc == QZ_OK)) {
    *bytes_read = src_len;
    *bytes_written = dst_len;
    return QZ_OK;
  }

  return compress_slowpath(env, sess, src_ptr, src_len, dst_ptr, dst_len,
                           bytes_read, bytes_written, retry_count);
}

/**
 * Compresses data using a QzSession_T session.
 *
 * @param env           JNI environment pointer
 * @param qz_key        Value representing unique compression params
 * @param src_ptr       Pointer to source data buffer
 * @param src_len       Length of source data
 * @param dst_ptr       Pointer to destination buffer
 * @param dst_len       Length of destination buffer
 * @param bytes_read    Pointer to store number of bytes read from source
 * @param bytes_written Pointer to store number of bytes written to destination
 * @param retry_count   Number of retry attempts for specific errors
 * @return              QZ_OK on success, error code on failure
 */
static int decompress_slowpath(JNIEnv *env,
                               QzSession_T *sess,
                               uint8_t *src_ptr,
                               unsigned int src_len,
                               uint8_t *dst_ptr,
                               unsigned int dst_len,
                               int *bytes_read,
                               int *bytes_written,
                               int retry_count) {
  int rc = QZ_OK;

  // Retry on specific error if retries remain
  while (rc == QZ_NOSW_NO_INST_ATTACH && retry_count > 0) {
    rc = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);
    retry_count--;
  }

  if (rc == QZ_OK || rc == QZ_BUF_ERROR || rc == QZ_DATA_ERROR) {
    // TODO: implement a better solution!
    // The streaming API requires that we allow BUF_ERROR and DATA_ERROR to
    // proceed. Caller needs to check bytes_read and bytes_written.
    *bytes_read = src_len;
    *bytes_written = dst_len;
    return QZ_OK;
  } else {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     get_err_str(rc));
    return rc;
  }
}

/**
 * Compresses data using a QzSession_T session.
 *
 * @param env           JNI environment pointer
 * @param qz_key        Value representing unique compression params
 * @param src_ptr       Pointer to source data buffer
 * @param src_len       Length of source data
 * @param dst_ptr       Pointer to destination buffer
 * @param dst_len       Length of destination buffer
 * @param bytes_read    Pointer to store number of bytes read from source
 * @param bytes_written Pointer to store number of bytes written to destination
 * @param retry_count   Number of retry attempts for specific errors
 * @return              QZ_OK on success, error code on failure
 */
static inline __attribute__((always_inline)) int decompress(
    JNIEnv *env,
    QzSession_T *sess,
    uint8_t *src_ptr,
    unsigned int src_len,
    uint8_t *dst_ptr,
    unsigned int dst_len,
    int *bytes_read,
    int *bytes_written,
    int retry_count) {
  
  int rc = qzDecompress(sess, src_ptr, &src_len, dst_ptr, &dst_len);

  if (likely(rc == QZ_OK)) {
    *bytes_read = src_len;
    *bytes_written = dst_len;
    return QZ_OK;
  }

  return decompress_slowpath(env, sess, src_ptr, src_len, dst_ptr, dst_len,
                             bytes_read, bytes_written, retry_count);
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

  jclass byte_buffer_class = (*env)->FindClass(env, "java/nio/ByteBuffer");
  g_nio_bytebuffer_position_id =
      (*env)->GetFieldID(env, byte_buffer_class, "position", "I");

  jclass qz_obj_class = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  g_qzip_bytes_read_id =
      (*env)->GetFieldID(env, qz_obj_class, "bytesRead", "I");
}

/**
 * JNI function to set up a QAT compression session.
 * Configures compression parameters, validates inputs, and initializes a
 * session for the specified algorithm.
 *
 * @param env             JNI environment pointer. Must not be NULL.
 * @param clz             Java class object (unused).
 * @param qz_obj          QatZipper Java object to store session key.
 * @param comp_algo       Compression algorithm identifier (e.g.,
 * ZSTD_ALGORITHM).
 * @param level           Compression level (1 to COMP_LVL_MAXIMUM).
 * @param sw_backup       Flag to enable (1) or disable (0) software fallback.
 * @param polling_mode    Polling mode for QAT hardware.
 * @param data_format     Data format for compression.
 * @param hw_buff_sz      Hardware buffer size.
 * @return                QZ_OK on success, QZ_FAIL on invalid input, QZ_NO_HW
 * if hardware is
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_setup(JNIEnv *env,
                                                            jclass clz,
                                                            jobject qz_obj,
                                                            jint comp_algo,
                                                            jint level,
                                                            jint sw_backup,
                                                            jint polling_mode,
                                                            jint data_format,
                                                            jint hw_buff_sz,
                                                            jint log_level) {
  (void)clz;

  // Validate inputs
  if (level < 1 || level > COMP_LVL_MAXIMUM || sw_backup < 0 || sw_backup > 1 ||
      hw_buff_sz < 0) {
    (*env)->ThrowNew(
        env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"),
        "Invalid compression level");
    return QZ_FAIL;
  }

  // Set Log level
  qzSetLogLevel(log_level);

  // Handle ZSTD algorithm
  if (comp_algo == ZSTD_ALGORITHM) {
    call_qzstd_once();
    if (g_zstd_is_device_available != QZSTD_OK) {
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

  int qz_key = gen_session_key(comp_algo, level, sw_backup, polling_mode,
                               data_format, hw_buff_sz);

  QzSessionHandle_T *sess_ptr = get_session(qz_key);

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(LOG_DEBUG1, __FILE__, __LINE__,
             sess_ptr ? "re-using a session, id is %#x\n"
                      : "creating a new session, id is %#x\n",
             qz_key);
  if (!sess_ptr) {
    sess_ptr = create_session(env, qz_key);
  }


  if ((*env)->ExceptionCheck(env)) {
    (*env)->ThrowNew(
        env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
        "Initializing QAT failed");
    return QZ_FAIL;
  }

  // Update the reference count
  sess_ptr->reference_count++;

  jclass qz_clz = (*env)->FindClass(env, "com/intel/qat/QatZipper");
  jfieldID qz_session_field = (*env)->GetFieldID(env, qz_clz, "qzKey", "I");
  (*env)->SetLongField(env, qz_obj, qz_session_field, (jint)sess_ptr->qz_key);

  return QZ_OK;
}

/**
 * Compresses a byte array using a QAT session and updates the result in a
 * destination array.
 *
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_obj      QatZipper Java object for updating bytesRead
 * @param qz_key      Value representing unique compression params
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
                                                 jobject qz_obj,
                                                 jint qz_key,
                                                 jbyteArray src_arr,
                                                 jint src_pos,
                                                 jint src_len,
                                                 jbyteArray dst_arr,
                                                 jint dst_pos,
                                                 jint dst_len,
                                                 jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "compressByteArray: src_pos = %d, src_len = %d, dst_pos = %d, dst_len = "
      "%d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
  (*env)->SetIntField(env, qz_obj, g_qzip_bytes_read_id, (jint)bytes_read);

  return (jint)bytes_written;
}

/**
 * Decompresses a byte array using a QAT session and stores the result in a
 * destination array.
 *
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_obj      QatZipper Java object for updating bytesRead
 * @param qz_key      Value representing unique compression params
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
                                                   jobject qz_obj,
                                                   jint qz_key,
                                                   jbyteArray src_arr,
                                                   jint src_pos,
                                                   jint src_len,
                                                   jbyteArray dst_arr,
                                                   jint dst_pos,
                                                   jint dst_len,
                                                   jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "decompressByteArray: src_pos = %d, src_len = %d, dst_pos = %d, dst_len "
      "= %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
  (*env)->SetIntField(env, qz_obj, g_qzip_bytes_read_id, (jint)bytes_read);

  return (jint)bytes_written;
}

/**
 * Compresses data from a byte array and updates a ByteBuffer's position.
 *
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                  jint qz_key,
                                                  jobject src_buf,
                                                  jbyteArray src_arr,
                                                  jint src_pos,
                                                  jint src_len,
                                                  jbyteArray dst_arr,
                                                  jint dst_pos,
                                                  jint dst_len,
                                                  jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "compressByteBuffer: src_pos = %d, src_len = %d, dst_pos = %d, dst_len = "
      "%d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                    jint qz_key,
                                                    jobject src_buf,
                                                    jbyteArray src_arr,
                                                    jint src_pos,
                                                    jint src_len,
                                                    jbyteArray dst_arr,
                                                    jint dst_pos,
                                                    jint dst_len,
                                                    jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "decompressByteBuffer: src_pos = %d, src_len = %d, dst_pos = %d, dst_len "
      "= %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access arrays with critical regions
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                        jint qz_key,
                                                        jobject src_buf,
                                                        jint src_pos,
                                                        jint src_len,
                                                        jobject dst_buf,
                                                        jint dst_pos,
                                                        jint dst_len,
                                                        jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "compressDirectByteBuffer: src_pos = %d, src_len = %d, dst_pos = %d, "
      "dst_len = %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access direct buffers
  uint8_t *src_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  uint8_t *dst_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, dst_buf);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                          jint qz_key,
                                                          jobject src_buf,
                                                          jint src_pos,
                                                          jint src_len,
                                                          jobject dst_buf,
                                                          jint dst_pos,
                                                          jint dst_len,
                                                          jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "decompressByteBuffer: src_pos = %d, src_len = %d, dst_pos = %d, dst_len "
      "= %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access direct buffers
  uint8_t *src_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  uint8_t *dst_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, dst_buf);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                           jint qz_key,
                                                           jobject src_buf,
                                                           jint src_pos,
                                                           jint src_len,
                                                           jbyteArray dst_arr,
                                                           jint dst_pos,
                                                           jint dst_len,
                                                           jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "compressDirectByteBufferSrc: src_pos = %d, src_len = %d, dst_pos = %d, "
      "dst_len = %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source buffer and destination array
  uint8_t *src_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                             jint qz_key,
                                                             jobject src_buf,
                                                             jint src_pos,
                                                             jint src_len,
                                                             jbyteArray dst_arr,
                                                             jint dst_pos,
                                                             jint dst_len,
                                                             jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "decompressDirectByteBufferSrc: src_pos = %d, src_len = %d, dst_pos = "
      "%d, dst_len = %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source buffer and destination array
  uint8_t *src_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, src_buf);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source buffer address");
    return -1;
  }

  uint8_t *dst_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, dst_arr, NULL);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                           jint qz_key,
                                                           jobject src_buf,
                                                           jbyteArray src_arr,
                                                           jint src_pos,
                                                           jint src_len,
                                                           jobject dst_buf,
                                                           jint dst_pos,
                                                           jint dst_len,
                                                           jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "compressDirectByteBufferDst: src_pos = %d, src_len = %d, dst_pos = %d, "
      "dst_len = %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source array and destination buffer
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, dst_buf);
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
 * @param env         JNI environment pointer
 * @param clz         Java class object (unused)
 * @param qz_key      Value representing unique compression params
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
                                                             jint qz_key,
                                                             jobject src_buf,
                                                             jbyteArray src_arr,
                                                             jint src_pos,
                                                             jint src_len,
                                                             jobject dst_buf,
                                                             jint dst_pos,
                                                             jint dst_len,
                                                             jint retry_count) {
  (void)clz;

  // Log message if current level is either LOG_DEBUG1,2 or 3
  logMessage(
      LOG_DEBUG1, __FILE__, __LINE__,
      "decompressDirectByteBufferDst: src_pos = %d, src_len = %d, dst_pos = "
      "%d, dst_len = %d\n",
      src_pos, src_len, dst_pos, dst_len);

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  // Access source array and destination buffer
  uint8_t *src_ptr =
      (uint8_t *)(*env)->GetPrimitiveArrayCritical(env, src_arr, NULL);
  if (unlikely(!src_ptr)) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"),
                     "Failed to access source array");
    return -1;
  }

  uint8_t *dst_ptr = (uint8_t *)(*env)->GetDirectBufferAddress(env, dst_buf);
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
 * @param env       JNI environment pointer (unused)
 * @param clz       Java class object (unused)
 * @param qz_key    Value representing unique compression params
 * @param src_size  Size of the source data
 * @return          Maximum compressed size, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_intel_qat_InternalJNI_maxCompressedSize(JNIEnv *env,
                                                 jclass clz,
                                                 jint qz_key,
                                                 jlong src_size) {
  (void)env;
  (void)clz;

  QzSession_T *qz_session = get_or_create_session(env, qz_key)->qz_session;
  if (unlikely(!qz_session)) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Invalid QAT session");
    return -1;
  }

  return qzMaxCompressedLength(src_size, qz_session);
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
 * @param clz  Java class object (unused)
 * @return     Pointer to the created QZSTD sequence producer state as a jlong,
 *             or 0 on error
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
 * @param clz           Java class object (unused)
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
 * @param qz_key   Value representing unique compression params
 * @return         QZ_OK on success, or 0 if an error occurs (with a Java
 * exception thrown).
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_teardown(JNIEnv *env,
                                                               jclass clz,
                                                               jint qz_key) {
  (void)clz;

  // Validate session pointer
  QzSessionHandle_T *sess_ptr = get_session(qz_key);
  if (!sess_ptr || !sess_ptr->qz_session) {
    return QZ_OK;
  }

  // Decrement reference count
  if (sess_ptr->reference_count > 1) {
    sess_ptr->reference_count--;
    return QZ_OK;
  }

  // Last reference: tear down the QAT session
  int rc = qzTeardownSession(sess_ptr->qz_session);
  if (rc != QZ_OK) {
    (*env)->ThrowNew(env,
                     (*env)->FindClass(env, "java/lang/IllegalStateException"),
                     "Failed to tear down QAT session");
    return 0;
  }

  // Clean up session
  if (sess_ptr->qz_session) {
    qzTeardownSession(sess_ptr->qz_session);
    free(sess_ptr->qz_session);
    sess_ptr->qz_session = NULL;
  }
  sess_ptr->reference_count = 0;
  sess_ptr->qz_key = 0;

  // Update session cache counter
  if (g_session_counter > 0) {
    g_session_counter--;
  }

  return QZ_OK;
}

/**
 * Sets the log level.
 *
 * @param env      JNI environment pointer. Must not be NULL.
 * @param clz      Java class object (unused).
 * @param log_level the log level (none|fatal|error|warning|info|debug1|debug2|debug3).
 */
JNIEXPORT void JNICALL Java_com_intel_qat_InternalJNI_setLogLevel(JNIEnv *env,
                                                                  jclass clz,
                                                                  jint log_level) {
  (void)env;
  (void)clz;
  qzSetLogLevel(log_level);
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

  JNIEnv *env = NULL;

  if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) == JNI_OK &&
      env != NULL) {
    if (g_zstd_is_device_available == QZSTD_OK) {
      QZSTD_freeSeqProdState(g_zstd_seqprod_state);
      QZSTD_stopQatDevice();
    }

    // Destroy the QZSTD mutex
    pthread_mutex_destroy(&g_init_qzstd_mtx);
  }
}
