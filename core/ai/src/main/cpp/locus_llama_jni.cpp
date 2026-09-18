#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>
#include <thread>
#include "llama.h"
#include "common.h"

#define TAG "LocusLlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ponytail: single resident model in static pointers, upgrade to instance registry if multi-model needed
static std::mutex g_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

static void internal_unload() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_locus_core_ai_llama_LlamaRuntime_nativeLoadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_path
) {
    if (j_path == nullptr) {
        return JNI_FALSE;
    }

    const char* path_chars = env->GetStringUTFChars(j_path, nullptr);
    if (path_chars == nullptr) {
        return JNI_FALSE;
    }
    std::string path(path_chars);
    env->ReleaseStringUTFChars(j_path, path_chars);

    std::lock_guard<std::mutex> lock(g_mutex);

    // Unload any previously loaded model (single-model-loaded-at-a-time contract)
    internal_unload();

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-first: M-1 requirement

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (g_model == nullptr) {
        LOGE("Failed to load model from: %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings = true;
    int hardware_threads = static_cast<int>(std::thread::hardware_concurrency());
    cparams.n_threads = std::max(1, hardware_threads > 0 ? hardware_threads : 4);
    cparams.n_threads_batch = cparams.n_threads;
    cparams.n_ctx = 2048;
    cparams.n_batch = 2048;
    cparams.n_ubatch = 2048;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        LOGE("Failed to create llama context for model: %s", path.c_str());
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Successfully loaded model from: %s (n_embd=%d)", path.c_str(), llama_model_n_embd(g_model));
    return JNI_TRUE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_locus_core_ai_llama_LlamaRuntime_nativeEmbed(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_text
) {
    if (j_text == nullptr) {
        return nullptr;
    }

    const char* text_chars = env->GetStringUTFChars(j_text, nullptr);
    if (text_chars == nullptr) {
        return nullptr;
    }
    std::string text(text_chars);
    env->ReleaseStringUTFChars(j_text, text_chars);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Cannot embed: model or context not loaded");
        return nullptr;
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    if (vocab == nullptr) {
        LOGE("Failed to get model vocab");
        return nullptr;
    }

    std::vector<llama_token> tokens = common_tokenize(vocab, text, true, false);
    if (tokens.empty()) {
        jfloatArray empty = env->NewFloatArray(0);
        return empty;
    }

    const int n_ctx = llama_n_ctx(g_ctx);
    if (static_cast<int>(tokens.size()) > n_ctx) {
        tokens.resize(n_ctx);
    }

    llama_batch batch = llama_batch_init(static_cast<int32_t>(tokens.size()), 0, 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], static_cast<llama_pos>(i), { 0 }, true);
    }

    llama_memory_clear(llama_get_memory(g_ctx), true);

    if (llama_decode(g_ctx, batch) < 0) {
        LOGE("llama_decode failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const int n_embd = llama_model_n_embd(g_model);
    const enum llama_pooling_type pooling_type = llama_pooling_type(g_ctx);

    const float* raw_embd = nullptr;
    if (pooling_type == LLAMA_POOLING_TYPE_NONE) {
        raw_embd = llama_get_embeddings_ith(g_ctx, -1);
    } else {
        raw_embd = llama_get_embeddings_seq(g_ctx, 0);
    }

    if (raw_embd == nullptr) {
        raw_embd = llama_get_embeddings(g_ctx);
    }

    if (raw_embd == nullptr || n_embd <= 0) {
        LOGE("Failed to extract embeddings");
        llama_batch_free(batch);
        return nullptr;
    }

    std::vector<float> normalized(n_embd);
    common_embd_normalize(raw_embd, normalized.data(), n_embd, 2);

    llama_batch_free(batch);

    jfloatArray result = env->NewFloatArray(n_embd);
    if (result == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, n_embd, normalized.data());
    return result;
}

JNIEXPORT void JNICALL
Java_com_locus_core_ai_llama_LlamaRuntime_nativeUnload(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    internal_unload();
    LOGI("Model unloaded");
}

}
