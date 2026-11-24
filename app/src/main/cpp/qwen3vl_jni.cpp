#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "chat.h"
#include "common.h"
#include "ggml-backend.h"
#include "llama.h"
#include "mtmd-helper.h"
#include "mtmd.h"

namespace {
constexpr const char * TAG = "SmartAdSkipQwen";
struct Runtime {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    mtmd_context * vision = nullptr;
    common_chat_templates_ptr templates;

    ~Runtime() {
        templates.reset();
        if (vision) mtmd_free(vision);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
std::mutex runtimeMutex;
std::unique_ptr<Runtime> runtime;
std::once_flag backendInit;

void logCallback(ggml_log_level level, const char * message, void *) {
    if (!message || (level != GGML_LOG_LEVEL_ERROR && level != GGML_LOG_LEVEL_WARN)) return;
    __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, TAG, message);
}
void throwJava(JNIEnv * env, const std::string & message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message.c_str());
}
std::string fromJava(JNIEnv * env, jstring value) {
    if (!value) throw std::runtime_error("Missing native string argument");
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("Cannot read native string argument");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
std::string tokenToString(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(128);
    int count = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (count < 0) throw std::runtime_error("Could not decode generated token");
    return {buffer.data(), static_cast<size_t>(count)};
}
long elapsedMs(std::chrono::steady_clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count();
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kenznguyen_smartadskip_ai_NativeQwen3Vl_load(
        JNIEnv * env, jclass, jstring modelPath, jstring projectorPath, jint threads) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    try {
        std::call_once(backendInit, [] {
            llama_log_set(logCallback, nullptr);
            ggml_backend_load_all();
            llama_backend_init();
        });
        auto next = std::make_unique<Runtime>();
        const std::string modelFile = fromJava(env, modelPath);
        const std::string projectorFile = fromJava(env, projectorPath);
        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        next->model = llama_model_load_from_file(modelFile.c_str(), modelParams);
        if (!next->model) throw std::runtime_error("Qwen3-VL language GGUF failed to load");

        llama_context_params contextParams = llama_context_default_params();
        contextParams.n_ctx = 4096;
        contextParams.n_batch = 512;
        contextParams.n_ubatch = 512;
        contextParams.n_threads = std::max(2, static_cast<int>(threads));
        contextParams.n_threads_batch = std::max(2, static_cast<int>(threads));
        next->context = llama_init_from_model(next->model, contextParams);
        if (!next->context) throw std::runtime_error("Could not create llama context");

        mtmd_context_params visionParams = mtmd_context_params_default();
        visionParams.use_gpu = false;
        visionParams.print_timings = false;
        visionParams.n_threads = std::max(2, static_cast<int>(threads));
        visionParams.warmup = false;
        next->vision = mtmd_init_from_file(projectorFile.c_str(), next->model, visionParams);
        if (!next->vision || !mtmd_support_vision(next->vision)) {
            throw std::runtime_error("Qwen3-VL mmproj is invalid or unsupported");
        }
        next->templates = common_chat_templates_init(next->model, "");
        if (!next->templates) throw std::runtime_error("Model chat template is unavailable");
        runtime = std::move(next);
        return JNI_TRUE;
    } catch (const std::exception & error) {
        runtime.reset();
        throwJava(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kenznguyen_smartadskip_ai_NativeQwen3Vl_infer(
        JNIEnv * env, jclass, jintArray argbPixels, jint width, jint height, jstring promptValue) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    try {
        if (!runtime) throw std::runtime_error("Qwen3-VL runtime is not loaded");
        if (!argbPixels || width <= 0 || height <= 0 ||
            static_cast<int64_t>(width) * height > 2'000'000) {
            throw std::runtime_error("Invalid screenshot dimensions");
        }
        const jsize count = env->GetArrayLength(argbPixels);
        if (count != static_cast<jsize>(width * height)) throw std::runtime_error("Invalid screenshot pixel count");
        jint * pixels = env->GetIntArrayElements(argbPixels, nullptr);
        if (!pixels) throw std::runtime_error("Cannot access screenshot pixels");
        std::vector<uint8_t> rgb(static_cast<size_t>(count) * 3);
        for (jsize i = 0; i < count; ++i) {
            const uint32_t pixel = static_cast<uint32_t>(pixels[i]);
            rgb[static_cast<size_t>(i) * 3] = (pixel >> 16) & 0xff;
            rgb[static_cast<size_t>(i) * 3 + 1] = (pixel >> 8) & 0xff;
            rgb[static_cast<size_t>(i) * 3 + 2] = pixel & 0xff;
        }
        env->ReleaseIntArrayElements(argbPixels, pixels, JNI_ABORT);

        llama_memory_clear(llama_get_memory(runtime->context), true);
        mtmd::bitmap image(width, height, rgb.data());
        if (!image.ptr) throw std::runtime_error("Could not prepare screenshot for MTMD");
        const std::string marker = mtmd_default_marker();
        common_chat_msg message;
        message.role = "user";
        message.content = marker + "\n" + fromJava(env, promptValue);
        const std::vector<common_chat_msg> history;
        const std::string formatted = common_chat_format_single(runtime->templates.get(), history, message, true, true);
        const size_t markerAt = formatted.find(marker);
        if (markerAt == std::string::npos) throw std::runtime_error("Chat template omitted image marker");
        const std::string before = formatted.substr(0, markerAt);
        const std::string after = formatted.substr(markerAt + marker.size());
        mtmd_input_text beforeText{before.data(), before.size(), false, true};
        mtmd_input_text afterText{after.data(), after.size(), false, true};
        mtmd_input_part beforePart{&beforeText, nullptr};
        mtmd_input_part imagePart{nullptr, image.ptr.get()};
        mtmd_input_part afterPart{&afterText, nullptr};
        const mtmd_input_part * parts[]{&beforePart, &imagePart, &afterPart};
        mtmd::input_chunks_ptr chunks(mtmd_input_chunks_init());
        if (mtmd_tokenize_from_parts(runtime->vision, chunks.get(), parts, 3, true) != 0) {
            throw std::runtime_error("MTMD image tokenization failed");
        }
        llama_pos position = 0;
        if (mtmd_helper_eval_chunks(runtime->vision, runtime->context, chunks.get(),
                                    0, 0, 512, true, &position) != 0) {
            throw std::runtime_error("MTMD image/prompt evaluation failed");
        }

        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        llama_batch batch = llama_batch_init(1, 0, 1);
        const llama_vocab * vocab = llama_model_get_vocab(runtime->model);
        std::string output;
        for (int generated = 0; generated < 160; ++generated) {
            const llama_token token = llama_sampler_sample(sampler, runtime->context, -1);
            llama_sampler_accept(sampler, token);
            if (llama_vocab_is_eog(vocab, token)) break;
            output += tokenToString(vocab, token);
            common_batch_clear(batch);
            common_batch_add(batch, token, position++, {0}, true);
            if (llama_decode(runtime->context, batch) != 0) {
                llama_batch_free(batch);
                llama_sampler_free(sampler);
                throw std::runtime_error("Qwen3-VL token decode failed");
            }
        }
        llama_batch_free(batch);
        llama_sampler_free(sampler);
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception & error) {
        throwJava(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_kenznguyen_smartadskip_ai_NativeQwen3Vl_close(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    runtime.reset();
}
