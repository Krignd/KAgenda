#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
using Clock = std::chrono::steady_clock;
struct Request {
    JNIEnv *env;
    jobject signal;
    jmethodID cancelled;
    Clock::time_point deadline = Clock::now() + std::chrono::seconds(90);
    bool stop() const {
        return Clock::now() >= deadline || env->CallBooleanMethod(signal, cancelled);
    }
};
bool abort_decode(void *p) { return static_cast<Request *>(p)->stop(); }
bool load_progress(float, void *p) { return !static_cast<Request *>(p)->stop(); }

// JNI modified UTF-8 is not UTF-8 for supplementary characters. Transfer byte arrays.
std::string bytes(JNIEnv *env, jbyteArray value) {
    std::string s(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, static_cast<jsize>(s.size()), reinterpret_cast<jbyte *>(s.data()));
    return s;
}

// JSON grammar constrains syntax; the Kotlin layer separately validates operations.
const char *grammar = R"GBNF(
root ::= "{" ws "\"ops\"" ws ":" ws "[" ws (object (ws "," ws object){0,23})? ws "]" ws "}" ws
object ::= "{" ws (field (ws "," ws field)*)? ws "}"
field ::= "\"op\"" ws ":" ws ("\"add\"" | "\"update\"" | "\"delete\"") | "\"tg\"" ws ":" ws ("\"agenda\"" | "\"plan\"") | ("\"d\"" | "\"ed\"") ws ":" ws date | ("\"s\"" | "\"e\"") ws ":" ws time | "\"rp\"" ws ":" ws repeat | ("\"notify\"" | "\"alarm\"" | "\"lg\"") ws ":" ws ("true" | "false" | "null") | "\"lead\"" ws ":" ws ([0-9]{1,5} | "null") | ("\"match\"" | "\"set\"") ws ":" ws object | ("\"t\"" | "\"tp\"" | "\"l\"" | "\"n\"" | "\"id\"" | "\"tag\"") ws ":" ws (string | "null")
date ::= "null" | "\"\"" | "\"" [0-9]{4} "-" ("0" [1-9] | "1" [0-2]) "-" ("0" [1-9] | [12] [0-9] | "3" [01]) "\""
time ::= "null" | "\"\"" | "\"" (([01] [0-9] | "2" [0-3]) ":" [0-5] [0-9] | "凌晨" | "早晨" | "上午" | "下午" | "晚上" | "午夜") "\""
repeat ::= "null" | "\"\"" | "\"" (("weekly:" | "biweekly:") [1-7] ("," [1-7])* | "daily:" [1-9] [0-9]{0,3} | "monthly" | "none") "\""
string ::= "\"" char* "\""
char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
ws ::= [ \t\n\r]*
)GBNF";
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kstudio_agenda_data_LocalAiNative_generate(
    JNIEnv *env, jobject, jbyteArray model_path, jbyteArray prompt_bytes,
    jboolean ops_grammar, jobject signal, jint timeout_ms) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] {
            // Never log prompts, user data, model paths or generated text.
            llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
            llama_backend_init();
        });
        Request request{env, signal, env->GetMethodID(env->GetObjectClass(signal), "isCancelled", "()Z")};
        request.deadline = Clock::now() + std::chrono::milliseconds(std::clamp(static_cast<int>(timeout_ms), 1000, 90000));
        const auto path = bytes(env, model_path);
        const auto prompt = bytes(env, prompt_bytes);
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.progress_callback = load_progress;
        mp.progress_callback_user_data = &request;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
            llama_model_load_from_file(path.c_str(), mp), llama_model_free);
        if (!model) throw std::runtime_error("model_load");
        const auto *vocab = llama_model_get_vocab(model.get());
        int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        if (count <= 0 || count > 3000) throw std::runtime_error("input_too_long");
        std::vector<llama_token> tokens(count);
        count = llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true);
        if (count <= 0) throw std::runtime_error("tokenize");
        auto cp = llama_context_default_params();
        // Reserve the same 1024 output tokens without allocating an oversized KV cache.
        cp.n_ctx = std::max(1536, ((count + 1024 + 255) / 256) * 256);
        cp.n_batch = 256;
        cp.n_ubatch = 128;
        cp.n_threads = 4;
        cp.n_threads_batch = 4;
        cp.abort_callback = abort_decode;
        cp.abort_callback_data = &request;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(
            llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) throw std::runtime_error("context_load");
        for (int pos = 0; pos < count; pos += 256) {
            if (request.stop()) throw std::runtime_error("cancelled_or_timeout");
            auto batch = llama_batch_get_one(tokens.data() + pos, std::min(256, count - pos));
            if (llama_decode(ctx.get(), batch) != 0)
                throw std::runtime_error(request.stop() ? "cancelled_or_timeout" : "prefill_decode");
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        if (ops_grammar) {
            auto *g = llama_sampler_init_grammar(vocab, grammar, "root");
            if (!g) throw std::runtime_error("grammar");
            llama_sampler_chain_add(sampler.get(), g);
        }
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
        std::string output;
        bool complete = false;
        bool quoted = false, escaped = false, started = false;
        int depth = 0;
        for (int i = 0; i < 1024; ++i) {
            if (request.stop()) throw std::runtime_error("cancelled_or_timeout");
            const auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) { complete = true; break; }
            char piece[512];
            int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (size < 0) throw std::runtime_error("token_piece");
            output.append(piece, size);
            // Finish at the complete JSON object; do not wait for extra whitespace or EOS.
            for (int j = 0; j < size; ++j) {
                const char c = piece[j];
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') quoted = false;
                } else if (c == '"') quoted = true;
                else if (c == '{') { started = true; ++depth; }
                else if (c == '}' && started && --depth == 0) complete = true;
            }
            if (complete) break;
            auto next = token;
            if (llama_decode(ctx.get(), llama_batch_get_one(&next, 1)) != 0)
                throw std::runtime_error(request.stop() ? "cancelled_or_timeout" : "generation_decode");
        }
        if (!complete) throw std::runtime_error("output_too_long");
        auto result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
        return nullptr;
    } catch (...) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "native_failure");
        return nullptr;
    }
}
