#include <jni.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <utility>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

namespace {

constexpr int kDefaultContextSize = 4096;
constexpr int kPromptTokensPerSlice = 512;
constexpr int kTokensPerSlice = 8;
constexpr std::size_t kMaximumThreads = 6;

std::once_flag g_backend_once;
std::mutex g_models_mutex;
std::unordered_map<std::string, llama_model *> g_models;

void EnsureBackend() {
  std::call_once(g_backend_once, [] {
    llama_backend_init();
    llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
  });
}

std::string ToString(JNIEnv *env, jstring value) {
  if (value == nullptr) {
    return {};
  }
  const char *chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return {};
  }
  std::string result{chars};
  env->ReleaseStringUTFChars(value, chars);
  return result;
}

void Throw(JNIEnv *env, const std::string &message) {
  jclass type = env->FindClass("java/lang/IllegalStateException");
  if (type != nullptr) {
    env->ThrowNew(type, message.c_str());
  }
}

using ProgressCallback = bool (*)(float, void *);

struct ProgressContext {
  JNIEnv *env;
  jobject callback;
  jmethodID on_load_progress;
  jmethodID is_cancelled;
};

bool ReportProgress(float progress, void *user_data) {
  auto *context = static_cast<ProgressContext *>(user_data);
  if (context == nullptr || context->callback == nullptr) {
    return true;
  }
  int percent = static_cast<int>(progress * 100.0F);
  percent = std::max(0, std::min(100, percent));
  context->env->CallVoidMethod(context->callback, context->on_load_progress, percent);
  if (context->env->ExceptionCheck()) {
    return false;
  }
  return !context->env->CallBooleanMethod(context->callback, context->is_cancelled);
}

std::size_t Utf8CompleteLength(const std::string &bytes) {
  if (bytes.empty()) {
    return 0;
  }
  std::size_t lead = bytes.size();
  const std::size_t maximum_back = std::min<std::size_t>(4, bytes.size());
  for (std::size_t back = 1; back <= maximum_back; ++back) {
    const auto candidate = bytes.size() - back;
    const auto value = static_cast<unsigned char>(bytes[candidate]);
    if ((value & 0xC0U) != 0x80U) {
      lead = candidate;
      break;
    }
  }
  if (lead == bytes.size()) {
    return 0;
  }
  const auto lead_value = static_cast<unsigned char>(bytes[lead]);
  std::size_t expected = 1;
  if ((lead_value & 0x80U) == 0x00U) {
    expected = 1;
  } else if ((lead_value & 0xE0U) == 0xC0U) {
    expected = 2;
  } else if ((lead_value & 0xF0U) == 0xE0U) {
    expected = 3;
  } else if ((lead_value & 0xF8U) == 0xF0U) {
    expected = 4;
  } else {
    return bytes.size();
  }
  return lead + expected <= bytes.size() ? bytes.size() : lead;
}

llama_model *AcquireModel(const std::string &path, int gpu_layers, ProgressCallback progress,
                          void *progress_user_data) {
  const std::string key = path + "|" + std::to_string(gpu_layers);
  {
    std::lock_guard<std::mutex> guard(g_models_mutex);
    auto found = g_models.find(key);
    if (found != g_models.end()) {
      return found->second;
    }
  }
  auto params = llama_model_default_params();
  params.n_gpu_layers = gpu_layers;
  if (progress != nullptr) {
    params.progress_callback = progress;
    params.progress_callback_user_data = progress_user_data;
  }
  llama_model *model = llama_model_load_from_file(path.c_str(), params);
  if (model == nullptr) {
    return nullptr;
  }
  std::lock_guard<std::mutex> guard(g_models_mutex);
  g_models.emplace(key, model);
  return model;
}

std::string ApplyChatTemplate(llama_model *model, const std::vector<std::string> &roles,
                              const std::vector<std::string> &contents) {
  const char *tmpl = llama_model_chat_template(model, nullptr);
  if (tmpl == nullptr) {
    std::string prompt;
    for (std::size_t i = 0; i < roles.size() && i < contents.size(); ++i) {
      prompt += roles[i] + "\n" + contents[i] + "\n";
    }
    prompt += "assistant\n";
    return prompt;
  }
  std::vector<llama_chat_message> messages;
  messages.reserve(roles.size());
  for (std::size_t i = 0; i < roles.size() && i < contents.size(); ++i) {
    messages.push_back(llama_chat_message{.role = roles[i].c_str(), .content = contents[i].c_str()});
  }
  std::size_t capacity = 256;
  for (std::size_t i = 0; i < roles.size() && i < contents.size(); ++i) {
    capacity += 2 * (roles[i].size() + contents[i].size());
  }
  for (int attempt = 0; attempt < 4; ++attempt) {
    std::vector<char> buffer(capacity);
    const int written = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true,
                                                  buffer.data(), static_cast<int>(buffer.size()));
    if (written < 0) {
      break;
    }
    if (static_cast<std::size_t>(written) < buffer.size()) {
      return std::string(buffer.data(), static_cast<std::size_t>(written));
    }
    capacity = static_cast<std::size_t>(written) + 1;
  }
  std::string prompt;
  for (std::size_t i = 0; i < roles.size() && i < contents.size(); ++i) {
    prompt += roles[i] + "\n" + contents[i] + "\n";
  }
  prompt += "assistant\n";
  return prompt;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_cn_lineai_engine_local_NativeLlamaEngine_nativeRuntimeInfo(JNIEnv *env, jclass) {
  EnsureBackend();
  std::string info = "llama.cpp ";
  info += LINCODE_LLAMA_REVISION;
  info += " | devices: ";
  const std::size_t count = ggml_backend_dev_count();
  for (std::size_t index = 0; index < count; ++index) {
    auto *device = ggml_backend_dev_get(index);
    if (device == nullptr) {
      continue;
    }
    if (index > 0) {
      info += ", ";
    }
    const char *name = ggml_backend_dev_name(device);
    info += name == nullptr ? "unknown" : name;
  }
  return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_cn_lineai_engine_local_NativeLlamaEngine_nativeGenerate(
    JNIEnv *env, jclass, jstring model_path, jobjectArray roles, jobjectArray contents,
    jint max_tokens, jint context_size, jint gpu_layers, jobject callback) {
  EnsureBackend();
  const std::string path = ToString(env, model_path);
  if (path.empty()) {
    Throw(env, "Local model path is empty");
    return nullptr;
  }
  jclass callback_class = env->GetObjectClass(callback);
  jmethodID on_piece = env->GetMethodID(callback_class, "onPiece", "(Ljava/lang/String;)V");
  jmethodID on_load_progress = env->GetMethodID(callback_class, "onLoadProgress", "(I)V");
  jmethodID is_cancelled = env->GetMethodID(callback_class, "isCancelled", "()Z");
  if (on_piece == nullptr || on_load_progress == nullptr || is_cancelled == nullptr) {
    Throw(env, "Local model callback is incomplete");
    return nullptr;
  }

  ProgressContext progress_context{env, callback, on_load_progress, is_cancelled};
  llama_model *model = AcquireModel(path, std::max(0, static_cast<int>(gpu_layers)),
                                    &ReportProgress, &progress_context);
  if (model == nullptr) {
    Throw(env, "llama.cpp could not load " + path);
    return nullptr;
  }
  env->CallVoidMethod(callback, on_load_progress, 100);

  const int role_count = env->GetArrayLength(roles);
  const int content_count = env->GetArrayLength(contents);
  std::vector<std::string> role_values;
  std::vector<std::string> content_values;
  const int message_count = std::min(role_count, content_count);
  for (int i = 0; i < message_count; ++i) {
    auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
    auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
    role_values.push_back(ToString(env, role));
    content_values.push_back(ToString(env, content));
    env->DeleteLocalRef(role);
    env->DeleteLocalRef(content);
  }
  const std::string prompt = ApplyChatTemplate(model, role_values, content_values);
  const auto *vocab = llama_model_get_vocab(model);

  int token_count = llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()),
                                   nullptr, 0, true, true);
  if (token_count < 0) {
    token_count = -token_count;
  }
  std::vector<llama_token> tokens(static_cast<std::size_t>(token_count));
  const int written = llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()),
                                     tokens.data(), token_count, true, true);
  if (written < 0) {
    Throw(env, "The prompt could not be tokenized");
    return nullptr;
  }
  tokens.resize(static_cast<std::size_t>(written));

  int context_tokens = context_size > 0 ? context_size : kDefaultContextSize;
  const int trained = llama_model_n_ctx_train(model);
  if (trained > 0 && context_tokens > trained) {
    context_tokens = trained;
  }
  if (static_cast<std::size_t>(context_tokens) <= tokens.size() + 1) {
    Throw(env, "The prompt needs " + std::to_string(tokens.size()) +
                   " tokens but the configured context is " + std::to_string(context_tokens));
    return nullptr;
  }

  auto context_params = llama_context_default_params();
  context_params.n_ctx = static_cast<std::uint32_t>(context_tokens);
  context_params.n_batch = static_cast<std::uint32_t>(kPromptTokensPerSlice);
  const unsigned hardware = std::thread::hardware_concurrency();
  const int threads = static_cast<int>(std::clamp(hardware == 0U ? 2U : hardware, 1U,
                                                  static_cast<unsigned>(kMaximumThreads)));
  context_params.n_threads = threads;
  context_params.n_threads_batch = threads;
  llama_context *context = llama_init_from_model(model, context_params);
  if (context == nullptr) {
    Throw(env, "llama.cpp could not create a context for the model");
    return nullptr;
  }

  llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
  llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
  llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95F, 1));
  llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05F, 1));
  llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8F));
  llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

  std::size_t fed = 0;
  while (fed < tokens.size()) {
    if (env->CallBooleanMethod(callback, is_cancelled)) {
      llama_sampler_free(sampler);
      llama_free(context);
      Throw(env, "Local generation cancelled");
      return nullptr;
    }
    const std::size_t chunk = std::min<std::size_t>(tokens.size() - fed, kPromptTokensPerSlice);
    auto batch = llama_batch_get_one(tokens.data() + fed, static_cast<int>(chunk));
    if (llama_decode(context, batch) != 0) {
      llama_sampler_free(sampler);
      llama_free(context);
      Throw(env, "llama.cpp failed to evaluate the prompt");
      return nullptr;
    }
    fed += chunk;
  }

  const int generation_limit = max_tokens > 0 ? max_tokens : context_tokens - static_cast<int>(tokens.size());
  int generated = 0;
  std::string pending;
  while (generated < generation_limit) {
    if (env->CallBooleanMethod(callback, is_cancelled)) {
      break;
    }
    llama_token token = llama_sampler_sample(sampler, context, -1);
    if (llama_vocab_is_eog(vocab, token)) {
      break;
    }
    char buffer[256];
    const int piece = llama_token_to_piece(vocab, token, buffer, static_cast<int>(sizeof(buffer)), 0, false);
    if (piece > 0) {
      pending.append(buffer, static_cast<std::size_t>(piece));
      const std::size_t complete = Utf8CompleteLength(pending);
      if (complete > 0) {
        jstring text = env->NewStringUTF(pending.substr(0, complete).c_str());
        env->CallVoidMethod(callback, on_piece, text);
        env->DeleteLocalRef(text);
        pending.erase(0, complete);
      }
    }
    ++generated;
    auto batch = llama_batch_get_one(&token, 1);
    if (llama_decode(context, batch) != 0) {
      break;
    }
  }
  if (!pending.empty()) {
    jstring text = env->NewStringUTF(pending.c_str());
    env->CallVoidMethod(callback, on_piece, text);
    env->DeleteLocalRef(text);
  }

  llama_sampler_free(sampler);
  llama_free(context);

  jintArray usage = env->NewIntArray(2);
  if (usage != nullptr) {
    jint values[2] = {static_cast<jint>(tokens.size()), static_cast<jint>(generated)};
    env->SetIntArrayRegion(usage, 0, 2, values);
  }
  return usage;
}
